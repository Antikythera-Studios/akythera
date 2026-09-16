package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.script.DataDrivenAbilityState;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The datapack entry for one ability: the parent constructor, the [SystematicAbility] settings the
/// editor fills in, one graph per event, and the variable list.
public record AbilityBlueprint(
    ScriptFunction ctor,
    SystematicAbility.SettingsSpec settings,
    Optional<PureNode> canActivate,
    Optional<FlowNode> canActivateFunction,
    FlowNode onEquipped,
    FlowNode onUnequipped,
    FlowNode onActivated,
    FlowNode onRecast,
    FlowNode onCanceled,
    FlowNode onFinished,
    List<ScriptVariables.VariableInfo> variables
) implements Blueprint<DataDrivenAbilityState, Ability<?>>
{
    public AbilityBlueprint {
        for (ScriptVariables.VariableInfo variable : variables) {
            if (variable.name().equals(DataDrivenAbilityState.OWNER_LOCAL)
                || variable.name().equals(DataDrivenAbilityState.INSTANCE_LOCAL)) {
                throw new IllegalArgumentException(
                    "An ability variable may not be named " + variable.name()
                        + ", which every graph run binds."
                );
            }
        }
    }

    public static final MapCodec<AbilityBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent")
                    .forGetter(AbilityBlueprint::ctor),
                SystematicAbility.SettingsSpec.MAP_CODEC.fieldOf("settings")
                    .forGetter(AbilityBlueprint::settings),
                PureNode.CODEC.optionalFieldOf("can_activate").forGetter(AbilityBlueprint::canActivate),
                FlowNode.CODEC.optionalFieldOf("can_activate_function")
                    .forGetter(AbilityBlueprint::canActivateFunction),
                graph("on_equipped").forGetter(AbilityBlueprint::onEquipped),
                graph("on_unequipped").forGetter(AbilityBlueprint::onUnequipped),
                graph("on_activated").forGetter(AbilityBlueprint::onActivated),
                graph("on_recast").forGetter(AbilityBlueprint::onRecast),
                graph("on_canceled").forGetter(AbilityBlueprint::onCanceled),
                graph("on_finished").forGetter(AbilityBlueprint::onFinished),
                ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                    .forGetter(AbilityBlueprint::variables)
            ).apply(instance, AbilityBlueprint::new)
    );

    public static final Codec<AbilityBlueprint> CODEC = MAP_CODEC.codec();

    /// An event graph field. Leaving it out of the file runs nothing for that event.
    private static MapCodec<FlowNode> graph(final String name) {
        return FlowNode.CODEC.optionalFieldOf(name, new SequenceNode(List.of()));
    }

    /// One [Ability] per entry, so a slot cannot hold two of the same and
    /// [com.yesman.akythera.api.world.ability.AbilitySystemComponent#giveAbility]'s identity check
    /// still means something. Dropped when the registry unloads.
    private static final Map<ResourceKey<AbilityBlueprint>, Ability<?>> ABILITY_CACHE = new ConcurrentHashMap<>();

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(event -> ABILITY_CACHE.clear());
    }

    /// Builds the ability this entry describes by invoking its parent constructor.
    ///
    /// @return null when the constructor is not a one-argument function, or refuses the blueprint
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Ability<?> createAbility(final Holder.Reference<AbilityBlueprint> blueprint) {
        Ability<?> cached = ABILITY_CACHE.get(blueprint.key());

        if (cached != null) return cached;

        try {
            ScriptFunction.ScriptUniFunction uniFunction =
                (ScriptFunction.ScriptUniFunction)blueprint.value().ctor();

            Ability<?> ability = (Ability<?>)uniFunction.function().apply(blueprint);

            // Stamped here rather than in a constructor, so a third-party parent that never
            // forwards the holder still ends up nameable on the wire.
            ability.setBlueprintKey(blueprint.key());
            ABILITY_CACHE.put(blueprint.key(), ability);

            return ability;
        } catch (ClassCastException | IllegalStateException | IllegalArgumentException exception) {
            Akythera.LOGGER.error(
                "Ability {} cannot be built from its parent constructor",
                blueprint.key().identifier(), exception
            );

            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DataDrivenAbilityState instantiate(final Ability<?> blueprintOwner) {
        Registry<AbilityBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ABILITY_KEY);

        return new DataDrivenAbilityState(
            (Holder.Reference<AbilityBlueprint>)registry.wrapAsHolder(this), blueprintOwner
        );
    }

    @Override
    public MapCodec<AbilityBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
