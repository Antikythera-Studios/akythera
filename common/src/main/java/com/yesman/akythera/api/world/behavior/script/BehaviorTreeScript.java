package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// The stimulation events a behavior tree asset's event graph implements, independent of the trees inside it.
public interface BehaviorTreeScript {
    /// Called when the owner hears a sound loud enough.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onHeard(final BrainComponent brain, final Vec3 location, final SoundEvent soundEvent);

    /// Called when a game event happens within its notification radius of the owner.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onVibration(final BrainComponent brain, final Vec3 location, final GameEvent gameEvent, final @Nullable Entity source);

    /// Called once when an entity enters the sight of the owner, with `sensed` true, and once when it leaves, with
    /// `sensed` false.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onSight(final BrainComponent brain, final Entity seen, final boolean sensed);

    /// Called when an entity pushes the owner, with a null `damageSource`, or when the owner takes damage, with a null
    /// `collided`.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onTouch(final BrainComponent brain, final @Nullable Entity collided, final @Nullable DamageSource damageSource);
}
