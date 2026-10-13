package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

/// An [Ability] whose common behaviors are settings rather than code: the activation preset, the
/// cost, the cooldown and the duration effect. A developer extends this so other developers can
/// parent entries on that subclass and fill the settings in the editor.
///
/// Every setting is read through its getter, so a subclass may answer one by overriding it.
public class SystematicAbility<S extends AbilityState> extends Ability<S> {
    private final ActivationType activationType;

    /// Spent on activation, and checked before it.
    private final List<AbilityEffect> costs;

    /// The recharge timer banking this ability's charges.
    private final @Nullable AbilityEffect cooldownEffect;

    /// Present only when [#activationType] is [ActivationType#DURATION], whose expiry is what
    /// finishes the ability.
    private final @Nullable AbilityEffect durationEffect;

    /// The key this ability reads, required by the presets that stay activated by input.
    private final @Nullable Holder<InputAction> inputAction;

    private final Tags tags;

    private final List<Trigger> triggers;

    private final InstantiationScope instantiationScope;

    public SystematicAbility(
        final ActivationType activationType,
        final InstantiationScope instantiationScope,
        final List<AbilityEffect> costs,
        final @Nullable AbilityEffect cooldownEffect,
        final @Nullable AbilityEffect durationEffect
    ) {
        this(activationType, instantiationScope, costs, cooldownEffect, durationEffect, null, Tags.NONE, List.of());
    }

    public SystematicAbility(
        final ActivationType activationType,
        final InstantiationScope instantiationScope,
        final List<AbilityEffect> costs,
        final @Nullable AbilityEffect cooldownEffect,
        final @Nullable AbilityEffect durationEffect,
        final @Nullable Holder<InputAction> inputAction,
        final Tags tags,
        final List<Trigger> triggers
    ) {
        if (activationType == ActivationType.DURATION && durationEffect == null) {
            throw new IllegalArgumentException("A Duration ability needs a Duration Effect.");
        }

        if (activationType.requiresInputAction() && inputAction == null) {
            throw new IllegalArgumentException(
                "A " + activationType + " ability stays activated by its key, and names no Input Action."
            );
        }

        // The ability's lifetime is the effect's.
        if (durationEffect != null
            && durationEffect.getApplicationType() != AbilityEffect.ApplicationType.DURATION) {
            throw new IllegalArgumentException(
                "A Duration Effect must itself be Duration, and " + durationEffect.getId()
                    + " is " + durationEffect.getApplicationType() + "."
            );
        }

        // A recharge timer counts down, so it needs a duration to count.
        if (cooldownEffect != null
            && cooldownEffect.getApplicationType() != AbilityEffect.ApplicationType.DURATION) {
            throw new IllegalArgumentException(
                "A Cooldown Effect must be Duration, and " + cooldownEffect.getId()
                    + " is " + cooldownEffect.getApplicationType() + "."
            );
        }

        if (activationType != ActivationType.DURATION && durationEffect != null) {
            throw new IllegalArgumentException(
                "A Duration Effect belongs to a Duration ability, not " + activationType + "."
            );
        }

        for (AbilityEffect cost : costs) {
            if (!activationType.allowsCostType(cost.getApplicationType())) {
                throw new IllegalArgumentException(
                    MessageFormat.format(
                        "A {0} ability cannot carry a {1} cost: {2}",
                        activationType, cost.getApplicationType(), cost.getId()
                    )
                );
            }

            for (AbilityEffect.Modifier<?> modifier : cost.getModifiers()) {
                AbilityEffect.AbilityResourceSpec resourceSpec = modifier.asAbilitySource();

                if (resourceSpec != null && resourceSpec.amount() <= 0.0F) {
                    throw new IllegalArgumentException(
                        "A cost amount is authored positive and subtracted by the engine. "
                            + cost.getId() + " carries " + resourceSpec.amount()
                    );
                }
            }
        }

        this.activationType = activationType;
        this.instantiationScope = instantiationScope;
        this.costs = List.copyOf(costs);
        this.cooldownEffect = cooldownEffect;
        this.durationEffect = durationEffect;
        this.inputAction = inputAction;
        this.tags = tags;
        this.triggers = List.copyOf(triggers);
    }

