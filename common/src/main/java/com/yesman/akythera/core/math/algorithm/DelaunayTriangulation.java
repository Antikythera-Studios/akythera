package com.yesman.akythera.core.math.algorithm;

import com.yesman.akythera.core.util.helper.MathUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/// Divides 2D space into triangles as equal as possible. No other
/// triangle's circumcircles are wrapping another triangle in division.
///
/// Refer to the detail algorithm [here](https://en.wikipedia.org/wiki/Delaunay_triangulation)
public final class DelaunayTriangulation<T extends DelaunayTriangulation.DelaunayPoint> {
    /// Minimum X axis value in space that points can have
    private final float minX;

    /// Minimum Y axis value in space that points can have
    private final float minY;

    /// Maximum X axis value in space that points can have
    private final float maxX;

    /// Maximum Y axis value in space that points can have
    private final float maxY;

    /// a X axis correction to make acute triangles
    private final float xCorrection;

    /// a Y axis correction to make acute triangles
    private final float yCorrection;

    /// Placed points by [#addPoint]. Identified by index.
    private final Int2ObjectMap<T> points = new Int2ObjectArrayMap<>();

    /// triangles that occupies the space
    private final List<Triangle> triangles = new ArrayList<>();

    /// Last assigned ID
    private int id;

    /// Used for creating temporary node
    private final BiFunction<Float, Float, T> defaultPointProvider;

    public DelaunayTriangulation(final float x1, final float y1, final float x2, final float y2, final BiFunction<Float, Float, T> defaultPointProvider) {
        minX = Math.min(x1, x2);
        minY = Math.min(y1, y2);
        maxX = Math.max(x1, x2);
        maxY = Math.max(y1, y2);

        // Apply axis correction to axis that has a shorter width
        xCorrection = Math.max(1.0F, (maxY - minY) / (maxX - minX));
        yCorrection = Math.max(1.0F, (maxX - minX) / (maxY - minY));

        this.defaultPointProvider = defaultPointProvider;
    }

    /// Put a new point in delaunay triangulation space
    ///
    /// The overhead would not suitable to be called in ticking events
    /// so avoid updating points too much.
    ///
    /// Must call [#reloadTriangles] to triangulate space for added points
    public T addPoint(final T point) {
        point.setIndex(id++);
        return points.put(point.index(), point);
    }

    /// Returns a node by a given index
    public T getPoint(final int index) {
        return points.get(index);
    }

    /// Removes a point by index
    ///
    /// Must call [#reloadTriangles] to triangulate space for added points
    public void removePoint(final int index) {
        points.remove(index);
    }

    public float minX() {
        return minX;
    }

    public float minY() {
        return minY;
    }

    public float maxX() {
        return maxX;
    }

    public float maxY() {
        return maxY;
    }

    public Collection<T> getAllPoints() {
        return points.values();
    }

    /// Returns an unmodifiable view of the current triangulation
    public List<Triangle> getTriangles() {
        return Collections.unmodifiableList(triangles);
    }

    /// Rebuild triangles by starting external triangle covers all space
    ///
    /// Cause a lot of overheads. Only use in initialization or debugging purpose.
    public void reloadTriangles() {
        triangles.clear();

        if (points.size() < 3) return;

        List<T> pts = new ArrayList<>(points.values());
        int n = pts.size();
        float minX = this.minX, minY = this.minY;
        float maxX = this.maxX, maxY = this.maxY;

        for (T p : points.values()) {
            if (p.x() < minX) minX = p.x();
            if (p.y() < minY) minY = p.y();
            if (p.x() > maxX) maxX = p.x();
            if (p.y() > maxY) maxY = p.y();
        }

        float midX = (minX + maxX) * 0.5F;
        float midY = (minY + maxY) * 0.5F;
        float xSize = (maxX - minX) * 100.0F;
        float ySize = (maxY - minY) * 100.0F;

        T external1 = defaultPointProvider.apply(midX - 2 * xSize, midY - ySize);
        external1.setIndex(n);
        T external2 = defaultPointProvider.apply(midX + 2 * xSize, midY - ySize);
        external2.setIndex(n+1);
        T external3 = defaultPointProvider.apply(midX, midY + 2 * ySize);
        external3.setIndex(n+2);
        // Make a big covering triangle
        pts.add(external1);
        pts.add(external2);
        pts.add(external3);

        triangles.add(new Triangle(n, n + 1, n + 2));

        for (int i = 0; i < n; i++) {
            T p = pts.get(i);
            List<Triangle> triToRemove = new ArrayList<>();

            for (Triangle t : triangles) {
                if (inCircumcircle(p, pts.get(t.a()), pts.get(t.b()), pts.get(t.c()))) {
                    triToRemove.add(t);
                }
            }

            List<int[]> boundary = new ArrayList<>();

            for (Triangle t : triToRemove) {
                int[][] edges = {{t.a(), t.b()}, {t.b(), t.c()}, {t.c(), t.a()}};

                for (int[] edge : edges) {
                    boolean shared = false;

                    for (Triangle other : triToRemove) {
                        if (other != t && hasEdge(other, edge[0], edge[1])) {
                            shared = true;
                            break;
                        }
                    }

                    if (!shared) boundary.add(edge);
                }
            }

            triangles.removeAll(triToRemove);

            for (int[] edge : boundary) {
                triangles.add(new Triangle(edge[0], edge[1], i));
            }
        }

        // Removes triangles that contain external triangle's points
        triangles.removeIf(t -> t.a() >= n || t.b() >= n || t.c() >= n);
    }

