package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.SweptColliderQuery;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.OrientedBoxColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.SweptColliderComponent;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Strikes the entities the colliders under [#socket] sweep through over the window, each entity once, by
/// [LivingEntityPatch#hurtTarget] without vanilla attack effects. The struck entities are kept in
/// [LivingEntityPatch#getStruckEntities].
public final class LivingHurtNotifyState extends AnimationNotifyState<LivingEntityPatch<?>> {
    public static final MapCodec<LivingHurtNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            Codec.STRING.fieldOf("socket").forGetter(LivingHurtNotifyState::socket),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_targets", Integer.MAX_VALUE).forGetter(LivingHurtNotifyState::maxTargets),
            Codec.BOOL.optionalFieldOf("shared", false).forGetter(LivingHurtNotifyState::shared),
            Codec.BOOL.optionalFieldOf("ignore_invulnerable_time", false).forGetter(LivingHurtNotifyState::ignoreInvulnerableTime)
        ).apply(inst, LivingHurtNotifyState::new)
    );

    /// The [ComponentAttachable#getComponentAt] path of the host whose colliders strike.
    private final String socket;

    /// How many entities the window may strike, counted over [#shared]'s range.
    private final int maxTargets;

    /// Whether the entities the owner's other [LivingHurtNotifyState]s struck count as struck by this one too.
    private final boolean shared;

    /// Whether a target's [Entity#invulnerableTime] is cleared before each strike.
    private final boolean ignoreInvulnerableTime;

    public LivingHurtNotifyState(final String socket, final int maxTargets, final boolean shared, final boolean ignoreInvulnerableTime) {
        super(LogicalSide.SERVER, NotifyTriggerThread.WORLD);

        this.socket = socket;
        this.maxTargets = maxTargets;
        this.shared = shared;
        this.ignoreInvulnerableTime = ignoreInvulnerableTime;
    }

    @Override
    public void onBegin(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
        owner.getStruckEntities().put(this, new HashSet<>());
    }

    /// Sweeps each collider from its last tick's placement to this tick's ([SweptColliderQuery#slice]) and strikes
    /// every entity whose bounding box a slice overlaps, until [#maxTargets] are struck.
    @Override
    public void onTick(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
        Set<Entity> ownStruck = owner.getStruckEntities().get(this);

        if (ownStruck == null || !(owner.getComponentAt(socket) instanceof ComponentAttachable host)) return;

        Set<Entity> struck = shared
            ? owner.getStruckEntities().values().stream().flatMap(Collection::stream).collect(HashSet::new, Set::add, Set::addAll)
            : ownStruck;

        if (struck.size() >= maxTargets) return;

        Transform previousEntity = owner.getModelTransform(0.0F);
        Transform currentEntity = owner.getModelTransform(1.0F);
        List<SweptColliderComponent> slices = new ArrayList<>();

        for (ColliderComponent<?> collider : host.findComponentsBelow(ColliderComponent.class)) {
            slices.addAll(SweptColliderQuery.slice(
                collider.getCollider(),
                previousEntity.mul(collider.previousParentModelTransform()),
                currentEntity.mul(collider.parentModelTransform())
            ));
        }

        if (slices.isEmpty()) return;

        AABB sweptArea = slices.stream().map(ColliderComponent::getOuterAABB).reduce(AABB::minmax).orElseThrow();
        Entity attacker = owner.getOriginal();

        List<Entity> targets = attacker.level().getEntities(attacker, sweptArea, entity ->
            entity.isAttackable() && entity.isAlive() && !struck.contains(entity) && !attacker.isPassengerOfSameVehicle(entity)
        );

        for (Entity target : targets) {
            if (struck.size() >= maxTargets) return;

            OrientedBoxColliderComponent targetBox = OrientedBoxColliderComponent.fromAABB(target.getBoundingBox());

            if (slices.stream().noneMatch(slice -> CollisionDetectionLogics.overlap(slice, targetBox))) continue;

            ownStruck.add(target);
            struck.add(target);

            if (ignoreInvulnerableTime) target.invulnerableTime = 0;

            owner.hurtTarget(target, false);
        }
    }

    @Override
    public void onEnd(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
        owner.getStruckEntities().remove(this);
    }

    public String socket() {
        return socket;
    }

    public int maxTargets() {
        return maxTargets;
    }

    public boolean shared() {
        return shared;
    }

    public boolean ignoreInvulnerableTime() {
        return ignoreInvulnerableTime;
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
