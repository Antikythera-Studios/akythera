package com.yesman.akythera.client.core.asset.mesh;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Rebuilds a vanilla [Model] as a [SkeletalMesh] in armature space, skinned by the weights of the
/// [SkeletalMesh] it is worn over.
///
/// A [ModelPart] carries one transform, so a cube that spans a joint cannot fold. A cube that wraps the
/// body part named like its own top-level part is rebuilt from that part: each of its triangles is
/// pushed out onto the cube with its weights unchanged, so the rows a body stacks on a joint are all
/// kept and the cube folds exactly like the body under it.
///
/// Any other cube has each face cut into cells on the coordinates the body's own vertices sit on. Every cell
/// has its own four vertices, and each copies the weights of the body point nearest to it, read a little
/// inside the cell. A top-level part named after a bone reads only the body triangles that bone and the
/// bones under it drive.
///
/// Where two neighboring cells read different weights the body has a joint: the vertices it stacks there
/// hold one side each. The cells are joined by a middle row of vertices on the same place, weighted like
/// the row the body keeps between its two sides (the one an elbow or a knee bone pushes out), with
/// triangles that have no area until the joint bends.
///
/// A bake is kept until the meshes reload ([#clear]), so a model is rebuilt once and skinned like any
/// other mesh from then on.
public final class VanillaModelBaker {
    /// Longest edge a face that follows no axis keeps, in blocks.
    private static final float MAX_EDGE = 0.125F;

    /// Distance under which two coordinates are one cut, in blocks.
    private static final float CUT_TOLERANCE = 1.0E-5F;

    /// Farthest a cube's side sits from the side of the body part it wraps, in blocks.
    private static final float MAX_INFLATION = 0.125F;

    /// Distance under which a body vertex sits on a side of its part's bounds, in blocks.
    private static final float FACE_TOLERANCE = 1.0F / 64.0F;

    /// Share of an edge its longest component holds for the edge to follow that axis.
    private static final float AXIS_ALIGNED = 0.999F;

    /// Bones a baked vertex keeps.
    private static final int MAX_INFLUENCES = 3;

    /// Share of the way from a cell's corner to its middle at which the corner's weights are read.
    private static final float SAMPLE_INSET = 0.05F;

    /// Summed difference of the weights two cells hold on the edge they meet on, above which that edge is a joint.
    private static final float JOINT_TOLERANCE = 0.01F;

    /// Farthest the middle row of a body joint sits from a vertex it weights, in blocks.
    private static final float MIDDLE_REACH = 0.25F;

    /// Squared length of the normal under which a triangle has no area.
    private static final float NO_AREA = 1.0E-12F;

    private static final float SEAM_AREA = 1.0E-6F;

    private static final float SEAM_NUDGE = 1.0F / 32.0F;

    private static final int PAINT_SAMPLES = 8;

    private static final float MINORITY_PAINT = 0.15F;

    private static final Map<BakeKey, Optional<Bake>> BAKED = new HashMap<>();

    private VanillaModelBaker() {
    }

    /// `model` in its initial pose, rebuilt over `body`. Null when `model` holds no cube, or when the
    /// bake failed. `item` and `slot` say what the model is worn as, and are kept with a new bake.
    public static @Nullable SkeletalMesh bake(
        final Model<?> model,
        final Armature armature,
        final SkeletalMesh body,
        final Item item,
        final EquipmentSlot slot,
        final List<Identifier> textures
    ) {
        return BAKED.computeIfAbsent(new BakeKey(model, armature, body, textures), _ -> {
            try {
                model.resetPose();

                SkeletalMesh.SkeletalMeshBuilder builder = build(model.root(), armature, body, textures);

                return builder == null
                    ? Optional.<Bake>empty()
                    : Optional.of(new Bake(model, armature, item, slot, textures, builder, new SkeletalMesh(builder)));
            } catch (RuntimeException e) {
                Akythera.LOGGER.error("Failed at baking a vanilla model {}", model.getClass().getName(), e);

                return Optional.<Bake>empty();
            }
        }).map(Bake::mesh).orElse(null);
    }

    /// Every bake kept right now.
    public static List<Bake> bakes() {
        return BAKED.values().stream().flatMap(Optional::stream).toList();
    }

    /// The cubes under `root` as they are posed, rebuilt over `body`, kept nowhere. Without a `body` every
    /// cube keeps its own faces and no vertex is weighted. Null when `root` holds no cube.
    public static SkeletalMesh.@Nullable SkeletalMeshBuilder build(
        final ModelPart root, final Armature armature, final @Nullable SkeletalMesh body
    ) {
        return build(root, armature, body, List.of());
    }

