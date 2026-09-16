package com.yesman.akythera.client.core.networking;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.networking.PayloadContext;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCues;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.client.input.AbilityInputBinder;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityEffectSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityEquipmentSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityLifecycle;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityStateSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityTagSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundCancelAbilityCue;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundDataBlockSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAbilityCue;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAnimMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlockSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public interface AkytheraClientBoundPayloadHandler {
    /// Receiver is always a [LocalPlayer] for client bound payloads
    static LocalPlayer getLocalPlayer(PayloadContext context) {
        return (LocalPlayer)context.receiver();
    }

    static void handleBlockSync(ClientBoundDataBlockSync payload, PayloadContext context) {
        // On an integrated server the reload listener already ingested into the shared static
        // manager; re-ingesting from the client thread would race it.
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }

        EnumerableDataBlockSync.ingest(payload.dataBlockType(), payload.entries());
    }

    /// Mirrors one tag transition onto the client's copy of the component. The grant count is not
    /// replicated, so a release drops the tag outright.
    static void handleAbilityTagSync(final ClientBoundAbilityTagSync payload, final PayloadContext context) {
        if (Minecraft.getInstance().level == null) return;

        Entity entity =
            Minecraft.getInstance().level.getEntity(payload.entityId());

        if (entity == null) return;

        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) return;

            if (payload.granted()) {
                component.addAbilityTag(payload.tag());
            } else {
                component.clearAbilityTag(payload.tag());
            }
        });
    }

    /// Mirrors one slot's equipment onto the client's copy of the component.
    ///
    /// Skipped when the slot already holds the same entry.
    static void handleAbilityEquipmentSync(
        final ClientBoundAbilityEquipmentSync payload,
        final PayloadContext context
    ) {
        if (Minecraft.getInstance().level == null) return;

        Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());

        if (entity == null) return;

        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) return;

            if (payload.ability().isEmpty()) {
                component.removeAbility(payload.slot());

                if (isLocalPlayer(entity)) AbilityInputBinder.unbind(payload.slot());

                return;
            }

            AbilityInstance held = component.getInstance(payload.slot());

            if (held != null && held.getAbility() != null
                && payload.ability().get().equals(held.getAbility().getBlueprintKey())) {
                return;
            }

            Minecraft.getInstance().level.registryAccess()
                .lookup(AkytheraRegistries.DataPack.ABILITY_KEY)
                .flatMap(registry -> registry.get(payload.ability().get()))
                .map(AbilityBlueprint::createAbility)
                .ifPresent(ability -> {
                    AbilityInstance equipped = component.giveAbility(payload.slot(), ability);

                    // A snapshot carries the current flag.
                    if (equipped != null && payload.activated()) equipped.applyActivated(component);

                    // Only the local player has a keyboard, and only a filled slot has a key to read.
                    if (equipped != null && isLocalPlayer(entity)) {
                        AbilityInputBinder.bind(component, payload.slot(), equipped);
                    }
                });
        });
    }

    /// Applies the server's activation verdict. The client re-decides nothing: its cooldown never
    /// banks a charge, so its own `canActivate` would always refuse.
    static void handleAbilityLifecycle(
        final ClientBoundAbilityLifecycle payload,
        final PayloadContext context
    ) {
        if (Minecraft.getInstance().level == null) return;

        Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());

        if (entity == null) return;

        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) return;

            AbilityInstance instance = component.getInstance(payload.slot());

            if (instance == null) return;

            switch (payload.transition()) {
                case ACTIVATED -> instance.applyActivated(component);
                case RECAST -> instance.applyRecast(component);
                case CANCELED -> {
                    component.stopMirroredCuesOf(instance);
                    instance.cancelAbility(component);
                }
                case FINISHED -> {
                    component.stopMirroredCuesOf(instance);
                    instance.endAbility(component);
                }
            }
        });
    }

    static boolean isLocalPlayer(final Entity entity) {
        return entity == Minecraft.getInstance().player;
    }

    /// The client's entity `entityId`, or null.
    ///
    /// Never name a client-only type here. A dedicated server resolves every handler in this
    /// interface while registering payload types, and loading one fails the whole mod.
    static @Nullable Entity entityOf(final int entityId) {
        if (Minecraft.getInstance().level == null) return null;

        return Minecraft.getInstance().level.getEntity(entityId);
    }

    /// The ability component on `entity`, or null.
    static @Nullable AbilitySystemComponent componentOf(final @Nullable Entity entity) {
        if (entity == null) return null;

        return AkytheraPatches.getEntityPatchOptional(entity)
            .map(entityPatch -> entityPatch.getComponent(AbilitySystemComponent.class))
            .orElse(null);
    }

    /// Reads the server's state into the slot's live state object, the one the callbacks and cues
    /// already hold.
    static void handleAbilityStateSync(
        final ClientBoundAbilityStateSync payload,
        final PayloadContext context
    ) {
        Entity entity = entityOf(payload.entityId());
        AbilitySystemComponent component = componentOf(entity);

        if (component == null) return;

        AbilityInstance instance = component.getInstance(payload.slot());

        if (instance == null || instance.getAbility() == null) return;

        boolean read = AbilityState.decode(
            instance.getState(), PacketFlow.CLIENTBOUND, payload.state(), entity.level()
        );

        if (!read) {
            Akythera.LOGGER.warn(
                "State of {} on entity {} did not decode", payload.slot(), payload.entityId()
            );
        }
    }

    /// Mirrors one effect application, for the HUD and for client side validation to read.
    static void handleAbilityEffectSync(
        final ClientBoundAbilityEffectSync payload,
        final PayloadContext context
    ) {
        Entity entity = entityOf(payload.entityId());
        AbilitySystemComponent component = componentOf(entity);

        if (component == null) return;

        if (payload.ended()) {
            component.dropMirroredEffect(payload.effectId(), payload.modifierIndex());

            return;
        }

        AbilityInstance source = payload.source().map(component::getInstance).orElse(null);

        // A slot this client holds nothing in: the application's ability never reached it.
        if (payload.source().isPresent() && source == null) return;

        AbilityEffect effect = resolveEffect(payload.effectId(), source, entity.level().registryAccess());

        if (effect == null) {
            Akythera.LOGGER.warn("Unknown ability effect {}", payload.effectId());

            return;
        }

        component.mirrorEffect(
            effect,
            payload.modifierIndex(),
            payload.role(),
            source,
            payload.remainingTicks(),
            payload.stacks()
        );
    }

    /// The effect `effectId` names: first among the source ability's own settings, the only place a
    /// Java built effect can be found, then in the data pack registry.
    private static @Nullable AbilityEffect resolveEffect(
        final Identifier effectId,
        final @Nullable AbilityInstance source,
        final RegistryAccess registryAccess
    ) {
        if (source != null && source.getAbility() instanceof SystematicAbility<?> systematic) {
            AbilityEffect cooldown = systematic.getCooldownEffect();

            if (cooldown != null && cooldown.getId().equals(effectId)) return cooldown;

            AbilityEffect duration = systematic.getDurationEffect();

            if (duration != null && duration.getId().equals(effectId)) return duration;
        }

        return registryAccess.lookup(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY)
            .flatMap(registry -> registry.getOptional(effectId))
            .orElse(null);
    }

    /// Starts the play the server sent. A cue whose ability this client never instantiated has
    /// nowhere to park, and is dropped.
    static void handlePlayAbilityCue(
        final ClientBoundPlayAbilityCue payload,
        final PayloadContext context
    ) {
        AbilityCue cue = AbilityCues.get(payload.cueId());

        if (cue == null) {
            Akythera.LOGGER.warn("Unknown ability cue {}", payload.cueId());

            return;
        }

        AbilitySystemComponent component = componentOf(entityOf(payload.entityId()));

        if (component == null) return;

        AbilityInstance source = payload.source().map(component::getInstance).orElse(null);

        if (payload.source().isPresent() && source == null) return;

        component.startMirroredCue(
            cue,
            source,
            payload.withState() && source != null ? source.getState() : null,
            payload.endsWithAbility()
        );
    }

    /// Cancels the plays the server cancelled, by the same tag match it ran.
    static void handleCancelAbilityCue(
        final ClientBoundCancelAbilityCue payload,
        final PayloadContext context
    ) {
        AbilitySystemComponent component = componentOf(entityOf(payload.entityId()));

        if (component != null) component.cancelMirroredCues(payload.query());
    }

    static void handlePlayMontage(ClientBoundPlayAnimMontage payload, PayloadContext context) {
        AkytheraPatches.getEntityPatchOptional(context.receiver()).ifPresent(entityPatch -> {
            if (entityPatch.getArmatureComponent() == null) return;

            entityPatch.getArmatureComponent().getAnimator().playAnimMontage(
                ResourceKey.create(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, payload.montageId()),
                payload.montageSection()
            );
        });
    }
}
