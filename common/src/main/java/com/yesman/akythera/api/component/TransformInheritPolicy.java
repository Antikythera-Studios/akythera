package com.yesman.akythera.api.component;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.util.StringRepresentable;
import org.joml.Quaterniond;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/// The elements of its parent space a [TransformComponent] takes on. The location always follows the parent in full:
/// the parent's location, rotation and scale place [TransformComponent#localTransform]'s location. The parent's
/// rotation and scale reach the component's own rotation and scale only where the policy lists them.
public enum TransformInheritPolicy implements StringRepresentable {
    LOC(false, false),
    LOC_ROT(true, false),
    LOC_ROT_SCA(true, true);

    public static final Codec<TransformInheritPolicy> CODEC = StringRepresentable.fromEnum(TransformInheritPolicy::values);

    private final boolean inheritsRotation;
    private final boolean inheritsScale;

    TransformInheritPolicy(final boolean inheritsRotation, final boolean inheritsScale) {
        this.inheritsRotation = inheritsRotation;
        this.inheritsScale = inheritsScale;
    }

    public boolean inheritsRotation() {
        return inheritsRotation;
    }

    public boolean inheritsScale() {
        return inheritsScale;
    }

    /// `local` placed in `parent`, taking on the parent elements this policy lists. [#LOC_ROT_SCA] is
    /// [Transform#mul].
    public Transform inherit(final Transform parent, final Transform local) {
        return new Transform(
            parent.mul(local.loc()),
            inheritsRotation ? parent.rot().mul(local.rot(), new Quaterniond()) : new Quaterniond(local.rot()),
            inheritsScale ? parent.sca().multiply(local.sca()) : local.sca()
        );
    }

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
