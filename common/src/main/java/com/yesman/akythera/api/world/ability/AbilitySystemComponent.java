package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityEffectSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityEquipmentSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityLifecycle;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityStateSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundAbilityTagSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundCancelAbilityCue;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAbilityCue;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The abilities one entity holds, one [AbilityInstance] per filled [AbilitySlot].
public final class AbilitySystemComponent
    implements DynamicPatchComponent<LivingEntityPatch<?>>, TickableDynamicComponent<LivingEntityPatch<?>>, UniqueComponent
{
    /// The name this component is attached under.
    public static final String COMPONENT_NAME = "ability_system";

    /// How many triggers deep one chain may run before it is cut.
    private static final int MAX_TRIGGER_DEPTH = 8;

    private LivingEntityPatch<?> owner;

    private final Map<AbilitySlot, AbilityInstance> instances = new LinkedHashMap<>();

    /// Live grant count per [AbilityTag]. A tag is released only once every grant of it is.
    private final Map<AbilityTag, Integer> ownerTags = new LinkedHashMap<>();

    /// Live effect applications, keyed by [AbilityEffect#getId].
    private final Map<Identifier, List<AbilityEffectInstance>> effectInstances = new LinkedHashMap<>();

    /// Live cue plays, in the order they started.
    private final List<AbilityCueInstance> cueInstances = new ArrayList<>();

    /// Tasks running this tick.
    private final List<AbilityTaskHandle> runningTasks = new ArrayList<>();

    /// Tasks started since the last tick.
    private final List<AbilityTaskHandle> pendingTasks = new ArrayList<>();

    /// The loadout a blueprint declared, equipped as the owner joins a level. Construction is too
    /// early: it runs inside `Entity`'s own constructor, before the subclass constructors, the saved
    /// data, and a player's connection.
    private Map<AbilitySlot, ResourceKey<AbilityBlueprint>> pendingLoadout;

    /// How deep the current chain of triggers runs. An ability that grants the tag it triggers on
    /// would otherwise recurse until the stack gives out.
    private int triggerDepth;

    /// Whether [AbilityTrace] writes this owner's lifecycle to the log.
    private boolean tracing;

    public AbilitySystemComponent(final LivingEntityPatch<?> owner) {
        this(owner, Map.of());
    }

    public AbilitySystemComponent(
        final LivingEntityPatch<?> owner,
        final Map<AbilitySlot, ResourceKey<AbilityBlueprint>> loadout
    ) {
        this.owner = owner;
        this.pendingLoadout = Map.copyOf(loadout);
    }

    /// Equips `ability` into `slot`, replacing whatever the slot held.
    ///
    /// @return the new instance, or null when this component already holds `ability` in another slot
    public @Nullable AbilityInstance giveAbility(final AbilitySlot slot, final Ability<?> ability) {
        for (AbilityInstance held : instances.values()) {
            if (held.getAbility() == ability) return null;
        }

        removeAbility(slot);

        AbilityInstance instance = new AbilityInstance();
        instances.put(slot, instance);

        // Sent before the equip callbacks, which can grant a tag, fire a trigger and activate this
        // very slot. A packet sent afterwards would arrive behind that activation.
        syncEquipment(slot, ability, false);

        instance.equip(this, ability, slot);

        AbilityTrace.log(this, instance, "equipped");

        return instance;
    }

    /// Releases the ability in `slot`, cancelling it first when it is still running.
    ///
    /// Every task the ability started ends here, including the ones that declared they outlive it.
    ///
    /// @return whether the slot held one
    @ScriptFunction(label = "Remove Ability", category = "Ability", desc = "Releases the ability in the slot.")
    public boolean removeAbility(final AbilitySlot slot) {
        AbilityInstance instance = instances.remove(slot);

        if (instance == null) return false;

        Ability<?> released = instance.getAbility();

        // Captured and sent before unequip, which nulls the ability and runs callbacks of its own.
        if (released != null) {
            sendScoped(released, new ClientBoundAbilityEquipmentSync(
                owner.getOriginal().getId(), slot, Optional.empty(), false
            ));
        }

        stopTasksOf(instance, true);
        stopCuesOf(instance, true);

        if (instance.isActivated()) AbilityTrace.log(this, instance, "canceled");

        AbilityTrace.log(this, instance, "unequipped");
        instance.unequip(this);

        // The ability's own cancel and unequip callbacks may have started more.
        stopTasksOf(instance, true);
        stopCuesOf(instance, true);

        return true;
    }

    /// Activates the ability in `slot`, or recasts it when it is already running.
    @ScriptFunction(label = "Activate Ability", category = "Ability", desc = "Activates the slot, or recasts it when it is already running.")
    public boolean activateAbility(final AbilitySlot slot) {
        AbilityInstance instance = instances.get(slot);

        if (instance == null) return false;

        boolean wasActivated = instance.isActivated();
        @Nullable ActivationType activationType = activationTypeOf(instance);

        // A Toggle preset's second request cancels; a Hold preset's second request is the release,
        // which finishes only when the hold reached its threshold. Only the presets with neither
        // rule recast.
        if (wasActivated && activationType != null) {
            if (activationType.recastCancels()) return cancelAbility(slot);

            if (activationType.recastFinishes()) {
                return heldToThreshold(instance) ? endAbility(slot) : cancelAbility(slot);
            }
        }

        if (wasActivated) syncLifecycle(slot, instance, ClientBoundAbilityLifecycle.Transition.RECAST);

        if (!instance.tryActivate(this)) return false;

        if (!wasActivated) {
            instance.setHoldingTicks(0);
            syncLifecycle(slot, instance, ClientBoundAbilityLifecycle.Transition.ACTIVATED);
        }

        AbilityTrace.log(this, instance, wasActivated ? "recast" : "activated");

        // An Instant preset ends on the tick it activated.
        if (!wasActivated && activationType == ActivationType.INSTANT) endAbility(slot);

        return true;
    }

    /// The preset driving `instance`, or null for a bare [Ability].
    private static @Nullable ActivationType activationTypeOf(final AbilityInstance instance) {
        return instance.getAbility() instanceof SystematicAbility<?> systematic
            ? systematic.getActivationType()
            : null;
    }

    /// Stops the ability in `slot` as interrupted.
    ///
    /// Its tasks end before [Ability#onCanceled] runs.
    @ScriptFunction(label = "Cancel Ability", category = "Ability", desc = "Stops the slot as interrupted.")
    public boolean cancelAbility(final AbilitySlot slot) {
        AbilityInstance instance = instances.get(slot);

        if (instance == null || !instance.isActivated()) return false;

        stopTasksOf(instance, false);
        stopCuesOf(instance, false);

        // Re-checked: a task or cue callback may already have ended the ability.
        if (!instance.isActivated()) return false;

        syncLifecycle(slot, instance, ClientBoundAbilityLifecycle.Transition.CANCELED);

        if (!instance.cancelAbility(this)) return false;

        AbilityTrace.log(this, instance, "canceled");

        return true;
    }

    /// Stops the ability in `slot` as completed.
    ///
    /// Its tasks end before [Ability#onFinished] runs.
    @ScriptFunction(label = "End Ability", category = "Ability", desc = "Stops the slot as completed.")
    public boolean endAbility(final AbilitySlot slot) {
        AbilityInstance instance = instances.get(slot);

        if (instance == null || !instance.isActivated()) return false;

        stopTasksOf(instance, false);
        stopCuesOf(instance, false);

        if (!instance.isActivated()) return false;

        syncLifecycle(slot, instance, ClientBoundAbilityLifecycle.Transition.FINISHED);

        if (!instance.endAbility(this)) return false;

        AbilityTrace.log(this, instance, "finished");

        return true;
    }

    @ScriptFunction(pure = true, label = "Get Ability Instance", category = "Ability")
    public @Nullable AbilityInstance getInstance(final AbilitySlot slot) {
        return instances.get(slot);
    }

    /// The filled slots, in the order they were equipped.
    public Map<AbilitySlot, AbilityInstance> getInstances() {
        return Collections.unmodifiableMap(instances);
    }

    // ***************************************************************
    // Ability tasks
    // ***************************************************************

    /// Starts `task` on behalf of `abilityInstance`. [AbilityTask#onStart] runs now and
    /// [AbilityTask#tick] from the next tick on.
    ///
    /// @return the handle, or null on the client or for an instance holding no ability
    @ScriptFunction(label = "Activate Ability Task", category = "Ability/Task")
    public @Nullable AbilityTaskHandle activateTask(final AbilityInstance abilityInstance, final AbilityTask task) {
        if (!owner.isLogicalServer() || abilityInstance.getAbility() == null) return null;

        task.bind(abilityInstance);

        AbilityTaskHandle handle = new AbilityTaskHandle(this, task);
        pendingTasks.add(handle);
        task.onStart(this);

        return handle;
    }

    /// Ends `handle`'s task as interrupted.
    @ApiStatus.Internal
    void cancelTask(final AbilityTaskHandle handle) {
        terminate(handle, true);
    }

    /// Ends one task, firing exactly one of [AbilityTask#onCanceled] or [AbilityTask#onEnd].
    ///
    /// The handle is invalidated and delisted before the callback runs.
    private void terminate(final AbilityTaskHandle handle, final boolean canceled) {
        if (!handle.isValid()) return;

        handle.invalidate();
        runningTasks.remove(handle);
        pendingTasks.remove(handle);

        if (canceled) {
            handle.getTask().onCanceled(this);
        } else {
            handle.getTask().onEnd(this);
        }
    }

    /// Ends `abilityInstance`'s tasks as canceled.
    ///
    /// @param includeDetached whether tasks that declared [AbilityTask#endsWithAbility] false end too
    private void stopTasksOf(final AbilityInstance abilityInstance, final boolean includeDetached) {
        List<AbilityTaskHandle> candidates = new ArrayList<>(runningTasks);
        candidates.addAll(pendingTasks);

        for (AbilityTaskHandle handle : candidates) {
            AbilityTask task = handle.getTask();

            if (task.getOwner() != abilityInstance) continue;
            if (!includeDetached && !task.endsWithAbility()) continue;

            terminate(handle, true);
        }
    }

    // ***************************************************************
    // Effects
    // ***************************************************************

    /// Applies `effect` to the owner and starts tracking it.
    ///
    /// @return the live application, or null for [AbilityEffect.ApplicationType#INSTANT]
    public @Nullable AbilityEffectInstance applyEffect(
        final AbilityEffect effect,
        final AbilityEffectInstance.Role role
    ) {
        return applyEffect(effect, role, null);
    }

    /// Applies `effect` on behalf of `source`, whose lifetime the application then follows.
    @ScriptFunction(label = "Apply Ability Effect", category = "Ability/Effect")
    public @Nullable AbilityEffectInstance applyEffect(
        final AbilityEffect effect,
        final AbilityEffectInstance.Role role,
        final @Nullable AbilityInstance source
    ) {
        // An application is the server's; a client learns of one from its sync.
        if (!owner.isLogicalServer()) return null;

        if (effect.getApplicationType() == AbilityEffect.ApplicationType.INSTANT) {
            AbilityEffectInstance.applyInstant(effect, owner);

            return null;
        }

        List<AbilityEffectInstance> live = effectInstances.computeIfAbsent(
            effect.getId(), id -> new ArrayList<>()
        );

        AbilityEffectInstance instance = new AbilityEffectInstance(effect, role, nextModifierIndex(live));

        if (source != null) instance.bindSource(source);

        live.add(instance);
        syncEffect(instance, false);
        instance.apply(owner);

        return instance;
    }

    /// The lowest index no live application of the same effect is holding.
    private static int nextModifierIndex(final List<AbilityEffectInstance> live) {
        int index = 1;

        while (true) {
            boolean taken = false;

            for (AbilityEffectInstance held : live) {
                if (held.getModifierIndex() == index) {
                    taken = true;

                    break;
                }
            }

            if (!taken) return index;

            index++;
        }
    }

    /// Drops every application whose source ability has left its slot.
    ///
    /// An application with no source outlives every ability, so binding one is what declares the
    /// lifetime dependency.
    private void sweepUnreferencedEffects() {
        for (AbilityEffectInstance instance : snapshotEffects()) {
            AbilityInstance source = instance.getSource();

            if (source != null && !instances.containsValue(source)) cancelEffect(instance);
        }
    }

    /// Rolls `instance` back and stops tracking it.
    ///
    /// @return whether this component held it
    @ScriptFunction(label = "Cancel Ability Effect", category = "Ability/Effect")
    public boolean cancelEffect(final AbilityEffectInstance instance) {
        if (!owner.isLogicalServer()) return false;

        List<AbilityEffectInstance> live = effectInstances.get(instance.getEffect().getId());

        if (live == null || !live.remove(instance)) return false;

        if (live.isEmpty()) effectInstances.remove(instance.getEffect().getId());

        // Before the rollback, the one step here that writes to the world.
        syncEffect(instance, true);
        instance.rollback(owner);

        return true;
    }

    /// The first live application of `effectId`, whatever its role.
    public @Nullable AbilityEffectInstance getEffectInstance(final Identifier effectId) {
        List<AbilityEffectInstance> live = effectInstances.get(effectId);

        return live == null || live.isEmpty() ? null : live.getFirst();
    }

    /// Every live application of `effectId`.
    public List<AbilityEffectInstance> getEffectInstances(final Identifier effectId) {
        List<AbilityEffectInstance> live = effectInstances.get(effectId);

        return live == null ? List.of() : Collections.unmodifiableList(live);
    }

    /// The timer recharging `abilityInstance`, or null while nothing has started it.
    ///
    /// One timer per ability, never shared: two abilities pointing at one [AbilityEffect] count
    /// separately.
    public @Nullable AbilityEffectInstance getCooldown(
        final AbilityEffect effect,
        final AbilityInstance abilityInstance
    ) {
        for (AbilityEffectInstance held : getEffectInstances(effect.getId())) {
            if (held.getRole() == AbilityEffectInstance.Role.COOLDOWN
                && held.getSource() == abilityInstance) {
                return held;
            }
        }

        return null;
    }

    /// The timer recharging `abilityInstance`, started on first ask.
    public AbilityEffectInstance getOrCreateCooldown(
        final AbilityEffect effect,
        final AbilityInstance abilityInstance
    ) {
        AbilityEffectInstance existing = getCooldown(effect, abilityInstance);

        if (existing != null) return existing;

        List<AbilityEffectInstance> live = effectInstances.computeIfAbsent(
            effect.getId(), id -> new ArrayList<>()
        );

        AbilityEffectInstance instance = new AbilityEffectInstance(
            effect, AbilityEffectInstance.Role.COOLDOWN, nextModifierIndex(live)
        );
        instance.bindSource(abilityInstance);
        live.add(instance);
        syncEffect(instance, false);

        return instance;
    }

    /// Every live application on this owner, in no particular order.
    public List<AbilityEffectInstance> getAllEffectInstances() {
        return Collections.unmodifiableList(snapshotEffects());
    }

    /// Equips what a blueprint declared, once, on the server.
    ///
    /// Runs as the owner joins its level, so a tracker's [#sendSnapshotTo] already sees the loadout.
    @ApiStatus.Internal
    public void equipPendingLoadout() {
        if (!owner.isLogicalServer() || pendingLoadout.isEmpty()) return;

        Map<AbilitySlot, ResourceKey<AbilityBlueprint>> loadout = pendingLoadout;
        pendingLoadout = Map.of();

        Registry<AbilityBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ABILITY_KEY);

        loadout.forEach((slot, key) -> {
            Holder.Reference<AbilityBlueprint> blueprint = registry.get(key).orElse(null);

            if (blueprint == null) {
                Akythera.LOGGER.error("Loadout names no ability {}", key.identifier());

                return;
            }

            Ability<?> ability = AbilityBlueprint.createAbility(blueprint);

            if (ability != null) giveAbility(slot, ability);
        });
    }

    /// Whether `instance` was held to at least its input action's threshold. A preset naming no
    /// input action, or no threshold, finishes.
    ///
    /// The count is the server's own; a client never sends one.
    private static boolean heldToThreshold(final AbilityInstance instance) {
        if (!(instance.getAbility() instanceof SystematicAbility<?> systematic)) return true;

        Holder<InputAction> inputAction = systematic.getInputAction();

        return inputAction == null || inputAction.value().reachedThreshold(instance.getHoldingTicks());
    }

    /// Counts each activated Hold ability's held ticks up by one.
    private void tickHoldCounts() {
        for (AbilityInstance held : instances.values()) {
            if (!held.isActivated() || activationTypeOf(held) != ActivationType.HOLD) continue;

            held.setHoldingTicks(held.getHoldingTicks() + 1);
        }
    }

    /// Drains every activated ability's Infinite costs.
    ///
    /// A Hold ability that runs dry finishes when it was held to its threshold, and cancels
    /// otherwise; every other preset cancels.
    private void tickCosts() {
        for (Map.Entry<AbilitySlot, AbilityInstance> entry : new ArrayList<>(instances.entrySet())) {
            AbilityInstance held = entry.getValue();

            if (!held.isActivated()) continue;
            if (!(held.getAbility() instanceof SystematicAbility<?> systematic)) continue;

            if (systematic.chargeCosts(owner, AbilityEffect.ApplicationType.INFINITE)) continue;

            if (systematic.getActivationType() == ActivationType.HOLD && heldToThreshold(held)) {
                endAbility(entry.getKey());
            } else {
                cancelAbility(entry.getKey());
            }
        }
    }

    /// Counts every live application on one tick: Duration applications toward their expiry,
    /// Cooldown applications toward their next charge.
    ///
    /// Duration runs first, so an ability ending this tick is already deactivated when its cooldown
    /// is asked whether to count.
    private void tickEffects() {
        sweepUnreferencedEffects();

        for (AbilityEffectInstance instance : snapshotEffects()) {
            if (instance.getRole() != AbilityEffectInstance.Role.DURATION) continue;

            if (!instance.tickDuration()) continue;

            finishAbilitiesOn(instance);
            cancelEffect(instance);
        }

        for (AbilityEffectInstance instance : snapshotEffects()) {
            if (instance.getRole() == AbilityEffectInstance.Role.COOLDOWN && !isCooldownHeld(instance)) {
                // A client counts the timer itself; only a banked charge restarts it.
                if (instance.tickRecharge()) syncEffect(instance, false);
            }
        }
    }

    /// Every live application, flattened into a copy a pass may modify the map under.
    private List<AbilityEffectInstance> snapshotEffects() {
        List<AbilityEffectInstance> snapshot = new ArrayList<>();

        effectInstances.values().forEach(snapshot::addAll);

        return snapshot;
    }

    /// Finishes the ability whose lifetime `expired` was.
    private void finishAbilitiesOn(final AbilityEffectInstance expired) {
        for (Map.Entry<AbilitySlot, AbilityInstance> entry : new ArrayList<>(instances.entrySet())) {
            AbilityInstance held = entry.getValue();

            if (!held.isActivated()) continue;

            if (expired.getSource() == held) endAbility(entry.getKey());
        }
    }

    /// Whether the ability this timer belongs to is activated, which stops the recharge.
    private static boolean isCooldownHeld(final AbilityEffectInstance cooldown) {
        AbilityInstance source = cooldown.getSource();

        return source != null && source.isActivated();
    }

    // ***************************************************************
    // Cues
    // ***************************************************************

    /// Plays `cue` on behalf of `abilityInstance`, taking [AbilityCue#endsWithAbility] as given.
    @ScriptFunction(label = "Play Ability Cue", category = "Ability/Cue")
    public @Nullable AbilityCueInstance playCue(
        final AbilityCue cue,
        final @Nullable AbilityInstance abilityInstance,
        final @Nullable AbilityState abilityState
    ) {
        return playCue(cue, abilityInstance, abilityState, cue.endsWithAbility());
    }

    /// Plays `cue`, overriding whether it ends with the ability that started it.
    ///
    /// The server keeps the play for its timer, its tags and its ability; the behavior belongs to
    /// each client that instantiates that ability. A client asking directly, as a mirrored callback
    /// does, plays nothing, because the server's packet already will.
    ///
    /// @param abilityState any non-null value hands the behavior `abilityInstance`'s own state,
    ///                     the only state that crosses the wire
    /// @return the play, or null on the client
    public @Nullable AbilityCueInstance playCue(
        final AbilityCue cue,
        final @Nullable AbilityInstance abilityInstance,
        final @Nullable AbilityState abilityState,
        final boolean endsWithAbility
    ) {
        if (!owner.isLogicalServer()) return null;

        if (AbilityCues.get(cue.getId()) != cue) {
            throw new IllegalArgumentException("A played cue must be registered in AbilityCues: " + cue.getId());
        }

        AbilityCueInstance cueInstance = new AbilityCueInstance(cue, abilityInstance, endsWithAbility);
        cueInstances.add(cueInstance);
        sendCue(cueInstance, abilityState != null);

        return cueInstance;
    }

    /// Starts `cueInstance` on the clients that instantiate its ability, or wherever the owner is
    /// tracked when no ability played it.
    private void sendCue(final AbilityCueInstance cueInstance, final boolean withState) {
        int entityId = owner.getOriginal().getId();
        Identifier cueId = cueInstance.getCue().getId();
        AbilityInstance source = cueInstance.getSource();

        if (source == null) {
            sendToScope(InstantiationScope.EVERYWHERE, new ClientBoundPlayAbilityCue(
                entityId, cueId, Optional.empty(), false, cueInstance.endsWithAbility()
            ));

            return;
        }

        Ability<?> ability = source.getAbility();
        AbilitySlot slot = source.getSlot();

        if (ability == null || slot == null) return;

        // The behavior reads the state it is handed, so the state goes first.
        if (withState) flushState(slot, source);

        sendScoped(ability, new ClientBoundPlayAbilityCue(
            entityId, cueId, Optional.of(slot), withState, cueInstance.endsWithAbility()
        ));
    }

    /// Cancels every live play whose cue carries a tag matching `query`, on the server and on every
    /// client holding one.
    ///
    /// @return how many were cancelled, always 0 on the client
    @ScriptFunction(label = "Cancel Ability Cue", category = "Ability/Cue")
    public int cancelCue(final AbilityTag query) {
        if (!owner.isLogicalServer()) return 0;

        List<AbilityCueInstance> matched = new ArrayList<>();

        for (AbilityCueInstance cueInstance : cueInstances) {
            if (cueInstance.isValid() && cueInstance.getCue().hasTag(query)) matched.add(cueInstance);
        }

        if (matched.isEmpty()) return 0;

        // InstantiationScope is declared narrowest first.
        InstantiationScope widest = InstantiationScope.SERVER_ONLY;

        for (AbilityCueInstance cueInstance : matched) {
            InstantiationScope scope = cueScopeOf(cueInstance);

            if (scope.compareTo(widest) > 0) widest = scope;
        }

        sendToScope(widest, new ClientBoundCancelAbilityCue(owner.getOriginal().getId(), query));

        for (AbilityCueInstance cueInstance : matched) {
            terminateCue(cueInstance, true);
        }

        return matched.size();
    }

    /// Who holds `cueInstance` besides the server.
    private static InstantiationScope cueScopeOf(final AbilityCueInstance cueInstance) {
        AbilityInstance source = cueInstance.getSource();

        if (source == null) return InstantiationScope.EVERYWHERE;

        return source.getAbility() == null ? InstantiationScope.SERVER_ONLY : scopeOf(source.getAbility());
    }

    /// Every live play, in the order they started.
    public List<AbilityCueInstance> getCueInstances() {
        return Collections.unmodifiableList(cueInstances);
    }

    /// Ends one play, firing exactly one of [AbilityCue.Behavior#onCanceled] or
    /// [AbilityCue.Behavior#onEnd]. The instance is invalidated and delisted before the callback
    /// runs.
    private void terminateCue(final AbilityCueInstance cueInstance, final boolean cancelled) {
        if (!cueInstance.isValid()) return;

        cueInstance.invalidate();
        cueInstances.remove(cueInstance);

        // The server only keeps the books.
        if (owner.isLogicalServer()) return;

        if (cancelled) {
            cueInstance.getCue().getBehavior().onCanceled(this, cueInstance);
        } else {
            cueInstance.getCue().getBehavior().onEnd(this, cueInstance);
        }
    }

    /// Cancels `abilityInstance`'s plays that declared [AbilityCueInstance#endsWithAbility].
    private void stopCuesOf(final AbilityInstance abilityInstance, final boolean includeDetached) {
        for (AbilityCueInstance cueInstance : new ArrayList<>(cueInstances)) {
            if (cueInstance.getSource() != abilityInstance) continue;
            if (!includeDetached && !cueInstance.endsWithAbility()) continue;

            terminateCue(cueInstance, true);
        }
    }

    /// Counts every live play down one tick, ending the ones that ran out.
    private void tickCues() {
        for (AbilityCueInstance cueInstance : new ArrayList<>(cueInstances)) {
            if (!cueInstance.isValid()) continue;

            if (!owner.isLogicalServer()) cueInstance.getCue().getBehavior().tick(this, cueInstance);

            if (!cueInstance.isValid()) continue;

            if (cueInstance.tickDuration()) terminateCue(cueInstance, false);
        }
    }

    // ***************************************************************
    // Tags
    // ***************************************************************

    /// Grants `tag` once. The owner carries it until every grant is released.
    @ScriptFunction(label = "Add Ability Tag", category = "Ability/Tag")
    public void addAbilityTag(final AbilityTag tag) {
        boolean granted = ownerTags.merge(tag, 1, Integer::sum) == 1;

        // Only the transition is an event.
        if (!granted) return;

        syncTag(tag, true);
        fireTriggers(tag, SystematicAbility.Trigger.Source.ON_TAG_ADDED);
    }

    /// Drops every grant of `tag` at once.
    public void clearAbilityTag(final AbilityTag tag) {
        if (ownerTags.remove(tag) == null) return;

        syncTag(tag, false);
        fireTriggers(tag, SystematicAbility.Trigger.Source.ON_TAG_REMOVED);
    }

    /// Sends `tracker` everything it needs to mirror this owner: the tags it carries, each filled
    /// slot with its current activation and state, then every live effect application.
    @ApiStatus.Internal
    public void sendSnapshotTo(final ServerPlayer tracker) {
        if (!owner.isLogicalServer()) return;

        int entityId = owner.getOriginal().getId();

        // Tags first: a client validating against them must not see an ability before its tags.
        for (AbilityTag tag : ownerTags.keySet()) {
            NetworkingHelper.getInstance()
                .sendToClient(tracker, new ClientBoundAbilityTagSync(entityId, tag, true));
        }

        for (Map.Entry<AbilitySlot, AbilityInstance> entry : instances.entrySet()) {
            Ability<?> ability = entry.getValue().getAbility();

            if (ability == null || ability.getBlueprintKey() == null) continue;
            if (!ability.getInstantiationScope().reachesTrackers()) continue;

            NetworkingHelper.getInstance().sendToClient(tracker, new ClientBoundAbilityEquipmentSync(
                entityId,
                entry.getKey(),
                Optional.of(ability.getBlueprintKey()),
                entry.getValue().isActivated()
            ));

            byte[] state = entry.getValue().isStateSyncFailed()
                ? null
                : encodeState(entry.getValue(), entry.getValue().getState());

            if (state != null && state.length > 0) {
                NetworkingHelper.getInstance()
                    .sendToClient(tracker, new ClientBoundAbilityStateSync(entityId, entry.getKey(), state));
            }
        }

        // Last: an application names its ability by slot, which the loop above filled.
        for (AbilityEffectInstance instance : snapshotEffects()) {
            AbilityInstance source = instance.getSource();

            if (source != null
                && (source.getAbility() == null || !scopeOf(source.getAbility()).reachesTrackers())) {
                continue;
            }

            NetworkingHelper.getInstance()
                .sendToClient(tracker, ClientBoundAbilityEffectSync.of(entityId, instance, false));
        }
    }

    /// Sends `payload` to whichever clients instantiate `ability`.
    ///
    /// An ability Java built has no blueprint key, so it cannot be named on the wire and never
    /// leaves the server.
    private void sendScoped(final Ability<?> ability, final CustomPacketPayload payload) {
        sendToScope(scopeOf(ability), payload);
    }

    /// An ability Java built has no blueprint key, so it cannot be named on the wire and never
    /// leaves the server.
    private static InstantiationScope scopeOf(final Ability<?> ability) {
        return ability.getBlueprintKey() == null
            ? InstantiationScope.SERVER_ONLY
            : ability.getInstantiationScope();
    }

    private void sendToScope(final InstantiationScope scope, final CustomPacketPayload payload) {
        if (!owner.isLogicalServer()) return;

        if (scope.reachesTrackers()) {
            NetworkingHelper.getInstance()
                .sendToPlayersTrackingEntityWithSelfIfPlayer(owner.getOriginal(), payload);
        } else if (scope.reachesOwner() && owner.getOriginal() instanceof ServerPlayer serverPlayer) {
            NetworkingHelper.getInstance().sendToClient(serverPlayer, payload);
        }
    }

    /// Tells the tracking clients `slot` now holds `ability`, or is empty when it is null.
    private void syncEquipment(
        final AbilitySlot slot,
        final Ability<?> ability,
        final boolean activated
    ) {
        sendScoped(ability, new ClientBoundAbilityEquipmentSync(
            owner.getOriginal().getId(),
            slot,
            ability.getBlueprintKey() == null ? Optional.empty() : Optional.of(ability.getBlueprintKey()),
            activated
        ));
    }

    /// Tells the tracking clients how `slot`'s activation changed.
    private void syncLifecycle(
        final AbilitySlot slot,
        final AbilityInstance instance,
        final ClientBoundAbilityLifecycle.Transition transition
    ) {
        Ability<?> ability = instance.getAbility();

        if (ability == null) return;

        // The client runs its mirrored callback against the state it holds.
        flushState(slot, instance);

        sendScoped(ability, new ClientBoundAbilityLifecycle(owner.getOriginal().getId(), slot, transition));
    }

    /// Tells the tracking clients about one tag transition.
    private void syncTag(final AbilityTag tag, final boolean granted) {
        if (!owner.isLogicalServer()) return;

        NetworkingHelper.getInstance().sendToPlayersTrackingEntityWithSelfIfPlayer(
            owner.getOriginal(), new ClientBoundAbilityTagSync(owner.getOriginal().getId(), tag, granted)
        );
    }

    /// Sends `instance`'s state when a field its client bound codec carries moved since the last
    /// send, then moves the baseline up to it.
    private void flushState(final AbilitySlot slot, final AbilityInstance instance) {
        if (!owner.isLogicalServer()) return;

        Ability<?> ability = instance.getAbility();
        AbilityState state = instance.getState();

        if (ability == null || state.clientBoundCodec() == null || instance.isStateSyncFailed()) return;
        if (!state.checkDirtyToSend(instance.getPrevState())) return;

        byte[] encoded = encodeState(instance, state);

        if (encoded == null) return;

        sendScoped(ability, new ClientBoundAbilityStateSync(owner.getOriginal().getId(), slot, encoded));

        instance.getPrevState().copyState(state);
    }

    /// `state` through its client bound codec, or null once it has thrown.
    ///
    /// A third-party state can return a codec typed for another class, which throws from inside the
    /// codec. This runs at the end of the owner's tick, where an unguarded throw ends the tick.
    private @Nullable byte[] encodeState(final AbilityInstance instance, final AbilityState state) {
        try {
            return AbilityState.encode(
                state, PacketFlow.CLIENTBOUND, owner.getOriginal().level().registryAccess()
            );
        } catch (RuntimeException exception) {
            instance.setStateSyncFailed(true);
            Akythera.LOGGER.error(
                "Ability state of {} failed to encode and stops syncing on this instance",
                instance.getAbility() == null ? null : instance.getAbility().getBlueprintKey(), exception
            );

            return null;
        }
    }

    /// Tells the clients that instantiate `instance`'s ability where it stands, or that it ended.
    ///
    /// An application no ability claimed, or whose ability has already left its slot, goes to
    /// everyone tracking the owner: there is no scope left to read, and a client holding no copy
    /// ignores it.
    private void syncEffect(final AbilityEffectInstance instance, final boolean ended) {
        AbilityInstance source = instance.getSource();
        InstantiationScope scope = source == null || source.getAbility() == null
            ? InstantiationScope.EVERYWHERE
            : scopeOf(source.getAbility());

        sendToScope(scope, ClientBoundAbilityEffectSync.of(owner.getOriginal().getId(), instance, ended));
    }

    /// Tells the clients an application moved outside this component, as a spent charge does.
    @ApiStatus.Internal
    public void syncEffect(final AbilityEffectInstance instance) {
        syncEffect(instance, false);
    }

    /// Releases one grant of `tag`.
    ///
    /// @return whether that was the last grant
    @ScriptFunction(label = "Remove Ability Tag", category = "Ability/Tag")
    public boolean removeAbilityTag(final AbilityTag tag) {
        Integer count = ownerTags.get(tag);

        if (count == null) return false;

        if (count <= 1) {
            ownerTags.remove(tag);
            syncTag(tag, false);
            fireTriggers(tag, SystematicAbility.Trigger.Source.ON_TAG_REMOVED);

            return true;
        }

        ownerTags.put(tag, count - 1);

        return false;
    }

    /// Raises `tag` as an event, activating every equipped ability that triggers on it.
    ///
    /// Nothing is added to the owner's tag container.
    @ScriptFunction(label = "Trigger Ability Event", category = "Ability/Tag", desc = "Raises the tag as an event, activating every ability that triggers on it.")
    public void triggerEvent(final AbilityTag tag) {
        fireTriggers(tag, SystematicAbility.Trigger.Source.EVENT);
    }

    /// Activates every equipped ability whose triggers name `tag` from `source`. Server side
    /// only.
    private void fireTriggers(final AbilityTag tag, final SystematicAbility.Trigger.Source source) {
        if (!owner.isLogicalServer()) return;

        if (triggerDepth >= MAX_TRIGGER_DEPTH) {
            Akythera.LOGGER.warn(
                "Ability trigger chain on {} passed {} deep at {}, and was cut",
                owner.getOriginal().getName().getString(), MAX_TRIGGER_DEPTH, tag
            );

            return;
        }

        triggerDepth++;

        try {
            for (Map.Entry<AbilitySlot, AbilityInstance> entry : new ArrayList<>(instances.entrySet())) {
                if (!(entry.getValue().getAbility() instanceof SystematicAbility<?> systematic)) continue;

                for (SystematicAbility.Trigger trigger : systematic.getTriggers()) {
                    if (trigger.source() != source || !trigger.triggerTag().matches(tag)) continue;

                    activateAbility(entry.getKey());

                    break;
                }
            }
        } finally {
            triggerDepth--;
        }
    }

    /// Whether the owner carries `query` or anything beneath it: asking for `activation` answers
    /// true while the owner holds `activation/combo_attack`.
    @ScriptFunction(pure = true, label = "Has Ability Tag", category = "Ability/Tag", desc = "True while the owner carries the tag or anything beneath it.")
    public boolean hasAbilityTag(final AbilityTag query) {
        for (AbilityTag held : ownerTags.keySet()) {
            if (query.matches(held)) return true;
        }

        return false;
    }

    /// How many live grants `tag` has, counting only that exact tag.
    @ScriptFunction(pure = true, label = "Ability Tag Count", category = "Ability/Tag")
    public int getAbilityTagCount(final AbilityTag tag) {
        return ownerTags.getOrDefault(tag, 0);
    }

    /// The tags the owner carries, each mapped to its grant count.
    public Map<AbilityTag, Integer> getOwnerTags() {
        return Collections.unmodifiableMap(ownerTags);
    }

    public boolean isTracing() {
        return tracing;
    }

    public void setTracing(final boolean tracing) {
        this.tracing = tracing;
    }

    // ***************************************************************
    // Client mirrors
    // ***************************************************************

    /// Counts the client's copies down one tick, under the rules the server counts by.
    private void tickMirroredEffects() {
        for (AbilityEffectInstance instance : snapshotEffects()) {
            instance.tickMirror(
                instance.getRole() == AbilityEffectInstance.Role.COOLDOWN && isCooldownHeld(instance)
            );
        }
    }

    /// Creates or overwrites the client's copy of the server's application `modifierIndex` of
    /// `effect`.
    ///
    /// Never applied: attribute changes and resources reach a client through their own sync.
    @ApiStatus.Internal
    public void mirrorEffect(
        final AbilityEffect effect,
        final int modifierIndex,
        final AbilityEffectInstance.Role role,
        final @Nullable AbilityInstance source,
        final int remainingTicks,
        final int stacks
    ) {
        if (owner.isLogicalServer()) return;

        List<AbilityEffectInstance> live = effectInstances.computeIfAbsent(
            effect.getId(), id -> new ArrayList<>()
        );
        AbilityEffectInstance mirrored = null;

        for (AbilityEffectInstance held : live) {
            if (held.getModifierIndex() == modifierIndex) mirrored = held;
        }

        if (mirrored == null) {
            mirrored = new AbilityEffectInstance(effect, role, modifierIndex);

            if (source != null) mirrored.bindSource(source);

            live.add(mirrored);
        }

        mirrored.mirror(remainingTicks, stacks);
    }

    /// Drops the client's copy of application `modifierIndex` of `effectId`.
    @ApiStatus.Internal
    public void dropMirroredEffect(final Identifier effectId, final int modifierIndex) {
        if (owner.isLogicalServer()) return;

        List<AbilityEffectInstance> live = effectInstances.get(effectId);

        if (live == null) return;

        live.removeIf(held -> held.getModifierIndex() == modifierIndex);

        if (live.isEmpty()) effectInstances.remove(effectId);
    }

    /// Starts the play the server sent and runs its behavior.
    @ApiStatus.Internal
    public void startMirroredCue(
        final AbilityCue cue,
        final @Nullable AbilityInstance source,
        final @Nullable AbilityState abilityState,
        final boolean endsWithAbility
    ) {
        if (owner.isLogicalServer()) return;

        AbilityCueInstance cueInstance = new AbilityCueInstance(cue, source, endsWithAbility);
        cueInstances.add(cueInstance);
        cue.getBehavior().onExecute(this, cueInstance, abilityState);
    }

    /// Cancels the plays the server cancelled by `query`, by the same match it ran.
    @ApiStatus.Internal
    public void cancelMirroredCues(final AbilityTag query) {
        if (owner.isLogicalServer()) return;

        for (AbilityCueInstance cueInstance : new ArrayList<>(cueInstances)) {
            if (cueInstance.isValid() && cueInstance.getCue().hasTag(query)) terminateCue(cueInstance, true);
        }
    }

    /// Ends the plays `abilityInstance` started that end with it, as the server did before it sent
    /// the deactivation.
    @ApiStatus.Internal
    public void stopMirroredCuesOf(final AbilityInstance abilityInstance) {
        if (owner.isLogicalServer()) return;

        stopCuesOf(abilityInstance, false);
    }

    // ***************************************************************
    // [TickableDynamicComponent]
    // ***************************************************************

    @Override
    public void tick() {
        // Both sides count a play's timer down; only the client runs its behavior.
        tickCues();

        if (!owner.isLogicalServer()) {
            tickMirroredEffects();

            return;
        }

        // A no-op once the owner has joined, and a safety net for any add path that skips the join.
        equipPendingLoadout();
        tickEffects();
        tickCosts();
        tickHoldCounts();

        runningTasks.addAll(pendingTasks);
        pendingTasks.clear();

        for (AbilityTaskHandle handle : new ArrayList<>(runningTasks)) {
            // A task earlier in this pass may have ended this one.
            if (!handle.isValid()) continue;

            AbilityTask task = handle.getTask();
            task.tick(this);

            if (!handle.isValid()) continue;

            if (!task.canContinue(this)) {
                terminate(handle, true);

                continue;
            }

            if (task.isEnd()) {
                terminate(handle, false);
            }
        }

        // Last, so every write this tick made leaves on this tick.
        instances.forEach(this::flushState);
    }

    @Override
    public void frameUpdate(final float frameTime, final float partialTick) {

    }

    // ***************************************************************
    // [DynamicPatchComponent]
    // ***************************************************************

    @Override
    public LivingEntityPatch<?> getOwner() {
        return owner;
    }

    /// Rebinds to `newOwner` and keeps everything.
    ///
    /// Renewal swaps the patch wrapper, not the entity, so the attribute modifiers, banked charges
    /// and running tasks all still belong to the same entity.
    @Override
    public AbilitySystemComponent getRenewed(final LivingEntityPatch<?> newOwner) {
        owner = newOwner;

        return this;
    }
}
