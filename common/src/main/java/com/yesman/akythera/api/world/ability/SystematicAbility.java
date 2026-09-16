package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// An [Ability] whose common behaviors are settings rather than code: the activation preset, the
/// cost, the cooldown and the duration effect. A developer extends this so other developers can
/// parent entries on that subclass and fill the settings in the editor.
public class SystematicAbility<S extends AbilityState> extends Ability<S> {
    private final Settings settings;

    public SystematicAbility(final Settings settings) {
        this.settings = settings;
    }

    /// Builds from a datapack entry, resolving the named effects.
    public SystematicAbility(final Holder.Reference<AbilityBlueprint> blueprint) {
        this(blueprint.value().settings().resolve());
    }

    public Settings getSettings() {
        return settings;
    }

    public ActivationType getActivationType() {
        return settings.activationType();
    }

    public @Nullable AbilityEffect getCooldownEffect() {
        return settings.cooldownEffect();
    }

    public @Nullable AbilityEffect getDurationEffect() {
        return settings.durationEffect();
    }

    public @Nullable Holder<InputAction> getInputAction() {
        return settings.inputAction();
    }

    public List<AbilityEffect> getCosts() {
        return settings.costs();
    }

    public Tags getTags() {
        return settings.tags();
    }

    public List<Trigger> getTriggers() {
        return settings.triggers();
    }

    /// Whether any tag in `patterns` matches any tag in `candidates`, by [AbilityTag#matches].
    public static boolean matchesAny(final Set<AbilityTag> patterns, final Set<AbilityTag> candidates) {
        for (AbilityTag pattern : patterns) {
            for (AbilityTag candidate : candidates) {
                if (pattern.matches(candidate)) return true;
            }
        }

        return false;
    }

    /// Starts the recharge timer.
    @Override
    public void onEquipped(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        super.onEquipped(abilitySystemComponent, abilityInstance, abilityState);

        AbilityEffect cooldownEffect = settings.cooldownEffect();

        // A client's timer arrives from the server, numbered as the server numbered it.
        if (cooldownEffect != null && abilitySystemComponent.getOwner().isLogicalServer()) {
            abilitySystemComponent.getOrCreateCooldown(cooldownEffect, abilityInstance);
        }
    }

    /// Refuses the activation when no charge is banked, or when the owner cannot pay every cost.
    @Override
    public boolean canActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        if (!super.canActivate(abilitySystemComponent, abilityInstance, abilityState)) return false;

        Tags tags = settings.tags();

        for (AbilityTag required : tags.activationRequiredTags()) {
            if (!abilitySystemComponent.hasAbilityTag(required)) return false;
        }

        for (AbilityTag blocked : tags.activationBlockedTags()) {
            if (abilitySystemComponent.hasAbilityTag(blocked)) return false;
        }

        // Server side only: this reads instances, which a client holds only partially.
        if (abilitySystemComponent.getOwner().isLogicalServer()
            && isBlockedByActivatedAbility(abilitySystemComponent, abilityInstance)) {
            return false;
        }

        AbilityEffect cooldownEffect = settings.cooldownEffect();

        if (cooldownEffect != null) {
            AbilityEffectInstance cooldown =
                abilitySystemComponent.getCooldown(cooldownEffect, abilityInstance);

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
                && matchesAny(other.getTags().blockAbilitiesWithTag(), settings.tags().idTags())) {
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

        for (AbilityEffect cost : settings.costs()) {
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

        AbilityEffect cooldownEffect = settings.cooldownEffect();

        if (cooldownEffect != null) {
            AbilityEffectInstance cooldown =
                abilitySystemComponent.getOrCreateCooldown(cooldownEffect, abilityInstance);

            // Sent ahead of the clash cancelling and the tag grants below, which both run callbacks.
            if (cooldown.consumeStack()) abilitySystemComponent.syncEffect(cooldown);
        }

        cancelClashingAbilities(abilitySystemComponent, abilityInstance);

        for (AbilityTag acquired : settings.tags().ownerAcquiredTags()) {
            abilitySystemComponent.addAbilityTag(acquired);
        }

        AbilityEffect durationEffect = settings.durationEffect();

        if (durationEffect != null) {
            abilitySystemComponent.applyEffect(
                durationEffect, AbilityEffectInstance.Role.DURATION, abilityInstance
            );
        }
    }

    @Override
    public void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

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
        Set<AbilityTag> patterns = settings.tags().cancelAbilitiesWithTag();

        if (patterns.isEmpty()) return;

        for (Map.Entry<AbilitySlot, AbilityInstance> entry
            : List.copyOf(abilitySystemComponent.getInstances().entrySet())) {
            AbilityInstance held = entry.getValue();

            if (held == self || !held.isActivated()) continue;

            if (held.getAbility() instanceof SystematicAbility<?> other
                && matchesAny(patterns, other.getTags().idTags())) {
                abilitySystemComponent.cancelAbility(entry.getKey());
            }
        }
    }

    /// Releases the tags this ability granted its owner while it ran.
    private void releaseOwnerAcquiredTags(final AbilitySystemComponent abilitySystemComponent) {
        for (AbilityTag acquired : settings.tags().ownerAcquiredTags()) {
            abilitySystemComponent.removeAbilityTag(acquired);
        }
    }

