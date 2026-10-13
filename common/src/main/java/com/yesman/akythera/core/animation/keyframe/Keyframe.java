package com.yesman.akythera.core.animation.keyframe;

import com.yesman.akythera.core.math.Transform;
import org.jspecify.annotations.NonNull;

/// A timestamp & bone transform pair to be recorded into an animation
public record Keyframe(float timeStamp, Transform transform) {
    public static Keyframe deepCopy(Keyframe other) {
        return new Keyframe(other.timeStamp, other.transform);
    }

    @Override
    public @NonNull String toString() {
        return "Keyframe[Time: " + this.timeStamp + ", " + (this.transform == null ? "null" : this.transform.toString()) + "]";
    }
}