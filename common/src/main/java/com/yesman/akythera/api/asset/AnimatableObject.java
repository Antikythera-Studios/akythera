package com.yesman.akythera.api.asset;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import net.minecraft.client.model.EntityModel;
import org.jspecify.annotations.Nullable;

/// Any kind of objects that has [ArmatureComponent] as its child component.
///
/// Requires a renderer inherits [SkeletalMeshRenderer] to be drawn in the screen,
/// or [EntityModel] itself that is powered by Player Animation Library. (Entities only)
public interface AnimatableObject {
    /// Returns armature component
    @Nullable ArmatureComponent getArmatureComponent();
}
