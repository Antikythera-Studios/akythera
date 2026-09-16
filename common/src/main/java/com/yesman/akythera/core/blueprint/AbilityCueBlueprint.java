package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.core.blueprint.script.DataDrivenCueBehavior;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The data pack entry for one [AbilityCue]: its tags, its lifetime, and the `on_execute` graph that
/// is its behavior.
///
/// The editor gives a cue one entry point, so this carries one graph rather than the four a Java
/// [AbilityCue.Behavior] may override.
public record AbilityCueBlueprint(
    Set<AbilityTag> tags,
    boolean endsWithAbility,
    int duration,
    FlowNode onExecute,
    List<ScriptVariables.VariableInfo> variables
) {
    public static final MapCodec<AbilityCueBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AbilityTag.ENUM_MANAGER.getNameCodec().listOf()
                    .optionalFieldOf("tags", List.of())
                    .xmap(Set::copyOf, List::copyOf)
                    .forGetter(AbilityCueBlueprint::tags),
                Codec.BOOL.optionalFieldOf("ends_with_ability", true)
                    .forGetter(AbilityCueBlueprint::endsWithAbility),
                Codec.INT.optionalFieldOf("duration", 0).forGetter(AbilityCueBlueprint::duration),
                FlowNode.CODEC.optionalFieldOf("on_execute", new SequenceNode(List.of()))
                    .forGetter(AbilityCueBlueprint::onExecute),
                ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                    .forGetter(AbilityCueBlueprint::variables)
            ).apply(instance, AbilityCueBlueprint::new)
    );

    public static final Codec<AbilityCueBlueprint> CODEC = MAP_CODEC.codec();

    /// One [AbilityCue] per entry, so two plays of one entry are the same cue. Dropped when the
    /// registry unloads.
    private static final Map<ResourceKey<AbilityCueBlueprint>, AbilityCue> CUE_CACHE = new ConcurrentHashMap<>();

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(event -> CUE_CACHE.clear());
    }

    /// The cue this entry describes, named by its own registry key.
    public static AbilityCue createCue(final Holder.Reference<AbilityCueBlueprint> blueprint) {
        AbilityCue cached = CUE_CACHE.get(blueprint.key());

        if (cached != null) return cached;

        AbilityCueBlueprint value = blueprint.value();
        AbilityCue cue = new AbilityCue(
            blueprint.key().identifier(),
            value.tags(),
            value.endsWithAbility(),
            value.duration(),
            new DataDrivenCueBehavior(blueprint)
        );

        CUE_CACHE.put(blueprint.key(), cue);

        return cue;
    }
}
