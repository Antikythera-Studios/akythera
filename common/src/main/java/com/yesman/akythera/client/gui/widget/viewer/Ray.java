package com.yesman.akythera.client.gui.widget.viewer;

import org.joml.Vector3f;

/// A picking ray in [Model3DViewer] world space, built by [Model3DViewer#buildRay].
public record Ray(Vector3f origin, Vector3f direction) {
    /// The point `t` units along the ray. `direction` is normalized, so `t` is a distance.
    public Vector3f at(float t) {
        return new Vector3f(direction).mul(t).add(origin);
    }

    /// Intersection distance with the plane through `planeOrigin`, or -1 when the ray is parallel to
    /// it or hits it behind the camera.
    public float intersectPlane(Vector3f planeOrigin, Vector3f normal) {
        float denominator = direction.dot(normal);

        if (Math.abs(denominator) < 1.0e-4F) return -1.0F;

        float t = new Vector3f(planeOrigin).sub(origin).dot(normal) / denominator;

        return t < 0.0F ? -1.0F : t;
    }
}