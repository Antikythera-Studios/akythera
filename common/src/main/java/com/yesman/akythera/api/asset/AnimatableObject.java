package com.yesman.akythera.api.asset;

import com.yesman.akythera.api.animation.ArmatureComponent;
import com.yesman.akythera.client.core.renderer.SkeletalMeshRenderer;
import net.minecraft.client.model.EntityModel;
import org.jspecify.annotations.Nullable;

/// Any kind of objects that has an armature with a bone hierarchy and an animator.
///
/// Requires a renderer inherits [SkeletalMeshRenderer] to be drawn in the screen,
/// or [EntityModel] that is powered by Player Animation Library. (Entities only)
public interface AnimatableObject {
    /// Returns armature component
    @Nullable ArmatureComponent getArmatureComponent();
}
