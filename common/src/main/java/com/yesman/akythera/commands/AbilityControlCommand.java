package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCues;
import com.yesman.akythera.api.world.ability.AbilityDebugScaffolding;
import com.yesman.akythera.api.world.ability.AbilityEffectInstance;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/// Drives the ability lifecycle from the console while there is no input system.
///
/// Must be used only for the development stage and removed on releases.
public class AbilityControlCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("ability").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("trace")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context ->
                                    forEachComponent(
                                        EntityArgument.getEntities(context, "targets"),
                                        component -> {
                                            component.setTracing(BoolArgumentType.getBool(context, "enabled"));

                                            return true;
                                        }
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> {
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.PRIMARY,
                                            new AbilityDebugScaffolding.TestAbility()
                                        );

                                        return true;
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("givesystematic")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> {
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.PRIMARY,
                                            new AbilityDebugScaffolding.TestSystematicAbility()
                                        );

                                        return true;
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("giveinstant")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> {
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.PRIMARY,
                                            new AbilityDebugScaffolding.TestInstantAbility()
                                        );

                                        return true;
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("givetagged")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("scenario", StringArgumentType.word())
                                .then(Commands.argument("slot", StringArgumentType.word())
                                    .executes(context -> {
                                        AbilityDebugScaffolding.TaggedScenario scenario =
                                            ParseUtils.enumValueOfOrNull(
                                                AbilityDebugScaffolding.TaggedScenario.class,
                                                StringArgumentType.getString(context, "scenario")
                                            );
                                        AbilityDebugScaffolding.DebugSlot slot =
                                            ParseUtils.enumValueOfOrNull(
                                                AbilityDebugScaffolding.DebugSlot.class,
                                                StringArgumentType.getString(context, "slot")
                                            );

                                        if (scenario == null || slot == null) return 0;

                                        return forEachComponent(
                                            EntityArgument.getEntities(context, "targets"),
                                            component -> {
                                                component.giveAbility(slot, scenario.build());

                                                return true;
                                            }
                                        );
                                    })
                                )
                            )
                        )
                    )
                    .then(Commands.literal("activate")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("slot", StringArgumentType.word())
                                .executes(context -> {
                                    AbilityDebugScaffolding.DebugSlot slot =
                                        ParseUtils.enumValueOfOrNull(
                                            AbilityDebugScaffolding.DebugSlot.class,
                                            StringArgumentType.getString(context, "slot")
                                        );

                                    if (slot == null) return 0;

                                    return forEachComponent(
                                        EntityArgument.getEntities(context, "targets"),
                                        component -> {
                                            boolean accepted = component.activateAbility(slot);

                                            Akythera.LOGGER.info("activate {} -> {}", slot, accepted);

                                            return true;
                                        }
                                    );
                                })
                            )
                        )
                    )
                    .then(Commands.literal("tag")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("action", StringArgumentType.word())
                                .then(Commands.argument("tag", StringArgumentType.word())
                                    .executes(context -> {
                                        AbilityDebugScaffolding.DebugTag tag =
                                            ParseUtils.enumValueOfOrNull(
                                                AbilityDebugScaffolding.DebugTag.class,
                                                StringArgumentType.getString(context, "tag")
                                            );

                                        if (tag == null) return 0;
                                        boolean add = StringArgumentType.getString(context, "action").equals("add");

                                        return forEachComponent(
                                            EntityArgument.getEntities(context, "targets"),
                                            component -> {
                                                if (add) {
                                                    component.addAbilityTag(tag);
                                                } else {
                                                    component.removeAbilityTag(tag);
                                                }

                                                return true;
                                            }
                                        );
                                    })
                                )
                            )
                        )
                    )
                    .then(Commands.literal("event")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(context -> {
                                    AbilityDebugScaffolding.DebugTag tag =
                                        ParseUtils.enumValueOfOrNull(
                                            AbilityDebugScaffolding.DebugTag.class,
                                            StringArgumentType.getString(context, "tag")
                                        );

                                    if (tag == null) return 0;

                                    return forEachComponent(
                                        EntityArgument.getEntities(context, "targets"),
                                        component -> {
                                            component.triggerEvent(tag);

                                            return true;
                                        }
                                    );
                                })
                            )
                        )
                    )
                    .then(Commands.literal("tags")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(EntityArgument.getEntities(context, "targets"), component -> {
                                    Akythera.LOGGER.info("owner tags {}", component.getOwnerTags());

                                    return true;
                                })
                            )
                        )
                    )
                    .then(Commands.literal("cue")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("which", StringArgumentType.word())
                                .executes(context -> {
                                    String which = StringArgumentType.getString(context, "which");

                                    return forEachComponent(
                                        EntityArgument.getEntities(context, "targets"),
                                        component -> {
                                            AbilityCue cue = switch (which) {
                                                case "timed" -> AbilityDebugScaffolding.DebugCues.TIMED;
                                                case "endless" -> AbilityDebugScaffolding.DebugCues.ENDLESS;
                                                default -> AbilityCues.get(Akythera.identifier(which));
                                            };

                                            if (cue == null) return false;

                                            component.playCue(
                                                cue,
                                                component.getInstance(AbilityDebugScaffolding.DebugSlot.PRIMARY),
                                                null
                                            );

                                            return true;
                                        }
                                    );
                                })
                            )
                        )
                    )
                    .then(Commands.literal("cancelcue")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(context -> {
                                    AbilityDebugScaffolding.DebugTag tag =
                                        ParseUtils.enumValueOfOrNull(
                                            AbilityDebugScaffolding.DebugTag.class,
                                            StringArgumentType.getString(context, "tag")
                                        );

                                    if (tag == null) return 0;

                                    return forEachComponent(
                                        EntityArgument.getEntities(context, "targets"),
                                        component -> {
                                            Akythera.LOGGER.info("cancelCue {} -> {}", tag, component.cancelCue(tag));

                                            return true;
                                        }
                                    );
                                })
                            )
                        )
                    )
                    .then(Commands.literal("givecost")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> {
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.PRIMARY,
                                            new AbilityDebugScaffolding.TestCostAbility()
                                        );

                                        return true;
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("givedoublecost")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> {
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.PRIMARY,
                                            new AbilityDebugScaffolding.TestDoubleCostAbility()
                                        );

                                        return true;
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("giveshared")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> {
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.PRIMARY,
                                            new AbilityDebugScaffolding.TestSystematicAbility()
                                        );
                                        component.giveAbility(
                                            AbilityDebugScaffolding.DebugSlot.SECONDARY,
                                            new AbilityDebugScaffolding.TestSharedCooldownAbility()
                                        );

                                        return true;
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("health")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(EntityArgument.getEntities(context, "targets"), component -> {
                                    Akythera.LOGGER.info("health {}", component.getOwner().getOriginal().getHealth());

                                    return true;
                                })
                            )
                        )
                    )
                    .then(Commands.literal("giveblueprint")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> giveBlueprint(context, AbilityDebugScaffolding.DebugSlot.PRIMARY))
                                .then(Commands.argument("slot", StringArgumentType.word())
                                    .executes(context -> {
                                        AbilityDebugScaffolding.DebugSlot slot = ParseUtils.enumValueOfOrNull(
                                            AbilityDebugScaffolding.DebugSlot.class,
                                            StringArgumentType.getString(context, "slot")
                                        );

                                        return slot == null ? 0 : giveBlueprint(context, slot);
                                    })
                                )
                            )
                        )
                    )
                    .then(Commands.literal("registry")
                        .executes(context -> {
                            Registry<AbilityEffect> registry =
                                context.getSource().registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY);

                            Akythera.LOGGER.info("ability_effect registry size {}", registry.size());

                            registry.listElements().forEach(holder ->
                                Akythera.LOGGER.info(
                                    "ability_effect {} -> id {} type {} duration {} maxStack {} modifiers {}",
                                    holder.key().identifier(),
                                    holder.value().getId(),
                                    holder.value().getApplicationType(),
                                    holder.value().getDuration(),
                                    holder.value().getMaxStack(),
                                    holder.value().getModifiers().size()
                                )
                            );

                            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.listElements()
                                .filter(holder -> holder.key().identifier().getPath().startsWith("ability"))
                                .forEach(holder ->
                                    Akythera.LOGGER.info(
                                        "script_function {} usage {} pure {}",
                                        holder.key().identifier(),
                                        holder.value().metadata().usage(),
                                        holder.value().metadata().pure()
                                    )
                                );

                            return registry.size();
                        })
                    )
                    .then(Commands.literal("effects")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(EntityArgument.getEntities(context, "targets"), AbilityControlCommand::listEffects)
                            )
                        )
                    )
                    .then(Commands.literal("trigger")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> component.activateAbility(AbilityDebugScaffolding.DebugSlot.PRIMARY)
                                )
                            )
                        )
                    )
                    .then(Commands.literal("cancel")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> component.cancelAbility(AbilityDebugScaffolding.DebugSlot.PRIMARY)
                                )
                            )
                        )
                    )
                    .then(Commands.literal("finish")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(
                                    EntityArgument.getEntities(context, "targets"),
                                    component -> component.endAbility(AbilityDebugScaffolding.DebugSlot.PRIMARY)
                                )
                            )
                        )
                    )
                    .then(Commands.literal("task")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(context ->
                                    forEachComponent(
                                        EntityArgument.getEntities(context, "targets"),
                                        component -> {
                                            AbilityInstance instance = component.getInstance(AbilityDebugScaffolding.DebugSlot.PRIMARY);

                                            if (instance == null) return false;

                                            component.activateTask(
                                                instance,
                                                new AbilityDebugScaffolding.WaitTicksTask(
                                                    IntegerArgumentType.getInteger(context, "ticks")
                                                )
                                            );

                                            return true;
                                        }
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("list")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(context ->
                                forEachComponent(EntityArgument.getEntities(context, "targets"), AbilityControlCommand::listAbilities)
                            )
                        )
                    )
                )
        );
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// Runs `action` against every target that carries an [AbilitySystemComponent].
    ///
    /// @return how many targets the action reported success for
    /// Builds the named blueprint and equips it into `slot`.
    private static int giveBlueprint(
        final CommandContext<CommandSourceStack> context,
        final AbilityDebugScaffolding.DebugSlot slot
    ) throws CommandSyntaxException {
        Registry<AbilityBlueprint> registry = context.getSource().registryAccess()
            .lookupOrThrow(AkytheraRegistries.DataPack.ABILITY_KEY);
        ResourceKey<AbilityBlueprint> key = ResourceKey.create(
            AkytheraRegistries.DataPack.ABILITY_KEY,
            Akythera.identifier(StringArgumentType.getString(context, "id"))
        );
        Holder.Reference<AbilityBlueprint> blueprint = registry.get(key).orElse(null);

        if (blueprint == null) {
            Akythera.LOGGER.info("no ability blueprint {}", key.identifier());

            return 0;
        }

        return forEachComponent(
            EntityArgument.getEntities(context, "targets"),
            component -> {
                Ability<?> ability = AbilityBlueprint.createAbility(blueprint);

                if (ability == null) return false;

                Akythera.LOGGER.info(
                    "built {} from {} into {} (identity {})",
                    ability.getClass().getSimpleName(),
                    key.identifier(),
                    slot,
                    System.identityHashCode(ability)
                );
                component.giveAbility(slot, ability);

                return true;
            }
        );
    }

    private static int forEachComponent(
        final Collection<? extends Entity> targets,
        final Function<AbilitySystemComponent, Boolean> action
    ) {
        int count = 0;

        for (Entity entity : targets) {
            @Nullable EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);

            if (entityPatch == null) continue;

            @Nullable AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) continue;

            if (action.apply(component)) count++;
        }

        return count;
    }

    /// Reports every live effect application as `role id stacks/max remaining`.
    private static boolean listEffects(final AbilitySystemComponent component) {
        for (AbilityEffectInstance instance : component.getAllEffectInstances()) {
            Akythera.LOGGER.info(
                "effect {} {} stacks {}/{} remaining {} ticks ({}s)",
                instance.getRole(),
                instance.getModifierId(),
                instance.getStacks(),
                instance.getEffect().getMaxStack(),
                instance.getRemainingTicks(),
                String.format("%.2f", instance.getRemainingTicks() / 20.0F)
            );
        }

        return true;
    }

    private static boolean listAbilities(final AbilitySystemComponent component) {
        for (Map.Entry<AbilitySlot, AbilityInstance> entry : component.getInstances().entrySet()) {
            AbilityInstance instance = entry.getValue();

            Akythera.LOGGER.info(
                "slot {} holds {} (activated: {})",
                entry.getKey(),
                instance.getAbility() == null ? "<none>" : instance.getAbility().getClass().getSimpleName(),
                instance.isActivated()
            );
        }

        return true;
    }
}
