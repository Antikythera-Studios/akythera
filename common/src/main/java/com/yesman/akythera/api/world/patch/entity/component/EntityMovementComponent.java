package com.yesman.akythera.api.world.patch.entity.component;

import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.impl.MovementComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/// [MovementComponent] of an entity patch: moves and turns the [EntityPatch#getOriginal] entity.
public final class EntityMovementComponent implements MovementComponent, DynamicPatchComponent<EntityPatch<?>> {
    private EntityPatch<?> owner;

    private boolean enabled = true;

    public EntityMovementComponent(final EntityPatch<?> owner) {
        this.owner = owner;
    }

    // ***************************************************************
    // MovementComponent
    // ***************************************************************

    @Override
    public void moveTo(final Vec3 position) {
        owner.getOriginal().setPos(position);
    }

    @Override
    public void move(final Vec3 delta) {
        owner.getOriginal().move(MoverType.SELF, delta);
    }

    @Override
    public void lookAt(final Vec3 target) {
        owner.getOriginal().lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    @Override
    public void lookAt(final float xRot, final float yRot) {
        owner.getOriginal().setXRot(xRot);
        owner.getOriginal().setYRot(yRot);
        owner.getOriginal().setYHeadRot(yRot);
    }

    @Override
    public Vec3 toWorldVector(final Vec3 relative) {
        return owner.fromRelativeToWorldVector(relative, false);
    }

    // ***************************************************************
    // DynamicPatchComponent
    // ***************************************************************

    @Override
    public EntityPatch<?> getOwner() {
        return owner;
    }

    @Override
    public EntityMovementComponent getRenewed(final EntityPatch<?> newOwner) {
        owner = newOwner;
        return this;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
