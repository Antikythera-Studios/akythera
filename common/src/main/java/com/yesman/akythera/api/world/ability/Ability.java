package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.client.input.AbilityInputInterface;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

public abstract class Ability<S extends AbilityState> {
    /// The datapack entry that built this ability, or null when Java did.
    ///
    /// An ability with no key cannot be named on the wire, so it never leaves the server.
    private @Nullable ResourceKey<AbilityBlueprint> blueprintKey;

    public @Nullable ResourceKey<AbilityBlueprint> getBlueprintKey() {
        return blueprintKey;
    }

    @ApiStatus.Internal
    public void setBlueprintKey(final ResourceKey<AbilityBlueprint> blueprintKey) {
        this.blueprintKey = blueprintKey;
    }

    /// Which sides instantiate this ability.
    public InstantiationScope getInstantiationScope() {
        return InstantiationScope.EVERYWHERE;
    }

    /// This ability's key binding, as a static nested class implementing [AbilityInputInterface].
    /// Null leaves the ability activated only by command or trigger.
    ///
    /// A dedicated server holds no client classes, and this declaration does not make it load one:
    /// the generic erases, so the descriptor names only [Class], and the nested class is
    /// resolved by the `ldc` in an override's body, which runs on a client.
    @ClientOnly
    public @Nullable Class<? extends AbilityInputInterface> getAbilityInputInterfaceClass() {
        return null;
    }

    /// Builds the per-owner state handed to every callback, once per [AbilityInstance]. An ability
    /// with no variables of its own keeps [AbilityState#EMPTY].
    @SuppressWarnings("unchecked")
    public S createState() {
        return (S)AbilityState.EMPTY;
    }

    /// Whether the owner may activate this ability right now.
    public boolean canActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {
        return true;
    }

    /// Called when this ability is equipped into a slot.
    public void onEquipped(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    /// Called when this ability leaves its slot.
    public void onUnequipped(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    public abstract void onActivated(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );

    public abstract void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );

    public abstract void onCanceled(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );

    public abstract void onFinished(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );
}
