package com.yesman.akythera.client.core.physics.cloth;

import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.physics.ClothPhysicsData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.PriorityQueue;

/// The particle graph of one cloth over one [SkeletalMesh], indexed by particle. Offset particles are
/// not part of it: they ride a particle and are listed on their own.
///
/// @param particlePositions  mesh position index of each particle
/// @param inverseMasses      1 for a simulated particle, 0 for an anchor
/// @param weights            authored weight of each particle, 0 for an anchor
/// @param restPositions      bind-pose position of each particle, xyz flattened
/// @param tetherAnchors      nearest anchor particle of each particle, -1 for none or for an anchor
/// @param tetherLengths      geodesic rest distance to [#tetherAnchors]
/// @param adjacencyStarts    first triangle of each particle in [#adjacentTriangles], in triangles
/// @param adjacencyCounts    triangles touching each simulated particle, 0 for an anchor
/// @param adjacentTriangles  particle-index triples, grouped per particle
/// @param constraintEnds     particle pairs, flattened, ordered by color
/// @param restLengths        rest length of each constraint
/// @param constraintKinds    [#KIND_STRETCH] or [#KIND_BEND] per constraint
/// @param colorOffsets       first constraint of each color
/// @param colorCounts        constraints in each color
/// @param renderMap          state slot of each render vertex: a particle, or [#particleCount] plus an
///                           offset particle; -1 where it renders skinned
/// @param offsetPositions    mesh position index of each offset particle
/// @param offsetRoots        particle each offset particle rides
public record ClothTopology(
    int[] particlePositions,
    float[] inverseMasses,
    float[] weights,
    float[] restPositions,
    int[] tetherAnchors,
    float[] tetherLengths,
    int[] adjacencyStarts,
    int[] adjacencyCounts,
    int[] adjacentTriangles,
    int[] constraintEnds,
    float[] restLengths,
    int[] constraintKinds,
    int[] colorOffsets,
    int[] colorCounts,
    int[] renderMap,
    int[] offsetPositions,
    int[] offsetRoots
) {
    public static final int KIND_STRETCH = 0;
    public static final int KIND_BEND = 1;

    /// Colors a long mask can track. Cloth meshes color in well under a dozen.
    private static final int MAX_COLORS = 64;

    public int particleCount() {
        return particlePositions.length;
    }

    public int constraintCount() {
        return restLengths.length;
    }

    public int offsetCount() {
        return offsetPositions.length;
    }

    /// `weights` is indexed by mesh position; a position past its end is kinematic. `offsetPairs` is
    /// [ClothPhysicsData#offsetPairs].
    public static ClothTopology build(final SkeletalMesh mesh, final float[] weights, final int[] offsetPairs) {
        int positionCount = mesh.getPositionCount();
        int[] rootOf = new int[positionCount];
        boolean[] offset = new boolean[positionCount];

        Arrays.fill(rootOf, -1);

        for (int k = 0; k + 1 < offsetPairs.length; k += 2) {
            int root = offsetPairs[k];
            int follower = offsetPairs[k + 1];

            if (root < 0 || follower < 0 || root >= positionCount || follower >= positionCount || root == follower) continue;

            rootOf[follower] = root;
            offset[follower] = true;
        }

        boolean[] simulated = new boolean[positionCount];

        for (int i = 0; i < positionCount; i++) {
            simulated[i] = !offset[i] && i < weights.length && weights[i] > 0.0F;
        }

        // An offset rides only a simulated root, which is never an offset itself
        for (int i = 0; i < positionCount; i++) {
            if (rootOf[i] >= 0 && !simulated[rootOf[i]]) rootOf[i] = -1;
        }

        IntArrayList renderPositions = new IntArrayList();
        IntArrayList triangles = new IntArrayList();

        for (SkeletalMesh.SkeletalMeshPart part : mesh.getParts().values()) {
            int vertexCount = part.vertexCount();

            for (int v = 0; v < vertexCount; v++) {
                renderPositions.add(part.getPositionIndex(v));
            }

            for (int v = 0; v + 2 < vertexCount; v += 3) {
                int a = part.getPositionIndex(v);
                int b = part.getPositionIndex(v + 1);
                int c = part.getPositionIndex(v + 2);

                if (a == b || b == c || c == a) continue;

                // The offset layer and the faces joining it to the root layer stay out of the cloth
                if (offset[a] || offset[b] || offset[c]) continue;

                triangles.add(a);
                triangles.add(b);
                triangles.add(c);
            }
        }

        int triangleCount = triangles.size() / 3;
        boolean[] included = new boolean[positionCount];
        Long2ObjectOpenHashMap<IntArrayList> edgeOpposites = new Long2ObjectOpenHashMap<>();

        for (int t = 0; t < triangleCount; t++) {
            int a = triangles.getInt(t * 3);
            int b = triangles.getInt(t * 3 + 1);
            int c = triangles.getInt(t * 3 + 2);

            putEdge(edgeOpposites, a, b, c);
            putEdge(edgeOpposites, b, c, a);
            putEdge(edgeOpposites, c, a, b);

            if (simulated[a] || simulated[b] || simulated[c]) {
                included[a] = true;
                included[b] = true;
                included[c] = true;
            }
        }

        for (IntArrayList opposites : edgeOpposites.values()) {
            if (opposites.size() != 2) continue;

            int c = opposites.getInt(0);
            int d = opposites.getInt(1);

            if (c != d && (simulated[c] || simulated[d])) {
                included[c] = true;
                included[d] = true;
            }
        }

        int[] positionToParticle = new int[positionCount];
        IntArrayList particlePositions = new IntArrayList();
        Arrays.fill(positionToParticle, -1);

        for (int i = 0; i < positionCount; i++) {
            if (!included[i]) continue;

            positionToParticle[i] = particlePositions.size();
            particlePositions.add(i);
        }

        int particleCount = particlePositions.size();
        float[] inverseMasses = new float[particleCount];
        float[] particleWeights = new float[particleCount];
        float[] restPositions = new float[particleCount * 3];
        Vector3f scratch = new Vector3f();

        for (int p = 0; p < particleCount; p++) {
            int position = particlePositions.getInt(p);

            inverseMasses[p] = simulated[position] ? 1.0F : 0.0F;
            particleWeights[p] = simulated[position] ? weights[position] : 0.0F;
            mesh.getPosition(position, scratch);
            restPositions[p * 3] = scratch.x;
            restPositions[p * 3 + 1] = scratch.y;
            restPositions[p * 3 + 2] = scratch.z;
        }

        // Constraints, uncolored
        IntArrayList ends = new IntArrayList();
        IntArrayList kinds = new IntArrayList();
        IntArrayList[] particleNeighbors = new IntArrayList[particleCount];

        for (int p = 0; p < particleCount; p++) {
            particleNeighbors[p] = new IntArrayList();
        }

        for (Long2ObjectMap.Entry<IntArrayList> entry : edgeOpposites.long2ObjectEntrySet()) {
            int a = (int) (entry.getLongKey() >>> 32);
            int b = (int) entry.getLongKey();

            if (included[a] && included[b]) {
                particleNeighbors[positionToParticle[a]].add(positionToParticle[b]);
                particleNeighbors[positionToParticle[b]].add(positionToParticle[a]);
            }

            if (simulated[a] || simulated[b]) {
                ends.add(positionToParticle[a]);
                ends.add(positionToParticle[b]);
                kinds.add(KIND_STRETCH);
            }

            IntArrayList opposites = entry.getValue();

            if (opposites.size() == 2) {
                int c = opposites.getInt(0);
                int d = opposites.getInt(1);

                if (c != d && (simulated[c] || simulated[d])) {
                    ends.add(positionToParticle[c]);
                    ends.add(positionToParticle[d]);
                    kinds.add(KIND_BEND);
                }
            }
        }

        // Greedy coloring over simulated endpoints; anchors are never written by the solver
        int constraintCount = kinds.size();
        int[] colors = new int[constraintCount];
        long[] usedColors = new long[particleCount];
        int colorCount = 0;

        for (int k = 0; k < constraintCount; k++) {
            int a = ends.getInt(k * 2);
            int b = ends.getInt(k * 2 + 1);
            long taken = (inverseMasses[a] > 0.0F ? usedColors[a] : 0L) | (inverseMasses[b] > 0.0F ? usedColors[b] : 0L);
            int color = Long.numberOfTrailingZeros(~taken);

            if (color >= MAX_COLORS) {
                throw new IllegalStateException("Cloth constraint graph needs more than " + MAX_COLORS + " colors");
            }

            usedColors[a] |= 1L << color;
            usedColors[b] |= 1L << color;
            colors[k] = color;
            colorCount = Math.max(colorCount, color + 1);
        }

        int[] colorCounts = new int[colorCount];
        int[] colorOffsets = new int[colorCount];

        for (int color : colors) {
            colorCounts[color]++;
        }

        for (int c = 1; c < colorCount; c++) {
            colorOffsets[c] = colorOffsets[c - 1] + colorCounts[c - 1];
        }

        int[] cursor = Arrays.copyOf(colorOffsets, colorCount);
        int[] constraintEnds = new int[constraintCount * 2];
        float[] restLengths = new float[constraintCount];
        int[] constraintKinds = new int[constraintCount];

        for (int k = 0; k < constraintCount; k++) {
            int slot = cursor[colors[k]]++;
            int a = ends.getInt(k * 2);
            int b = ends.getInt(k * 2 + 1);

            constraintEnds[slot * 2] = a;
            constraintEnds[slot * 2 + 1] = b;
            constraintKinds[slot] = kinds.getInt(k);
            restLengths[slot] = restDistance(restPositions, a, b);
        }

        // Tethers: nearest anchor by geodesic distance
        int[] tetherAnchors = new int[particleCount];
        float[] tetherLengths = new float[particleCount];
        PriorityQueue<Visit> queue = new PriorityQueue<>();

        Arrays.fill(tetherAnchors, -1);
        Arrays.fill(tetherLengths, Float.MAX_VALUE);

        for (int p = 0; p < particleCount; p++) {
            if (inverseMasses[p] == 0.0F) {
                tetherAnchors[p] = p;
                tetherLengths[p] = 0.0F;
                queue.add(new Visit(0.0F, p));
            }
        }

        while (!queue.isEmpty()) {
            Visit visit = queue.poll();

            if (visit.distance() > tetherLengths[visit.particle()]) continue;

            IntArrayList neighbors = particleNeighbors[visit.particle()];

            for (int n = 0; n < neighbors.size(); n++) {
                int next = neighbors.getInt(n);
                float distance = visit.distance() + restDistance(restPositions, visit.particle(), next);

                if (distance < tetherLengths[next]) {
                    tetherLengths[next] = distance;
                    tetherAnchors[next] = tetherAnchors[visit.particle()];
                    queue.add(new Visit(distance, next));
                }
            }
        }

        for (int p = 0; p < particleCount; p++) {
            if (inverseMasses[p] == 0.0F || tetherAnchors[p] < 0) {
                tetherAnchors[p] = -1;
                tetherLengths[p] = 0.0F;
            }
        }

        // Normal adjacency of simulated particles
        IntArrayList[] touching = new IntArrayList[particleCount];

        for (int t = 0; t < triangleCount; t++) {
            for (int corner = 0; corner < 3; corner++) {
                int position = triangles.getInt(t * 3 + corner);

                if (!simulated[position]) continue;

                int particle = positionToParticle[position];

                if (touching[particle] == null) touching[particle] = new IntArrayList();

                touching[particle].add(t);
            }
        }

        int[] adjacencyStarts = new int[particleCount];
        int[] adjacencyCounts = new int[particleCount];
        IntArrayList adjacentTriangles = new IntArrayList();

        for (int p = 0; p < particleCount; p++) {
            adjacencyStarts[p] = adjacentTriangles.size() / 3;

            if (touching[p] == null) continue;

            adjacencyCounts[p] = touching[p].size();

            for (int n = 0; n < touching[p].size(); n++) {
                int t = touching[p].getInt(n);

                adjacentTriangles.add(positionToParticle[triangles.getInt(t * 3)]);
                adjacentTriangles.add(positionToParticle[triangles.getInt(t * 3 + 1)]);
                adjacentTriangles.add(positionToParticle[triangles.getInt(t * 3 + 2)]);
            }
        }

        // Offset particles, each riding a root that became a particle
        int[] positionToOffset = new int[positionCount];
        IntArrayList offsetPositions = new IntArrayList();
        IntArrayList offsetRoots = new IntArrayList();

        Arrays.fill(positionToOffset, -1);

        for (int i = 0; i < positionCount; i++) {
            if (rootOf[i] < 0 || positionToParticle[rootOf[i]] < 0) continue;

            positionToOffset[i] = offsetPositions.size();
            offsetPositions.add(i);
            offsetRoots.add(positionToParticle[rootOf[i]]);
        }

        int[] renderMap = new int[renderPositions.size()];

        for (int v = 0; v < renderMap.length; v++) {
            int position = renderPositions.getInt(v);

            if (simulated[position]) {
                renderMap[v] = positionToParticle[position];
            } else if (positionToOffset[position] >= 0) {
                renderMap[v] = particleCount + positionToOffset[position];
            } else {
                renderMap[v] = -1;
            }
        }

        return new ClothTopology(
            particlePositions.toIntArray(),
            inverseMasses,
            particleWeights,
            restPositions,
            tetherAnchors,
            tetherLengths,
            adjacencyStarts,
            adjacencyCounts,
            adjacentTriangles.toIntArray(),
            constraintEnds,
            restLengths,
            constraintKinds,
            colorOffsets,
            colorCounts,
            renderMap,
            offsetPositions.toIntArray(),
            offsetRoots.toIntArray()
        );
    }

    /// Records `opposite` as a vertex facing the undirected edge `a`–`b`.
    private static void putEdge(final Long2ObjectOpenHashMap<IntArrayList> edgeOpposites, final int a, final int b, final int opposite) {
        long key = ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL);
        edgeOpposites.computeIfAbsent(key, _ -> new IntArrayList()).add(opposite);
    }

    private static float restDistance(final float[] restPositions, final int a, final int b) {
        float dx = restPositions[a * 3] - restPositions[b * 3];
        float dy = restPositions[a * 3 + 1] - restPositions[b * 3 + 1];
        float dz = restPositions[a * 3 + 2] - restPositions[b * 3 + 2];

        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record Visit(float distance, int particle) implements Comparable<Visit> {
        @Override
        public int compareTo(final Visit other) {
            return Float.compare(distance, other.distance);
        }
    }
}
