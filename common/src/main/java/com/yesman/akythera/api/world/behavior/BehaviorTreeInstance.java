package com.yesman.akythera.api.world.behavior;

import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.world.behavior.script.BehaviorTreeScript;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/// A behavior tree asset built for one brain: its blackboard, the runner of its root, and what it perceives. Never
/// saved.
public final class BehaviorTreeInstance implements DataDrivenScript<BrainComponent, BehaviorTreeBlueprint> {
    private final BrainComponent brain;

    private final Holder.Reference<BehaviorTreeBlueprint> blueprint;

    /// The values of the asset's variables for this brain, which the event graph runs on.
    private final ScriptVariables blackboard;

    private final BlueprintExecutor executor;

    private final BehaviorTreeRunner runner;

    /// The sight shape, or null for a brain that sees nothing.
    private final @Nullable ColliderComponent<?> sight;

    /// Where the last test placed [#sight], in world space.
    private Transform sightTransform = Transform.IDENTITY;

    /// The entities in sight at the last test.
    private final Set<Entity> seen = new HashSet<>();

    /// The game time each pushing entity was last felt at. Weak, so a removed entity drops out.
    private final Map<Entity, Long> lastTouches = new WeakHashMap<>();

    /// @throws IllegalStateException when a data pack node of the tree names a missing entry
    public BehaviorTreeInstance(final BrainComponent brain, final Holder.Reference<BehaviorTreeBlueprint> blueprint) {
        this.brain = brain;
        this.blueprint = blueprint;
        this.blackboard = new ScriptVariables(blueprint.value().body().variables());
        this.executor = new BlueprintExecutor(
            this, ScriptModularizeHelper.scanBlueprintLayout(BehaviorTreeScript.class), blueprint.value().body(), blackboard);
        this.runner = new BehaviorTreeRunner(brain, blueprint.value().roots().stream().map(root -> root.instantiate(brain)).toList());
        this.sight = blueprint.value().perception().sight().map(sense -> sense.shape().instantiate(null)).orElse(null);
    }

    public BehaviorTreeRunner getRunner() {
        return runner;
    }

    public ScriptVariables getBlackboard() {
        return blackboard;
    }

    /// The sight shape, or null for a brain that sees nothing.
    public @Nullable ColliderComponent<?> getSight() {
        return sight;
    }

    /// Where the last test placed the sight shape, in world space.
    public Transform getSightTransform() {
        return sightTransform;
    }

    /// Places the sight shape at the owner's eyes and runs [BehaviorTreeScript#onSight] for each entity that entered
    /// or left it since the last test.
    public void tickSight() {
        if (sight == null || !(brain.getOwner().getOriginal() instanceof LivingEntity owner)) return;

        Quaterniond rotation = new Quaterniond()
            .rotationY(-owner.getYHeadRot() * Mth.DEG_TO_RAD)
            .rotateAxis(owner.getXRot() * Mth.DEG_TO_RAD, 1.0D, 0.0D, 0.0D);

        sightTransform = Transform.of(owner.getEyePosition(), rotation, new Vec3(1.0D, 1.0D, 1.0D));
        sight.placedAt(sightTransform);

        Set<Entity> visible = new HashSet<>();

        for (LivingEntity candidate : owner.level().getEntitiesOfClass(LivingEntity.class, sight.getOuterAABB(), entity -> entity != owner && entity.isAlive())) {
            double radius = Math.min(candidate.getBbWidth(), candidate.getBbHeight()) * 0.5D;
            ColliderComponent<?> body = new SphereCollider(Transform.IDENTITY, TransformInheritPolicy.LOC_ROT_SCA, radius, true)
                .instantiate(null)
                .placedAt(Transform.fromTranslation(candidate.getBoundingBox().getCenter()));

            if (!CollisionDetectionLogics.overlap(sight, body)) continue;
            if (blueprint.value().perception().sight().orElseThrow().lineOfSight() && !owner.hasLineOfSight(candidate)) continue;

            visible.add(candidate);
        }

        for (Entity entity : visible) {
            if (seen.add(entity)) executor.invokeMethod("onSight", null, brain, entity, true);
        }

        for (Entity entity : Set.copyOf(seen)) {
            if (visible.contains(entity)) continue;

            seen.remove(entity);
            executor.invokeMethod("onSight", null, brain, entity, false);
        }
    }

    /// Runs [BehaviorTreeScript#onHeard] when the asset hears and `volume` reaches the hearing sphere loud enough.
    public void hear(final Vec3 location, final SoundEvent soundEvent, final float volume) {
        PerceptionSettings.Hearing hearing = blueprint.value().perception().hearing().orElse(null);

        if (hearing == null) return;

        double distance = Math.max(0.0D, brain.getOwner().getOriginal().position().distanceTo(location) - hearing.radius());
        float heard = volume * (1.0F - (float)distance / soundEvent.getRange(volume));

        if (heard >= hearing.minVolume()) executor.invokeMethod("onHeard", null, brain, location, soundEvent);
    }

    /// Runs [BehaviorTreeScript#onVibration] when the asset feels game events.
    public void feelVibration(final Vec3 location, final GameEvent gameEvent, final @Nullable Entity source) {
        if (blueprint.value().perception().vibration().isPresent()) executor.invokeMethod("onVibration", null, brain, location, gameEvent, source);
    }

    /// Runs [BehaviorTreeScript#onTouch] for `collided` pushing the owner, at most once per
    /// [PerceptionSettings.Touch#pushCooldown].
    public void feelPush(final Entity collided) {
        PerceptionSettings.Touch touch = blueprint.value().perception().touch().orElse(null);

        if (touch == null) return;

        long gameTime = brain.getOwner().getLevel().getGameTime();
        Long last = lastTouches.get(collided);

        if (last != null && gameTime - last < touch.pushCooldown()) return;

        lastTouches.put(collided, gameTime);
        executor.invokeMethod("onTouch", null, brain, collided, null);
    }

    /// Runs [BehaviorTreeScript#onTouch] for damage the owner took.
    public void feelDamage(final DamageSource damageSource) {
        if (blueprint.value().perception().touch().isPresent()) executor.invokeMethod("onTouch", null, brain, null, damageSource);
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public BrainComponent getScriptOwner() {
        return brain;
    }

    @Override
    public Holder.Reference<BehaviorTreeBlueprint> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return blackboard;
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }
}