    public static SkeletalMesh.@Nullable SkeletalMeshBuilder build(
        final ModelPart root, final Armature armature, final @Nullable SkeletalMesh body, final List<Identifier> textures
    ) {
        Skin skin = new Skin(body);
        Map<String, Region> regions = new HashMap<>();
        SkeletalMesh.SkeletalMeshBuilder builder = SkeletalMesh.builder();
        FloatArrayList positions = new FloatArrayList();
        FloatArrayList normals = new FloatArrayList();
        FloatArrayList uvs = new FloatArrayList();
        FloatArrayList weights = new FloatArrayList();
        IntArrayList deformationFromTo = new IntArrayList();
        IntArrayList boneIds = new IntArrayList();
        IntArrayList weightIds = new IntArrayList();
        float[] influence = new float[Math.max(armature.getBoneCount(), skin.boneLimit)];
        float[] compared = new float[influence.length];
        Output output = new Output(positions, normals, uvs, weights, deformationFromTo, boneIds, weightIds);

        root.visit(new PoseStack(), (pose, partPath, _, cube) -> {
            int nameEnd = partPath.indexOf('/', 1);
            String partName = partPath.isEmpty() ? "root" : partPath.substring(1, nameEnd < 0 ? partPath.length() : nameEnd);
            Region region = regions.computeIfAbsent(partName, name -> skin.region(armature.getBoneByName(name), root));
            List<VertexBuilder> part = builder.partBuilders.computeIfAbsent(partName, _ -> new ArrayList<>());

            Shell shell = skin.shells.get(partName);
            Vector3f[][] faces = new Vector3f[cube.polygons.length][];
            Vector3f[] faceNormals = new Vector3f[cube.polygons.length];
            Vector3f cubeMin = new Vector3f(Float.MAX_VALUE);
            Vector3f cubeMax = new Vector3f(-Float.MAX_VALUE);
            boolean wraps = shell != null;

            for (int face = 0; face < cube.polygons.length; face++) {
                ModelPart.Vertex[] vertices = cube.polygons[face].vertices();
                Vector3f[] corners = new Vector3f[vertices.length];

                for (int i = 0; i < vertices.length; i++) {
                    Vector3f corner = pose.pose().transformPosition(
                        vertices[i].worldX(), vertices[i].worldY(), vertices[i].worldZ(), new Vector3f()
                    );

                    corners[i] = corner.set(-corner.x, -EntityModel.MODEL_Y_OFFSET - corner.y, corner.z);
                    cubeMin.min(corner);
                    cubeMax.max(corner);
                }

                Vector3f normal = pose.transformNormal(cube.polygons[face].normal(), new Vector3f()).mul(-1.0F, -1.0F, 1.0F);

                wraps &= Math.abs(normal.get(normal.maxComponent())) > AXIS_ALIGNED;
                faces[face] = corners;
                faceNormals[face] = normal;
            }

            for (int axis = 0; wraps && axis < 3; axis++) {
                wraps = shell.max.get(axis) - shell.min.get(axis) > CUT_TOLERANCE
                    && Math.abs(cubeMin.get(axis) - shell.min.get(axis)) <= MAX_INFLATION
                    && Math.abs(cubeMax.get(axis) - shell.max.get(axis)) <= MAX_INFLATION;
            }

            for (int face = 0; face < faces.length; face++) {
                ModelPart.Vertex[] vertices = cube.polygons[face].vertices();
                Vector3f[] corners = faces[face];
                Vector3f normal = faceNormals[face];

                if (wraps) {
                    int axis = normal.maxComponent();
                    float side = normal.get(axis) > 0.0F ? shell.max.get(axis) : shell.min.get(axis);
                    Vector3f acrossEdge = corners[1].sub(corners[0], new Vector3f());
                    Vector3f alongEdge = corners[3].sub(corners[0], new Vector3f());

                    for (int triangle = 0; triangle + 2 < shell.triangles.length; triangle += 3) {
                        boolean onSide = true;

                        for (int corner = 0; corner < 3; corner++) {
                            onSide &= Math.abs(skin.positions[shell.triangles[triangle + corner] * 3 + axis] - side) <= FACE_TOLERANCE;
                        }

                        if (!onSide) continue;

                        Vector3f[] points = new Vector3f[3];
                        float[] cornerS = new float[3];
                        float[] cornerT = new float[3];

                        for (int corner = 0; corner < 3; corner++) {
                            int position = shell.triangles[triangle + corner];

                            points[corner] = wrapped(
                                new Vector3f(skin.positions[position * 3], skin.positions[position * 3 + 1], skin.positions[position * 3 + 2]),
                                shell, cubeMin, cubeMax
                            );

                            Vector3f offset = points[corner].sub(corners[0], new Vector3f());

                            cornerS[corner] = offset.dot(acrossEdge) / acrossEdge.lengthSquared();
                            cornerT[corner] = offset.dot(alongEdge) / alongEdge.lengthSquared();
                        }

                        boolean seam = Math.abs(
                            (cornerS[1] - cornerS[0]) * (cornerT[2] - cornerT[0]) - (cornerS[2] - cornerS[0]) * (cornerT[1] - cornerT[0])
                        ) < SEAM_AREA;

                        for (int corner = 0; corner < 3; corner++) {
                            int position = shell.triangles[triangle + corner];
                            Vector3f point = points[corner];
                            Vector3f centroid = shell.centroids.get(skin.heaviestBones[position]);
                            float s = cornerS[corner];
                            float t = cornerT[corner];

                            if (seam && centroid != null) {
                                Vector3f toward = wrapped(centroid, shell, cubeMin, cubeMax).sub(point);

                                toward.fma(-toward.dot(normal) / normal.lengthSquared(), normal);

                                if (toward.lengthSquared() > CUT_TOLERANCE * CUT_TOLERANCE) {
                                    toward.normalize(SEAM_NUDGE);
                                    s += toward.dot(acrossEdge) / acrossEdge.lengthSquared();
                                    t += toward.dot(alongEdge) / alongEdge.lengthSquared();
                                }
                            }

                            float w0 = (1.0F - s) * (1.0F - t);
                            float w1 = s * (1.0F - t);
                            float w2 = s * t;
                            float w3 = (1.0F - s) * t;
                            int index = positions.size() / 3;
                            int from = boneIds.size();

                            positions.add(point.x);
                            positions.add(point.y);
                            positions.add(point.z);
                            normals.add(normal.x);
                            normals.add(normal.y);
                            normals.add(normal.z);
                            uvs.add(w0 * vertices[0].u() + w1 * vertices[1].u() + w2 * vertices[2].u() + w3 * vertices[3].u());
                            uvs.add(w0 * vertices[0].v() + w1 * vertices[1].v() + w2 * vertices[2].v() + w3 * vertices[3].v());

                            for (int entry = body.getDeformationRangeStart(position); entry < body.getDeformationRangeEnd(position); entry++) {
                                boneIds.add(body.getDeformationBone(entry));
                                weightIds.add(weights.size());
                                weights.add(body.getDeformationWeight(entry));
                            }

                            deformationFromTo.add(from);
                            deformationFromTo.add(boneIds.size());
                            part.add(new VertexBuilder(index, index, index));
                        }
                    }

                    continue;
                }

                float[] across = region.cuts(corners[0], corners[1]);
                float[] along = region.cuts(corners[0], corners[3]);
                // The four vertices of each cell the cuts make of the face, running around the cell
                int[][][] cells = new int[along.length - 1][across.length - 1][4];
                // Each edge two neighboring cells meet on: its two vertices in one cell, then the same ends in the other
                List<int[]> seams = new ArrayList<>();

                for (int row = 0; row < along.length - 1; row++) {
                    for (int column = 0; column < across.length - 1; column++) {
                        float[] cornerS = {across[column], across[column + 1], across[column + 1], across[column]};
                        float[] cornerT = {along[row], along[row], along[row + 1], along[row + 1]};
                        float middleS = (across[column] + across[column + 1]) * 0.5F;
                        float middleT = (along[row] + along[row + 1]) * 0.5F;

                        for (int corner = 0; corner < 4; corner++) {
                            float s = cornerS[corner];
                            float t = cornerT[corner];
                            float w0 = (1.0F - s) * (1.0F - t);
                            float w1 = s * (1.0F - t);
                            float w2 = s * t;
                            float w3 = (1.0F - s) * t;

                            // Read a little inside the cell, so a cell that ends on a joint reads its own side of it
                            region.transfer(
                                onFace(corners, Mth.lerp(SAMPLE_INSET, s, middleS), Mth.lerp(SAMPLE_INSET, t, middleT)), influence
                            );
                            cells[row][column][corner] = output.add(
                                onFace(corners, s, t),
                                normal,
                                w0 * vertices[0].u() + w1 * vertices[1].u() + w2 * vertices[2].u() + w3 * vertices[3].u(),
                                w0 * vertices[0].v() + w1 * vertices[1].v() + w2 * vertices[2].v() + w3 * vertices[3].v(),
                                influence
                            );
                        }

                        int[] cell = cells[row][column];

                        for (int index : new int[] {cell[0], cell[1], cell[2], cell[0], cell[2], cell[3]}) {
                            part.add(new VertexBuilder(index, index, index));
                        }

                        if (row > 0) seams.add(new int[] {cells[row - 1][column][3], cells[row - 1][column][2], cell[0], cell[1]});
                        if (column > 0) seams.add(new int[] {cells[row][column - 1][1], cells[row][column - 1][2], cell[0], cell[3]});
                    }
                }

                for (int[] seam : seams) {
                    float difference = 0.0F;

                    for (int end = 0; end < 2; end++) {
                        output.read(seam[end], influence);
                        output.read(seam[end + 2], compared);

                        for (int bone = 0; bone < influence.length; bone++) {
                            difference += Math.abs(influence[bone] - compared[bone]);
                        }

                        Arrays.fill(influence, 0.0F);
                        Arrays.fill(compared, 0.0F);
                    }

                    // Cells that agree on a seam move as one there
                    if (difference <= JOINT_TOLERANCE) continue;

                    // The two sides of a joint part when it bends. A middle row joins them, weighted like the
                    // row the body keeps between its own two sides, by triangles with no area until then
                    int[] middle = new int[2];

                    for (int end = 0; end < 2; end++) {
                        Vector3f point = new Vector3f(
                            positions.getFloat(seam[end] * 3), positions.getFloat(seam[end] * 3 + 1), positions.getFloat(seam[end] * 3 + 2)
                        );

                        if (!region.middle(point, influence)) output.read(seam[end], influence);

                        middle[end] = output.add(point, normal, uvs.getFloat(seam[end] * 2), uvs.getFloat(seam[end] * 2 + 1), influence);
                    }

                    for (int index : new int[] {
                        seam[0], seam[1], middle[1], seam[0], middle[1], middle[0],
                        middle[0], middle[1], seam[3], middle[0], seam[3], seam[2]
                    }) {
                        part.add(new VertexBuilder(index, index, index));
                    }
                }
            }
        });

        if (positions.isEmpty()) return null;

        List<NativeImage> paint = new ArrayList<>();

        try {
            for (Identifier texture : textures) {
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);

                if (resource.isEmpty()) continue;

                try (InputStream stream = resource.get().open()) {
                    paint.add(NativeImage.read(stream));
                } catch (IOException e) {
                    Akythera.LOGGER.warn("Failed at reading the armor texture {}", texture, e);
                }
            }

            for (List<VertexBuilder> part : paint.isEmpty() ? List.<List<VertexBuilder>>of() : builder.partBuilders.values()) {
                if (part.isEmpty()) continue;

                int lowest = part.getFirst().position();
                int highest = lowest;

                for (VertexBuilder vertex : part) {
                    float y = positions.getFloat(vertex.position() * 3 + 1);

                    if (y < positions.getFloat(lowest * 3 + 1)) lowest = vertex.position();
                    if (y > positions.getFloat(highest * 3 + 1)) highest = vertex.position();
                }

                int below = output.heaviestBone(lowest);
                float boundary = Float.NaN;

                for (VertexBuilder vertex : part) {
                    float y = positions.getFloat(vertex.position() * 3 + 1);

                    if (output.heaviestBone(vertex.position()) != below && (Float.isNaN(boundary) || y < boundary)) boundary = y;
                }

                if (Float.isNaN(boundary)) continue;

                float paintedBelow = 0.0F;
                float paintedAbove = 0.0F;

                for (int corner = 0; corner + 2 < part.size(); corner += 3) {
                    int[] triangle = {part.get(corner).position(), part.get(corner + 1).position(), part.get(corner + 2).position()};
                    Vector3f a = new Vector3f(positions.getFloat(triangle[0] * 3), positions.getFloat(triangle[0] * 3 + 1), positions.getFloat(triangle[0] * 3 + 2));
                    Vector3f b = new Vector3f(positions.getFloat(triangle[1] * 3), positions.getFloat(triangle[1] * 3 + 1), positions.getFloat(triangle[1] * 3 + 2));
                    Vector3f c = new Vector3f(positions.getFloat(triangle[2] * 3), positions.getFloat(triangle[2] * 3 + 1), positions.getFloat(triangle[2] * 3 + 2));
                    Vector3f cross = b.sub(a, new Vector3f()).cross(c.sub(a, new Vector3f()));

                    if (cross.lengthSquared() < NO_AREA || cross.maxComponent() == 1) continue;

                    int hitsBelow = 0;
                    int hitsAbove = 0;
                    int samples = 0;

                    for (int i = 1; i < PAINT_SAMPLES; i++) {
                        for (int j = 1; i + j < PAINT_SAMPLES; j++) {
                            float wa = (float) i / PAINT_SAMPLES;
                            float wb = (float) j / PAINT_SAMPLES;
                            float wc = 1.0F - wa - wb;
                            float u = wa * uvs.getFloat(triangle[0] * 2) + wb * uvs.getFloat(triangle[1] * 2) + wc * uvs.getFloat(triangle[2] * 2);
                            float v = wa * uvs.getFloat(triangle[0] * 2 + 1) + wb * uvs.getFloat(triangle[1] * 2 + 1) + wc * uvs.getFloat(triangle[2] * 2 + 1);
                            boolean painted = false;

                            samples++;

                            for (NativeImage image : paint) {
                                int x = Mth.floor(u * image.getWidth());
                                int y = Mth.floor(v * image.getHeight());

                                painted |= x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && ARGB.alpha(image.getPixel(x, y)) != 0;
                            }

                            if (!painted) continue;

                            if (wa * a.y + wb * b.y + wc * c.y < boundary) {
                                hitsBelow++;
                            } else {
                                hitsAbove++;
                            }
                        }
                    }

                    float area = (float) Math.sqrt(cross.lengthSquared()) * 0.5F;

                    paintedBelow += area * hitsBelow / samples;
                    paintedAbove += area * hitsAbove / samples;
                }

                float total = paintedBelow + paintedAbove;

                if (total <= 0.0F || Math.min(paintedBelow, paintedAbove) / total > MINORITY_PAINT) continue;

                int rides = paintedBelow >= paintedAbove ? below : output.heaviestBone(highest);

                for (VertexBuilder vertex : part) {
                    int from = deformationFromTo.getInt(vertex.position() * 2);

                    if (deformationFromTo.getInt(vertex.position() * 2 + 1) <= from) continue;

                    boneIds.set(from, rides);
                    weights.set(weightIds.getInt(from), 1.0F);
                    deformationFromTo.set(vertex.position() * 2 + 1, from + 1);
                }
            }
        } finally {
            paint.forEach(NativeImage::close);
        }

