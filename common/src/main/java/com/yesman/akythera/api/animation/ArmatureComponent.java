package com.yesman.akythera.api.animation;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ReloadablePatchComponent;
import com.yesman.akythera.api.component.TickablePatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.asset.armature.Armature;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/// Module for skeletal mesh armature. Animator and armature are paired since both are coupled to work.
public class ArmatureComponent
    implements ReloadablePatchComponent<EntityPatch<Entity>>, TickablePatchComponent<EntityPatch<Entity>> {
    private final AssetAccessor<? extends Armature> armature;
    private final Animator animator;
    private final @Nullable RootMotionComponent rootMotionComponent;

    private EntityPatch<Entity> owner;

    @SuppressWarnings("unchecked")
    public ArmatureComponent(
        final EntityPatch<Entity> owner,
        final AssetAccessor<? extends Armature> armature,
        final Animator animator,
        final boolean useRootMotion
    ) {
        this.armature = armature;
        this.animator = animator;
        this.owner = owner;

        rootMotionComponent = useRootMotion && owner instanceof EntityPatch<?> entityPatch
            ? new RootMotionComponent((EntityPatch<Entity>)entityPatch) : null;
    }

    @Override
    public void tick() {
        getAnimator().tick();
        if (rootMotionComponent != null) rootMotionComponent.tick();
    }

    @Override
    public void frameUpdate(float frameTime, float partialTick) {
        getAnimator().update(frameTime, partialTick);
        if (rootMotionComponent != null) rootMotionComponent.frameUpdate(frameTime);
    }

    public Animator getAnimator() {
        return animator;
    }

    public AssetAccessor<? extends Armature> getArmature() {
        return armature;
    }

    // The call site must be in-game time. Crashed when try to use this component in preview model.
    public @Nullable RootMotionComponent getRootMotionComponent() {
        return rootMotionComponent;
    }

    // Null if preview armature component
    @Override
    public EntityPatch<Entity> getOwner() {
        return owner;
    }

    @Override
    public ArmatureComponent getRenewed(final EntityPatch<Entity> newOwner) {
        // No deep-copying the component. Just change the owner.
        owner = newOwner;
        return this;
    }
}