    /// Ends this ability's own duration effect, leaving alone any other ability's application of
    /// the same effect.
    private void rollBackDurationEffect(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance
    ) {
        AbilityEffect durationEffect = settings.durationEffect();

        if (durationEffect == null) return;

        for (AbilityEffectInstance live
            : List.copyOf(abilitySystemComponent.getEffectInstances(durationEffect.getId()))) {
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
    public record Tags(
        Set<AbilityTag> idTags,
        Set<AbilityTag> cancelAbilitiesWithTag,
        Set<AbilityTag> blockAbilitiesWithTag,
        Set<AbilityTag> ownerAcquiredTags,
        Set<AbilityTag> activationRequiredTags,
        Set<AbilityTag> activationBlockedTags
    ) {
        public static final Tags NONE =
            new Tags(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

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

        private static MapCodec<Set<AbilityTag>> tagSet(final String name) {
            return AbilityTag.ENUM_MANAGER.getNameCodec().listOf()
                .xmap(Set::copyOf, List::copyOf)
                .optionalFieldOf(name, Set.of());
        }

        public Tags {
            idTags = Set.copyOf(idTags);
            cancelAbilitiesWithTag = Set.copyOf(cancelAbilitiesWithTag);
            blockAbilitiesWithTag = Set.copyOf(blockAbilitiesWithTag);
            ownerAcquiredTags = Set.copyOf(ownerAcquiredTags);
            activationRequiredTags = Set.copyOf(activationRequiredTags);
            activationBlockedTags = Set.copyOf(activationBlockedTags);
        }
    }

    /// One tag that activates the ability, and what has to happen to that tag for it to fire.
    public record Trigger(AbilityTag triggerTag, Source source) {
        public static final Codec<Trigger> CODEC =
            RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        AbilityTag.ENUM_MANAGER.getNameCodec().fieldOf("tag")
                            .forGetter(Trigger::triggerTag),
                        AbilityEffect.enumCodec(Source.class).fieldOf("source")
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

    /// The datapack form of [Settings], naming its effects instead of holding them.
    public record SettingsSpec(
        ActivationType activationType,
        List<ResourceKey<AbilityEffect>> costs,
        Optional<ResourceKey<AbilityEffect>> cooldownEffect,
        Optional<ResourceKey<AbilityEffect>> durationEffect,
        Optional<ResourceKey<InputAction>> inputAction,
        Tags tags,
        List<Trigger> triggers
    ) {
        public static final MapCodec<SettingsSpec> MAP_CODEC =
            RecordCodecBuilder.mapCodec(
                instance ->
                    instance.group(
                        AbilityEffect.enumCodec(ActivationType.class).fieldOf("activation_type")
                            .forGetter(SettingsSpec::activationType),
                        effectKey().listOf().optionalFieldOf("costs", List.of())
                            .forGetter(SettingsSpec::costs),
                        effectKey().optionalFieldOf("cooldown_effect")
                            .forGetter(SettingsSpec::cooldownEffect),
                        effectKey().optionalFieldOf("duration_effect")
                            .forGetter(SettingsSpec::durationEffect),
                        ResourceKey.codec(AkytheraRegistries.DataPack.INPUT_ACTION_KEY)
                            .optionalFieldOf("input_action").forGetter(SettingsSpec::inputAction),
                        Tags.CODEC.optionalFieldOf("tags", Tags.NONE).forGetter(SettingsSpec::tags),
                        Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of())
                            .forGetter(SettingsSpec::triggers)
                    )
                    .apply(instance, SettingsSpec::new)
            );

        public static final Codec<SettingsSpec> CODEC = MAP_CODEC.codec();

        private static Codec<ResourceKey<AbilityEffect>> effectKey() {
            return ResourceKey.codec(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY);
        }

        /// Resolves every named effect against the loaded registry.
        public Settings resolve() {
            return new Settings(
                activationType,
                costs.stream().map(SettingsSpec::lookUp).toList(),
                cooldownEffect.map(SettingsSpec::lookUp).orElse(null),
                durationEffect.map(SettingsSpec::lookUp).orElse(null),
                inputAction.map(SettingsSpec::lookUpInput).orElse(null),
                tags,
                triggers
            );
        }

        private static AbilityEffect lookUp(final ResourceKey<AbilityEffect> key) {
            return StaticRegistryAccess.getRegistryAccess().getOrThrow(key).value();
        }

        private static Holder<InputAction> lookUpInput(final ResourceKey<InputAction> key) {
            return StaticRegistryAccess.getRegistryAccess().getOrThrow(key);
        }
    }

    /// What the editor fills in.
    ///
    /// @param costs          spent on activation, and checked before it
    /// @param cooldownEffect the recharge timer banking this ability's charges
    /// @param durationEffect present only when `activationType` is [ActivationType#DURATION], whose
    ///                       expiry is what finishes the ability
    /// @param inputAction    the key this ability reads, required by the presets that stay
    ///                       activated by input
    public record Settings(
        ActivationType activationType,
        List<AbilityEffect> costs,
        @Nullable AbilityEffect cooldownEffect,
        @Nullable AbilityEffect durationEffect,
        @Nullable Holder<InputAction> inputAction,
        Tags tags,
        List<Trigger> triggers
    ) {
        public Settings(
            final ActivationType activationType,
            final List<AbilityEffect> costs,
            final @Nullable AbilityEffect cooldownEffect,
            final @Nullable AbilityEffect durationEffect
        ) {
            this(activationType, costs, cooldownEffect, durationEffect, null, Tags.NONE, List.of());
        }

        public Settings {
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

            costs = List.copyOf(costs);
            triggers = List.copyOf(triggers);
        }
    }
}