        builder.weights(weights.toFloatArray())
            .deformationFromToArray(deformationFromTo.toIntArray())
            .affectingBoneIdArray(boneIds.toIntArray())
            .affectingWeightIndexArray(weightIds.toIntArray());
        builder.positions(positions.toFloatArray())
            .normals(normals.toFloatArray())
            .uvs(uvs.toFloatArray());

        return builder;
    }

    private static Vector3f wrapped(final Vector3f bodyPoint, final Shell shell, final Vector3f cubeMin, final Vector3f cubeMax) {
        Vector3f point = new Vector3f();

        for (int component = 0; component < 3; component++) {
            float share = (bodyPoint.get(component) - shell.min.get(component)) / (shell.max.get(component) - shell.min.get(component));

            point.setComponent(component, Mth.lerp(share, cubeMin.get(component), cubeMax.get(component)));
        }

        return point;
    }

    /// The point of the quad `corners` at `s` of the way from its first corner to its second and `t` of the
    /// way to its fourth.
    private static Vector3f onFace(final Vector3f[] corners, final float s, final float t) {
        return new Vector3f(corners[0]).mul((1.0F - s) * (1.0F - t))
            .fma(s * (1.0F - t), corners[1])
            .fma(s * t, corners[2])
            .fma((1.0F - s) * t, corners[3]);
    }

    /// The arrays a build fills, one vertex at a time.
    private record Output(
        FloatArrayList positions,
        FloatArrayList normals,
        FloatArrayList uvs,
        FloatArrayList weights,
        IntArrayList deformationFromTo,
        IntArrayList boneIds,
        IntArrayList weightIds
    ) {
        /// Adds a vertex weighted by the heaviest [#MAX_INFLUENCES] bones of `influence`, which holds a weight
        /// per bone id and is emptied. Returns the index of the vertex.
        int add(final Vector3f point, final Vector3f normal, final float u, final float v, final float[] influence) {
            int index = positions.size() / 3;
            int from = boneIds.size();
            float kept = 0.0F;

            positions.add(point.x);
            positions.add(point.y);
            positions.add(point.z);
            normals.add(normal.x);
            normals.add(normal.y);
            normals.add(normal.z);
            uvs.add(u);
            uvs.add(v);

            for (int n = 0; n < MAX_INFLUENCES; n++) {
                int heaviest = 0;

                for (int bone = 1; bone < influence.length; bone++) {
                    if (influence[bone] > influence[heaviest]) heaviest = bone;
                }

                if (influence[heaviest] <= 0.0F) break;

                kept += influence[heaviest];
                boneIds.add(heaviest);
                weightIds.add(weights.size());
                weights.add(influence[heaviest]);
                influence[heaviest] = 0.0F;
            }

            for (int entry = from; entry < boneIds.size(); entry++) {
                weights.set(weightIds.getInt(entry), weights.getFloat(weightIds.getInt(entry)) / kept);
            }

            Arrays.fill(influence, 0.0F);
            deformationFromTo.add(from);
            deformationFromTo.add(boneIds.size());

            return index;
        }

        int heaviestBone(final int index) {
            int heaviest = -1;
            float heaviestWeight = 0.0F;

            for (int entry = deformationFromTo.getInt(index * 2); entry < deformationFromTo.getInt(index * 2 + 1); entry++) {
                if (weights.getFloat(weightIds.getInt(entry)) > heaviestWeight) {
                    heaviestWeight = weights.getFloat(weightIds.getInt(entry));
                    heaviest = boneIds.getInt(entry);
                }
            }

            return heaviest;
        }

        /// Adds the weights of the vertex at `index` into `influence`, by bone id.
        void read(final int index, final float[] influence) {
            for (int entry = deformationFromTo.getInt(index * 2); entry < deformationFromTo.getInt(index * 2 + 1); entry++) {
                influence[boneIds.getInt(entry)] += weights.getFloat(weightIds.getInt(entry));
            }
        }
    }

    /// Closes every baked mesh. The meshes they were baked over are about to close.
    public static void clear() {
        BAKED.values().forEach(baked -> baked.ifPresent(bake -> bake.mesh().close()));
        BAKED.clear();
    }

    /// One model rebuilt on an armature.
    ///
    /// @param item    the item the model was first baked for; other items worn with the same model share the bake
    /// @param slot    the slot that item was worn in
    /// @param builder what [#mesh] was built from
    public record Bake(
        Model<?> model,
        Armature armature,
        Item item,
        EquipmentSlot slot,
        List<Identifier> textures,
        SkeletalMesh.SkeletalMeshBuilder builder,
        SkeletalMesh mesh
    ) {
    }

    private record BakeKey(Model<?> model, Armature armature, SkeletalMesh body, List<Identifier> textures) {
    }

    /// The body a model is baked over, read once. Holds nothing without a body.
    private static final class Skin {
        private final @Nullable SkeletalMesh body;

        /// Bind-pose positions, three elements per position index.
        private final float[] positions;

        /// Position indices, three per triangle.
        private final int[] triangles;

        /// The bone holding the most weight of each position index, -1 for a position no bone drives.
        private final int[] heaviestBones;

        /// One past the highest bone id the body references.
        private final int boneLimit;

        /// The body's parts by name.
        private final Map<String, Shell> shells = new HashMap<>();

        Skin(final @Nullable SkeletalMesh body) {
            this.body = body;

            int positionCount = body == null ? 0 : body.getPositionCount();
            Vector3f position = new Vector3f();
            int limit = 0;

            positions = new float[positionCount * 3];
            heaviestBones = new int[positionCount];

            for (int i = 0; i < positionCount; i++) {
                body.getPosition(i, position);
                positions[i * 3] = position.x;
                positions[i * 3 + 1] = position.y;
                positions[i * 3 + 2] = position.z;

                float heaviest = 0.0F;

                heaviestBones[i] = -1;

                for (int entry = body.getDeformationRangeStart(i); entry < body.getDeformationRangeEnd(i); entry++) {
                    limit = Math.max(limit, body.getDeformationBone(entry) + 1);

                    if (body.getDeformationWeight(entry) > heaviest) {
                        heaviest = body.getDeformationWeight(entry);
                        heaviestBones[i] = body.getDeformationBone(entry);
                    }
                }
            }

            IntArrayList indices = new IntArrayList();

            Map<String, SkeletalMesh.SkeletalMeshPart> parts = body == null ? Map.of() : body.getParts();

            for (Map.Entry<String, SkeletalMesh.SkeletalMeshPart> part : parts.entrySet()) {
                int[] owned = new int[part.getValue().vertexCount()];
                Vector3f min = new Vector3f(Float.MAX_VALUE);
                Vector3f max = new Vector3f(-Float.MAX_VALUE);

                for (int vertex = 0; vertex < owned.length; vertex++) {
                    owned[vertex] = part.getValue().getPositionIndex(vertex);
                    indices.add(owned[vertex]);
                    body.getPosition(owned[vertex], position);
                    min.min(position);
                    max.max(position);
                }

                Map<Integer, Vector3f> centroids = new HashMap<>();
                Map<Integer, Integer> counts = new HashMap<>();

                for (int index : owned) {
                    body.getPosition(index, position);
                    centroids.computeIfAbsent(heaviestBones[index], _ -> new Vector3f()).add(position);
                    counts.merge(heaviestBones[index], 1, Integer::sum);
                }

                centroids.forEach((bone, sum) -> sum.div(counts.get(bone)));
                shells.put(part.getKey(), new Shell(owned, min, max, centroids));
            }

            triangles = indices.toIntArray();
            boneLimit = limit;
        }

        /// The triangles `bone` and the bones under it drive, not descending into a bone `root` holds a
        /// part named after. Every triangle when `bone` is null or drives none.
        Region region(final @Nullable Bone bone, final ModelPart root) {
            BitSet bones = new BitSet();
            Deque<Bone> open = new ArrayDeque<>();

            if (bone != null) open.push(bone);

            while (!open.isEmpty()) {
                Bone walked = open.pop();

                bones.set(walked.getId());

                for (Bone child : walked.getChildBones()) {
                    if (!root.hasChild(child.getName())) open.push(child);
                }
            }

            IntArrayList owned = new IntArrayList();

            for (int triangle = 0; triangle + 2 < triangles.length; triangle += 3) {
                boolean driven = true;

                for (int corner = 0; corner < 3; corner++) {
                    int heaviest = heaviestBones[triangles[triangle + corner]];

                    driven &= heaviest >= 0 && bones.get(heaviest);
                }

                if (driven) owned.add(triangle);
            }

            if (owned.isEmpty()) {
                for (int triangle = 0; triangle + 2 < triangles.length; triangle += 3) {
                    owned.add(triangle);
                }
            }

            float[][] coordinates = new float[3][];

            for (int axis = 0; axis < 3; axis++) {
                float[] sorted = new float[owned.size() * 3];

                for (int i = 0; i < owned.size(); i++) {
                    for (int corner = 0; corner < 3; corner++) {
                        sorted[i * 3 + corner] = positions[triangles[owned.getInt(i) + corner] * 3 + axis];
                    }
                }

                Arrays.sort(sorted);

                FloatArrayList distinct = new FloatArrayList();

                for (float coordinate : sorted) {
                    if (distinct.isEmpty() || coordinate - distinct.getFloat(distinct.size() - 1) > CUT_TOLERANCE) {
                        distinct.add(coordinate);
                    }
                }

                coordinates[axis] = distinct.toFloatArray();
            }

            // A vertex only triangles with no area use sits in the middle row of a joint
            BitSet solid = new BitSet();
            IntArrayList middleEdges = new IntArrayList();
            Vector3f[] corner = {new Vector3f(), new Vector3f(), new Vector3f()};

            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < owned.size(); i++) {
                    int triangle = owned.getInt(i);

                    for (int c = 0; c < 3; c++) {
                        int at = triangles[triangle + c] * 3;

                        corner[c].set(positions[at], positions[at + 1], positions[at + 2]);
                    }

                    boolean flat = corner[1].sub(corner[0], new Vector3f())
                        .cross(corner[2].sub(corner[0], new Vector3f()))
                        .lengthSquared() < NO_AREA;

                    for (int c = 0; c < 3; c++) {
                        int from = triangles[triangle + c];
                        int to = triangles[triangle + (c + 1) % 3];

                        if (pass == 0 && !flat) solid.set(from);

                        if (pass == 1 && flat && !solid.get(from) && !solid.get(to)
                            && corner[c].distanceSquared(corner[(c + 1) % 3]) > CUT_TOLERANCE * CUT_TOLERANCE
                        ) {
                            middleEdges.add(from);
                            middleEdges.add(to);
                        }
                    }
                }
            }

            return new Region(this, owned.toIntArray(), coordinates, middleEdges.toIntArray());
        }
    }

    /// One named part of a [Skin].
    ///
    /// @param triangles position indices, three per triangle
    /// @param min       the least coordinate its vertices reach, per axis
    /// @param max       the greatest coordinate its vertices reach, per axis
    private record Shell(int[] triangles, Vector3f min, Vector3f max, Map<Integer, Vector3f> centroids) {
    }

    /// The part of a [Skin] one model part is baked over.
    ///
    /// @param triangles   offsets into [Skin#triangles], one per triangle
    /// @param coordinates the distinct coordinates the triangles' vertices sit on, ascending, per axis
    /// @param middleEdges the edges of the middle rows of the body's joints, two position indices each
    private record Region(Skin skin, int[] triangles, float[][] coordinates, int[] middleEdges) {
        /// Adds the weights of the point of [#middleEdges] nearest `point` into `influence`, by bone id. False
        /// when none lies within [#MIDDLE_REACH].
        boolean middle(final Vector3f point, final float[] influence) {
            float nearest = MIDDLE_REACH * MIDDLE_REACH;
            int found = -1;
            float foundShare = 0.0F;
            Vector3f from = new Vector3f();
            Vector3f edge = new Vector3f();

            for (int e = 0; e + 1 < middleEdges.length; e += 2) {
                from.set(skin.positions[middleEdges[e] * 3], skin.positions[middleEdges[e] * 3 + 1], skin.positions[middleEdges[e] * 3 + 2]);
                edge.set(skin.positions[middleEdges[e + 1] * 3], skin.positions[middleEdges[e + 1] * 3 + 1], skin.positions[middleEdges[e + 1] * 3 + 2]).sub(from);

                float share = Math.clamp(point.sub(from, new Vector3f()).dot(edge) / edge.lengthSquared(), 0.0F, 1.0F);
                float distance = from.fma(share, edge, new Vector3f()).distanceSquared(point);

                if (distance < nearest) {
                    nearest = distance;
                    found = e;
                    foundShare = share;
                }
            }

            if (found < 0) return false;

            float[] shares = {1.0F - foundShare, foundShare};

            for (int end = 0; end < 2; end++) {
                int position = middleEdges[found + end];

                for (int entry = skin.body.getDeformationRangeStart(position); entry < skin.body.getDeformationRangeEnd(position); entry++) {
                    influence[skin.body.getDeformationBone(entry)] += shares[end] * skin.body.getDeformationWeight(entry);
                }
            }

            return true;
        }

        /// Where the edge from `from` to `to` is cut, as ascending fractions of it with both ends
        /// included: on [#coordinates] when the edge follows an axis, evenly otherwise.
        float[] cuts(final Vector3f from, final Vector3f to) {
            Vector3f edge = to.sub(from, new Vector3f());
            float length = edge.length();
            FloatArrayList cuts = new FloatArrayList();

            cuts.add(0.0F);

            if (length > CUT_TOLERANCE) {
                int axis = edge.maxComponent();
                float span = edge.get(axis);

                if (Math.abs(span) > length * AXIS_ALIGNED) {
                    for (float coordinate : coordinates[axis]) {
                        float fraction = (coordinate - from.get(axis)) / span;

                        if (fraction * length > CUT_TOLERANCE && (1.0F - fraction) * length > CUT_TOLERANCE) {
                            cuts.add(fraction);
                        }
                    }
                } else {
                    int segments = Mth.ceil(length / MAX_EDGE);

                    for (int i = 1; i < segments; i++) {
                        cuts.add((float) i / segments);
                    }
                }
            }

            cuts.add(1.0F);

            float[] sorted = cuts.toFloatArray();

            Arrays.sort(sorted);

            return sorted;
        }

        /// Adds the weights of the point of [#triangles] nearest `point` into `influence`, by bone id.
        void transfer(final Vector3f point, final float[] influence) {
            float[] positions = skin.positions;
            float nearest = Float.MAX_VALUE;
            int found = -1;
            float foundV = 0.0F;
            float foundW = 0.0F;

            for (int triangle : triangles) {
                int a = skin.triangles[triangle] * 3;
                int b = skin.triangles[triangle + 1] * 3;
                int c = skin.triangles[triangle + 2] * 3;
                float abX = positions[b] - positions[a];
                float abY = positions[b + 1] - positions[a + 1];
                float abZ = positions[b + 2] - positions[a + 2];
                float acX = positions[c] - positions[a];
                float acY = positions[c + 1] - positions[a + 1];
                float acZ = positions[c + 2] - positions[a + 2];
                float apX = point.x - positions[a];
                float apY = point.y - positions[a + 1];
                float apZ = point.z - positions[a + 2];
                float d1 = abX * apX + abY * apY + abZ * apZ;
                float d2 = acX * apX + acY * apY + acZ * apZ;
                float d3 = d1 - (abX * abX + abY * abY + abZ * abZ);
                float d4 = d2 - (acX * abX + acY * abY + acZ * abZ);
                float d5 = d1 - (abX * acX + abY * acY + abZ * acZ);
                float d6 = d2 - (acX * acX + acY * acY + acZ * acZ);
                float va = d3 * d6 - d5 * d4;
                float vb = d5 * d2 - d1 * d6;
                float vc = d1 * d4 - d3 * d2;
                float v;
                float w;

                if (d1 <= 0.0F && d2 <= 0.0F) {
                    v = 0.0F;
                    w = 0.0F;
                } else if (d3 >= 0.0F && d4 <= d3) {
                    v = 1.0F;
                    w = 0.0F;
                } else if (vc <= 0.0F && d1 >= 0.0F && d3 <= 0.0F) {
                    v = d1 / (d1 - d3);
                    w = 0.0F;
                } else if (d6 >= 0.0F && d5 <= d6) {
                    v = 0.0F;
                    w = 1.0F;
                } else if (vb <= 0.0F && d2 >= 0.0F && d6 <= 0.0F) {
                    v = 0.0F;
                    w = d2 / (d2 - d6);
                } else if (va <= 0.0F && d4 - d3 >= 0.0F && d5 - d6 >= 0.0F) {
                    w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
                    v = 1.0F - w;
                } else {
                    v = vb / (va + vb + vc);
                    w = vc / (va + vb + vc);
                }

                float offX = apX - abX * v - acX * w;
                float offY = apY - abY * v - acY * w;
                float offZ = apZ - abZ * v - acZ * w;
                float distance = offX * offX + offY * offY + offZ * offZ;

                // A triangle with no area answers NaN, which is never nearer
                if (distance < nearest) {
                    nearest = distance;
                    found = triangle;
                    foundV = v;
                    foundW = w;
                }
            }

            if (found < 0) return;

            float[] shares = {1.0F - foundV - foundW, foundV, foundW};

            for (int corner = 0; corner < 3; corner++) {
                int position = skin.triangles[found + corner];

                for (int entry = skin.body.getDeformationRangeStart(position); entry < skin.body.getDeformationRangeEnd(position); entry++) {
                    influence[skin.body.getDeformationBone(entry)] += shares[corner] * skin.body.getDeformationWeight(entry);
                }
            }
        }
    }
}
