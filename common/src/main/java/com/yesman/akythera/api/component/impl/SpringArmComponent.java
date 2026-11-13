package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// An arm the components attached under it hang off. The arm starts at its pivot, [#localTransform] inside the host's
/// space, and runs [#length] blocks along the pivot's +Z, backward. [#modelTransform] is the arm's end, the space its
/// children live in.
///
/// Placed by an [ArmatureComponent] (attached under one, or under its sockets) the arm rides the body. Anywhere else
/// it turns with a yaw of the caller's choice, see [#pivotWorldTransform].
public final class SpringArmComponent implements TransformComponent, TickableComponent, ComponentAttachable, DynamicPatchComponent<ComponentAttachable> {
    private ComponentAttachable owner;

    private boolean enabled = true;

    private Transform localTransform;

    private final TransformInheritPolicy transformInheritPolicy;

    /// How far the arm's end is from its pivot, in blocks.
    private double length;

    /// The attached components by name, in declaration order.
    private final Map<String, PatchComponent> components = new LinkedHashMap<>();

    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;

    public SpringArmComponent(
        final ComponentAttachable owner,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final double length,
        final Map<String, ComponentBlueprint<?, ?>> childBlueprints
    ) {
        this.owner = owner;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
        this.length = length;

        ComponentBlueprint.instantiateAll(childBlueprints, this, owner.logicalSide().validInClientSide(), components::put);

        uniqueComponents = ComponentAttachable.indexUnique(components);
    }

    /// Whether an [ArmatureComponent] places this arm: one of the hosts above it is an armature.
    public boolean ridesArmature() {
        for (Object walked = owner; walked instanceof TransformComponent transform; walked = transform.host()) {
            if (walked instanceof ArmatureComponent) return true;
        }

        return false;
    }

    public double getLength() {
        return length;
    }

    public void setLength(final double length) {
        this.length = length;
    }

    /// The pivot's transform in world space `partialTick` after the last tick. Unless the arm [#ridesArmature], the
    /// patch's model space is turned by `yRot`, in degrees, in place of the body yaw. Null when the walk reaches no
    /// entity.
    public @Nullable Transform pivotWorldTransform(final float partialTick, final float yRot) {
        EntityPatch<?> entity = TransformComponent.entityOf(owner);

        if (entity == null) return null;

        Transform body = entity.getModelTransform(partialTick);
        Transform pivot = transformInheritPolicy.inherit(parentModelTransform(), localTransform);

        if (ridesArmature()) return body.mul(pivot);

        return new Transform(body.loc(), new Quaterniond().rotationY((180.0F - yRot) * Mth.DEG_TO_RAD), body.sca()).mul(pivot);
    }

    /// The arm from the pivot to its end, in the pivot's space.
    private Transform arm() {
        return Transform.fromTranslation(new Vec3(0.0D, 0.0D, length));
    }

    // ***************************************************************
    // ComponentAttachable
    // ***************************************************************

    @Override
    public Map<String, PatchComponent> getComponents() {
        return components;
    }

    @Override
    public LogicalSide logicalSide() {
        return owner.logicalSide();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends UniqueComponent> @Nullable C getComponent(final Class<C> type) {
        UniqueComponent component = uniqueComponents.get(type);

        return component != null && component.isEnabled() ? (C)component : null;
    }

    // ***************************************************************
    // TickableComponent
    // ***************************************************************

    @Override
    public void tick() {
        components.values().forEach(component -> {
            if (component.isEnabled() && component instanceof TickableComponent tickable) tickable.tick();
        });
    }

    // ***************************************************************
    // TransformComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public ComponentAttachable host() {
        return owner;
    }

    @Override
    public Transform modelTransform() {
        return TransformComponent.super.modelTransform().mul(arm());
    }

    @Override
    public Transform previousModelTransform() {
        return TransformComponent.super.previousModelTransform().mul(arm());
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return transformInheritPolicy;
    }

    // ***************************************************************
    // DynamicPatchComponent
    // ***************************************************************

    @Override
    public ComponentAttachable getOwner() {
        return owner;
    }

    @Override
    public SpringArmComponent getRenewed(final ComponentAttachable newOwner) {
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