    /// Runs a task from a specific point in triangulated space with points consist a containing triangle,
    /// and barycentric weights of each point.
    public void doTaskByBarycentricWeights(final float x, final float y, final DelaunayTask<T> task) {
        for (Triangle t : triangles) {
            T p1 = points.get(t.a());
            T p2 = points.get(t.b());
            T p3 = points.get(t.c());
            float[] w = barycentricWeights(x, y, p1, p2, p3);

            if (w[0] >= -MathUtils.EPSILON && w[1] >= -MathUtils.EPSILON && w[2] >= -MathUtils.EPSILON) {
                task.doTask(p1, w[0], p2, w[1], p3, w[2]);
                break;
            }
        }
    }

    /// Public counterpart to the private [#barycentricWeights]. Use this when
    /// the triangle is already known (e.g. a cached previous-tick triangle in
    /// a driver's hot path).
    ///
    /// Returned array is `[wA, wB, wC]`. The point lies inside the triangle
    /// iff each weight is `>= -EPSILON`.
    public float[] barycentricWeightsOf(
        final float x,
        final float y,
        final DelaunayPoint a,
        final DelaunayPoint b,
        final DelaunayPoint c
    ) {
        return barycentricWeights(x, y, a, b, c);
    }

    /// @return an index of a triangle that contains given point. -1 when found no triangle contains a given point.
    public int findContainingTriangle(final float x, final float y) {
        for (int i = 0; i < triangles.size(); i++) {
            Triangle t = triangles.get(i);
            float[] w = barycentricWeights(x, y, points.get(t.a()), points.get(t.b()), points.get(t.c()));

            if (w[0] >= -MathUtils.EPSILON && w[1] >= -MathUtils.EPSILON && w[2] >= -MathUtils.EPSILON) {
                return i;
            }
        }

        return -1;
    }

    /// @param p target point to check if it's in circumcircle
    /// @param a a point consisting a triangle
    /// @param b a point consisting a triangle
    /// @param c a point consisting a triangle
    private boolean inCircumcircle(final DelaunayPoint p, final DelaunayPoint a, final DelaunayPoint b, final DelaunayPoint c) {
        float ppx = p.x() * xCorrection;
        float ppy = p.y() * yCorrection;
        float apx = a.x() * xCorrection;
        float apy = a.y() * yCorrection;
        float bpx = b.x() * xCorrection;
        float bpy = b.y() * yCorrection;
        float cpx = c.x() * xCorrection;
        float cpy = c.y() * yCorrection;

        float ax = apx - ppx, ay = apy - ppy;
        float bx = bpx - ppx, by = bpy - ppy;
        float cx = cpx - ppx, cy = cpy - ppy;
        float det = ax * (by * (cx * cx + cy * cy) - cy * (bx * bx + by * by))
                   - ay * (bx * (cx * cx + cy * cy) - cx * (bx * bx + by * by))
                   + (ax * ax + ay * ay) * (bx * cy - by * cx);
        float orient = (bpx - apx) * (cpy - apy) - (bpy - apy) * (cpx - apx);

        return orient > 0 ? det > 0 : det < 0;
    }

    private static boolean hasEdge(final Triangle t, final int a, final int b) {
        return (t.a() == a || t.b() == a || t.c() == a) && (t.a() == b || t.b() == b || t.c() == b);
    }

    /// Barycentric weights (wA, wB, wC) for point (x, y) inside triangle (A, B, C)
    ///
    /// @return 3 floating points for (wA, wB, wC)
    private float[] barycentricWeights(
        final float x,
        final float y,
        final DelaunayPoint a,
        final DelaunayPoint b,
        final DelaunayPoint c
    ) {
        float xCorrected = x * xCorrection;
        float yCorrected = y * yCorrection;
        float apx = a.x() * xCorrection;
        float apy = a.y() * yCorrection;
        float bpx = b.x() * xCorrection;
        float bpy = b.y() * yCorrection;
        float cpx = c.x() * xCorrection;
        float cpy = c.y() * yCorrection;

        float d = (bpy - cpy) * (apx - cpx) + (cpx - bpx) * (apy - cpy);

        if (Math.abs(d) < 1e-10f) {
            return new float[]{1.0f / 3, 1.0f / 3, 1.0f / 3};
        }

        float wa = ((bpy - cpy) * (xCorrected - cpx) + (cpx - bpx) * (yCorrected - cpy)) / d;
        float wb = ((cpy - apy) * (xCorrected - cpx) + (apx - cpx) * (yCorrected - cpy)) / d;

        return new float[]{wa, wb, 1.0f - wa - wb};
    }

    /// Each int represents index in [#points]
    public record Triangle(int a, int b, int c) {}

    public interface DelaunayTask<T extends DelaunayPoint> {
        void doTask(T p1, float weight1, T p2, float weight2, T p3, float weight3);
    }

    public interface DelaunayPoint {
        /// would be clamped by [#minX] ~ [#maxX]
        float x();

        /// would be clamped by [#minY] ~ [#maxY]
        float y();

        /// An index to be distinguished in delaunay space
        int index();

        /// Index should be modifiable.
        void setIndex(final int index);
    }
}