    /// Builds from a datapack entry, reading its property values.
    public SystematicAbility(final Holder.Reference<AbilityBlueprint> blueprint) {
        this(blueprint.value().body());
    }

    private SystematicAbility(final BlueprintBody body) {
        this(
            body.property(SystematicAbility.class, "activationType", ActivationType.INSTANT),
            body.property(SystematicAbility.class, "instantiationScope", InstantiationScope.EVERYWHERE),
            body.property(SystematicAbility.class, "costs", List.of()),
            body.property(SystematicAbility.class, "cooldownEffect", null),
            body.property(SystematicAbility.class, "durationEffect", null),
            body.property(SystematicAbility.class, "inputAction", null),
            body.property(SystematicAbility.class, "tags", Tags.NONE),
            body.property(SystematicAbility.class, "triggers", List.of())
        );
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public ActivationType getActivationType() {
        return activationType;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    @Override
    public InstantiationScope getInstantiationScope() {
        return instantiationScope;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public @Nullable AbilityEffect getCooldownEffect() {
        return cooldownEffect;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public @Nullable AbilityEffect getDurationEffect() {
        return durationEffect;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public @Nullable Holder<InputAction> getInputAction() {
        return inputAction;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public List<AbilityEffect> getCosts() {
        return costs;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public Tags getTags() {
        return tags;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public List<Trigger> getTriggers() {
        return triggers;
    }

    /// Starts the recharge timer.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    @Override
    public void onEquipped(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        super.onEquipped(abilitySystemComponent, abilityInstance, abilityState);

        // A client's timer arrives from the server, numbered as the server numbered it.
        if (getCooldownEffect() != null && abilitySystemComponent.getOwner().isLogicalServer()) {
            abilitySystemComponent.getOrCreateCooldown(getCooldownEffect(), abilityInstance);
        }
    }

    /// Refuses the activation when no charge is banked, or when the owner cannot pay every cost.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    @Override
    public boolean canActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        if (!super.canActivate(abilitySystemComponent, abilityInstance, abilityState)) return false;

        if (!abilitySystemComponent.hasAllAbilityTags(getTags().activationRequiredTags())) return false;
        if (abilitySystemComponent.hasAnyAbilityTags(getTags().activationBlockedTags())) return false;

        // Server side only: this reads instances, which a client holds only partially.
        if (abilitySystemComponent.getOwner().isLogicalServer()
            && isBlockedByActivatedAbility(abilitySystemComponent, abilityInstance)) {
            return false;
        }

        if (getCooldownEffect() != null) {
            AbilityEffectInstance cooldown =
                abilitySystemComponent.getCooldown(getCooldownEffect(), abilityInstance);

            // Nothing has started the timer, so no charge can have been banked.
            if (cooldown == null || !cooldown.hasStack()) return false;
        }

        LivingEntityPatch<?> owner = abilitySystemComponent.getOwner();

        // The first tick's Infinite cost is checked too, or the ability activates, spends a charge
        // and cancels itself on the same tick.
        return canPayCosts(owner, AbilityEffect.ApplicationType.INSTANT)
            && canPayCosts(owner, AbilityEffect.ApplicationType.INFINITE);
    }

    /// Whether an activated ability refuses this one, by matching its own Block Abilities with Tag
    /// against this ability's IdTags.
    private boolean isBlockedByActivatedAbility(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance self
    ) {
        for (AbilityInstance held : abilitySystemComponent.getInstances().values()) {
            if (held == self || !held.isActivated()) continue;

            if (held.getAbility() instanceof SystematicAbility<?> other
                && getTags().idTags().hasAny(other.getTags().blockAbilitiesWithTag())) {
                return true;
            }
        }

        return false;
    }

    /// Whether `owner` holds every cost of `applicationType`, summed per resource.
    ///
    /// Summed rather than checked one at a time: two costs of five on an owner holding seven each
    /// pass alone, and the second write would then be clamped rather than refused.
    ///
    /// A cost amount is authored positive and the engine subtracts it.
    public boolean canPayCosts(
        final LivingEntityPatch<?> owner,
        final AbilityEffect.ApplicationType applicationType
    ) {
        for (Map.Entry<AbilityResource, Float> required : totalCosts(applicationType).entrySet()) {
            if (!required.getKey().hasResource(owner, required.getValue())) return false;
        }

        return true;
    }

    /// Subtracts every cost of `applicationType` from `owner`, or nothing at all when the owner
    /// cannot pay the whole set.
    ///
    /// @return whether the set was paid
    public boolean chargeCosts(
        final LivingEntityPatch<?> owner,
        final AbilityEffect.ApplicationType applicationType
    ) {
        Map<AbilityResource, Float> required = totalCosts(applicationType);

        for (Map.Entry<AbilityResource, Float> entry : required.entrySet()) {
            if (!entry.getKey().hasResource(owner, entry.getValue())) return false;
        }

        required.forEach((resource, amount) -> resource.consume(owner, amount));

        return true;
    }

    /// What one activation of `applicationType` costs, totalled per [AbilityResource].
    private Map<AbilityResource, Float> totalCosts(final AbilityEffect.ApplicationType applicationType) {
        Map<AbilityResource, Float> totals = new LinkedHashMap<>();

        for (AbilityEffect cost : getCosts()) {
            if (cost.getApplicationType() != applicationType) continue;

            for (AbilityEffect.Modifier<?> modifier : cost.getModifiers()) {
                AbilityEffect.AbilityResourceSpec resourceSpec = modifier.asAbilitySource();

                if (resourceSpec != null) {
                    totals.merge(resourceSpec.abilityResource(), resourceSpec.amount(), Float::sum);
                }
            }
        }

        return totals;
    }

    /// Spends the charge and the costs, then applies the duration effect.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    @Override
    public void onActivated(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        // Everything here writes authoritative state: resources, banked charges, other abilities'
        // lifecycles, the owner's tags and live attribute modifiers. A client mirroring this
        // activation receives all of it from the server instead.
        if (!abilitySystemComponent.getOwner().isLogicalServer()) return;

        // Charged before anything else runs. Cancelling a clashing ability rolls its effects back,
        // and granting a tag can fire a trigger, either of which moves the resource this reads.
        chargeCosts(abilitySystemComponent.getOwner(), AbilityEffect.ApplicationType.INSTANT);

        if (getCooldownEffect() != null) {
            AbilityEffectInstance cooldown =
                abilitySystemComponent.getOrCreateCooldown(getCooldownEffect(), abilityInstance);

            // Sent ahead of the clash cancelling and the tag grants below, which both run callbacks.
            if (cooldown.consumeStack()) abilitySystemComponent.syncEffect(cooldown);
        }

        cancelClashingAbilities(abilitySystemComponent, abilityInstance);

        for (AbilityTag acquired : getTags().ownerAcquiredTags()) {
            abilitySystemComponent.addAbilityTag(acquired);
        }

        if (getDurationEffect() != null) {
            abilitySystemComponent.applyEffect(
                getDurationEffect(), AbilityEffectInstance.Role.DURATION, abilityInstance
            );
        }
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    @Override
    public void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    @Override
    public void onCanceled(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        // Rolled back before the tags are released. Releasing one fires its triggers, which can
        // re-activate this ability and apply a second duration effect that the rollback would then
        // cancel along with the first, leaving the ability activated with nothing left to end it.
        if (!abilitySystemComponent.getOwner().isLogicalServer()) return;

        rollBackDurationEffect(abilitySystemComponent, abilityInstance);
        releaseOwnerAcquiredTags(abilitySystemComponent);
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    @Override
    public void onFinished(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        // Rolled back before the tags are released. Releasing one fires its triggers, which can
        // re-activate this ability and apply a second duration effect that the rollback would then
        // cancel along with the first, leaving the ability activated with nothing left to end it.
        if (!abilitySystemComponent.getOwner().isLogicalServer()) return;

        rollBackDurationEffect(abilitySystemComponent, abilityInstance);
        releaseOwnerAcquiredTags(abilitySystemComponent);
    }

    /// Cancels every activated ability this one's Cancel Abilities with Tag names.
    private void cancelClashingAbilities(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance self
    ) {
        AbilityTagSet patterns = getTags().cancelAbilitiesWithTag();

        if (patterns.isEmpty()) return;

        for (Map.Entry<AbilitySlot, AbilityInstance> entry
            : List.copyOf(abilitySystemComponent.getInstances().entrySet())) {
            AbilityInstance held = entry.getValue();

            if (held == self || !held.isActivated()) continue;

            if (held.getAbility() instanceof SystematicAbility<?> other
                && other.getTags().idTags().hasAny(patterns)) {
                abilitySystemComponent.cancelAbility(entry.getKey());
            }
        }
    }

    /// Releases the tags this ability granted its owner while it ran.
    private void releaseOwnerAcquiredTags(final AbilitySystemComponent abilitySystemComponent) {
        for (AbilityTag acquired : getTags().ownerAcquiredTags()) {
            abilitySystemComponent.removeAbilityTag(acquired);
        }
    }

    /// Ends this ability's own duration effect, leaving alone any other ability's application of
    /// the same effect.
    private void rollBackDurationEffect(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance
    ) {
        if (getDurationEffect() == null) return;

        for (AbilityEffectInstance live
            : List.copyOf(abilitySystemComponent.getEffectInstances(getDurationEffect().getId()))) {
            if (live.getSource() == abilityInstance) abilitySystemComponent.cancelEffect(live);
        }
    }

    /// The six tag fields the editor shows.
    ///
    /// Only [#ownerAcquiredTags] reaches the owner's counted container.
    ///
    /// @param idTags                 this ability's own tag set, which both "Abilities with Tag"
    ///                               fields match against
    /// @param cancelAbilitiesWithTag activating cancels every activated ability whose [#idTags]
    ///                               these match
    /// @param blockAbilitiesWithTag  while activated, refuses any ability whose [#idTags] these
    ///                               match. Server side only
    /// @param ownerAcquiredTags      granted to the owner while activated
    /// @param activationRequiredTags the owner must carry all of these
    /// @param activationBlockedTags  the owner must carry none of these
    @ScriptType(autoRegister = false)
    public record Tags(
        AbilityTagSet idTags,
        AbilityTagSet cancelAbilitiesWithTag,
        AbilityTagSet blockAbilitiesWithTag,
        AbilityTagSet ownerAcquiredTags,
        AbilityTagSet activationRequiredTags,
        AbilityTagSet activationBlockedTags
    ) {
        public static final Tags NONE = new Tags(
            AbilityTagSet.EMPTY, AbilityTagSet.EMPTY, AbilityTagSet.EMPTY,
            AbilityTagSet.EMPTY, AbilityTagSet.EMPTY, AbilityTagSet.EMPTY
        );

        public static final Codec<Tags> CODEC =
            RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        tagSet("id_tags").forGetter(Tags::idTags),
                        tagSet("cancel_abilities_with_tag").forGetter(Tags::cancelAbilitiesWithTag),
                        tagSet("block_abilities_with_tag").forGetter(Tags::blockAbilitiesWithTag),
                        tagSet("owner_acquired_tags").forGetter(Tags::ownerAcquiredTags),
                        tagSet("activation_required_tags").forGetter(Tags::activationRequiredTags),
                        tagSet("activation_blocked_tags").forGetter(Tags::activationBlockedTags)
                    )
                    .apply(instance, Tags::new)
            );

        private static MapCodec<AbilityTagSet> tagSet(final String name) {
            return AbilityTagSet.CODEC.optionalFieldOf(name, AbilityTagSet.EMPTY);
        }
    }

    /// One tag that activates the ability, and what has to happen to that tag for it to fire.
    @ScriptType(autoRegister = false)
    public record Trigger(AbilityTag triggerTag, Source source) {
        public static final Codec<Trigger> CODEC =
            RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        AbilityTag.ENUM_MANAGER.getNameCodec().fieldOf("tag")
                            .forGetter(Trigger::triggerTag),
                        ParseUtils.enumCodec(Source.class).fieldOf("source")
                            .forGetter(Trigger::source)
                    )
                    .apply(instance, Trigger::new)
            );

        public enum Source {
            /// [AbilitySystemComponent#triggerEvent] was called with the trigger tag.
            EVENT,

            /// [AbilitySystemComponent#addAbilityTag] granted the trigger tag.
            ON_TAG_ADDED,

            /// [AbilitySystemComponent#removeAbilityTag] released the trigger tag.
            ON_TAG_REMOVED
        }
    }
}
