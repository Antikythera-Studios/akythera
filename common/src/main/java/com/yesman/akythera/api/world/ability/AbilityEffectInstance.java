package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/// One live application of an [AbilityEffect]. Never created for
/// [AbilityEffect.ApplicationType#INSTANT].
public final class AbilityEffectInstance {
    /// Which of the two effect roles this application fills.
    public enum Role {
        /// Lifetime. Counts down, then rolls its modifiers back.
        DURATION,

        /// Recharge timer. Banks charges, and its modifier list is ignored.
        COOLDOWN
    }

    private final AbilityEffect effect;

    private final Role role;

    /// `namespace:path_n` of the effect's id, `n` numbered per target entity.
    private final Identifier modifierId;

    /// The `n` in [#modifierId].
    private final int modifierIndex;

    private final List<ResourceDelta> resourceDeltas = new ArrayList<>();

    /// Ticks left on the timer: a lifetime in the Duration role, a recharge in the Cooldown role.
    private int remainingTicks;

    /// Charges banked.
    private int stacks;

    private boolean applied;

    /// The ability this belongs to. Null only for an application nothing claimed.
    private @Nullable AbilityInstance source;

    /// Writes an [AbilityEffect.ApplicationType#INSTANT] effect straight through, leaving no
    /// instance and nothing to roll back.
    ///
    /// Its [net.minecraft.world.entity.ai.attributes.Attribute] modifiers are permanent, and are
    /// replaced rather than stacked when the same effect is applied again.
    public static void applyInstant(final AbilityEffect effect, final LivingEntityPatch<?> owner) {
        for (AbilityEffect.Modifier<?> modifier : effect.getModifiers()) {
            AttributeModifierEntry attributeEntry = modifier.asAttributeModifier();

            if (attributeEntry != null) {
                AttributeInstance attribute = owner.getOriginal().getAttribute(attributeEntry.attribute());

                if (attribute != null) {
                    attribute.removeModifier(effect.getId());
                    attribute.addPermanentModifier(
                        new AttributeModifier(
                            effect.getId(),
                            attributeEntry.modifier().amount(),
                            attributeEntry.modifier().operation()
                        )
                    );
                }

                continue;
            }

            AbilityEffect.AbilityResourceSpec resourceSpec = modifier.asAbilitySource();

            if (resourceSpec != null) {
                resourceSpec.abilityResource().add(owner, resourceSpec.amount());
            }
        }
    }

    public AbilityEffectInstance(final AbilityEffect effect, final Role role, final int modifierIndex) {
        this.effect = effect;
        this.role = role;
        this.modifierId = effect.getId().withSuffix("_" + modifierIndex);
        this.modifierIndex = modifierIndex;
        this.remainingTicks = effect.getDuration();
    }

    public AbilityEffect getEffect() {
        return effect;
    }

    public Role getRole() {
        return role;
    }

    /// The ability that applied this, or null when nothing claimed it.
    public @Nullable AbilityInstance getSource() {
        return source;
    }

    @ApiStatus.Internal
    void bindSource(final AbilityInstance source) {
        this.source = source;
    }

    public Identifier getModifierId() {
        return modifierId;
    }

    public int getModifierIndex() {
        return modifierIndex;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public int getStacks() {
        return stacks;
    }

    public boolean isApplied() {
        return applied;
    }

    /// Writes every modifier onto `owner` and memorizes the resource deltas. Applying twice is a
    /// no-op.
    public void apply(final LivingEntityPatch<?> owner) {
        if (applied || role == Role.COOLDOWN) {
            return;
        }

        applied = true;

        for (AbilityEffect.Modifier<?> modifier : effect.getModifiers()) {
            AttributeModifierEntry attributeEntry = modifier.asAttributeModifier();

            if (attributeEntry != null) {
                AttributeInstance attribute = owner.getOriginal().getAttribute(attributeEntry.attribute());

                if (attribute != null) {
                    attribute.addTransientModifier(
                        new AttributeModifier(
                            modifierId,
                            attributeEntry.modifier().amount(),
                            attributeEntry.modifier().operation()
                        )
                    );
                }

                continue;
            }

            AbilityEffect.AbilityResourceSpec resourceSpec = modifier.asAbilitySource();

            if (resourceSpec == null) {
                continue;
            }

            float delta = resourceSpec.abilityResource().add(owner, resourceSpec.amount());

            if (delta != 0.0F) {
                resourceDeltas.add(new ResourceDelta(resourceSpec.abilityResource(), delta));
            }
        }
    }

    /// Undoes [#apply]. Rolling back an unapplied instance is a no-op.
    public void rollback(final LivingEntityPatch<?> owner) {
        if (!applied) {
            return;
        }

        applied = false;

        for (AbilityEffect.Modifier<?> modifier : effect.getModifiers()) {
            AttributeModifierEntry attributeEntry = modifier.asAttributeModifier();

            if (attributeEntry == null) {
                continue;
            }

            AttributeInstance attribute = owner.getOriginal().getAttribute(attributeEntry.attribute());

            if (attribute != null) {
                attribute.removeModifier(modifierId);
            }
        }

        for (ResourceDelta resourceDelta : resourceDeltas) {
            resourceDelta.abilityResource().add(owner, -resourceDelta.amount());
        }

        resourceDeltas.clear();
    }

    /// Counts the lifetime down one tick. True once the effect has run out, and never for
    /// [AbilityEffect.ApplicationType#INFINITE].
    public boolean tickDuration() {
        if (role != Role.DURATION
            || effect.getApplicationType() != AbilityEffect.ApplicationType.DURATION) {
            return false;
        }

        if (remainingTicks > 0) {
            remainingTicks--;
        }

        return remainingTicks <= 0;
    }

    /// Counts the recharge timer down one tick, banking a charge and restarting whenever it
    /// reaches zero. True on the tick a charge was banked. Holds at a full bank.
    public boolean tickRecharge() {
        if (stacks >= effect.getMaxStack()) {
            return false;
        }

        remainingTicks--;

        if (remainingTicks > 0) {
            return false;
        }

        stacks++;
        remainingTicks = effect.getDuration();

        return true;
    }

    /// Overwrites the timer and the bank with the server's. Client side only.
    @ApiStatus.Internal
    void mirror(final int remainingTicks, final int stacks) {
        this.remainingTicks = remainingTicks;
        this.stacks = stacks;
    }

    /// Counts a client's copy down one tick, holding at zero.
    ///
    /// Never banks a charge and never expires; the server says when either happens.
    @ApiStatus.Internal
    void tickMirror(final boolean held) {
        if (held || remainingTicks <= 0) return;
        if (role == Role.DURATION
            && effect.getApplicationType() != AbilityEffect.ApplicationType.DURATION) {
            return;
        }
        if (role == Role.COOLDOWN && stacks >= effect.getMaxStack()) return;

        remainingTicks--;
    }

    public boolean hasStack() {
        return stacks > 0;
    }

    /// Spends one banked charge and restarts the recharge timer at full. False when none is
    /// banked.
    public boolean consumeStack() {
        if (stacks <= 0) {
            return false;
        }

        stacks--;
        remainingTicks = effect.getDuration();

        return true;
    }

    /// What [#apply] added to one [AbilityResource].
    private record ResourceDelta(AbilityResource abilityResource, float amount) {
    }
}
