package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityConstructEvent;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.event.entity.EntityTrackingEvent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.Set;

/// Stands in for `AbilitySystemComponentBlueprint` until that lands: one slot, one empty ability
/// and an unconditional attachment.
///
/// Must be used only for the development stage and removed on releases.
public final class AbilityDebugScaffolding {
    private AbilityDebugScaffolding() {}

    public enum DebugSlotGroup implements AbilitySlotGroup {
        COMMON;

        final int id;

        DebugSlotGroup() {
            id = AbilitySlotGroup.ENUM_MANAGER.assign(this);
        }

        @Override
        public int universalOrdinal() {
            return id;
        }
    }

    public enum DebugSlot implements AbilitySlot {
        PRIMARY,
        SECONDARY;

        final int id;

        DebugSlot() {
            id = AbilitySlot.ENUM_MANAGER.assign(this);
        }

        @Override
        public AbilitySlotGroup group() {
            return DebugSlotGroup.COMMON;
        }

        @Override
        public int universalOrdinal() {
            return id;
        }
    }

    /// Tag paths with a hierarchy in them, to exercise the path-prefix match in
    /// [AbilityTag#matches].
    public enum DebugTag implements AbilityTag {
        STANCE("debug/stance"),
        STANCE_HEAVY("debug/stance/heavy"),
        READY("debug/ready"),
        STUNNED("debug/stunned"),
        CHANNELING("debug/channeling"),
        SIGNAL("debug/signal");

        final int id;

        DebugTag(final String path) {
            id = AbilityTag.ENUM_MANAGER.assign(this, path);
        }

        @Override
        public int universalOrdinal() {
            return id;
        }

        @Override
        public String toString() {
            return path();
        }
    }

    /// One test ability per tag rule.
    public enum TaggedScenario {
        /// Holds `debug/stance/heavy` as its own id tag and grants `debug/channeling` while it runs.
        HOLDER(new SystematicAbility.Settings(
            ActivationType.DURATION,
            List.of(),
            null,
            TestSystematicAbility.LONG_DURATION,
            null,
            new SystematicAbility.Tags(
                Set.of(DebugTag.STANCE_HEAVY),
                Set.of(),
                Set.of(),
                Set.of(DebugTag.CHANNELING),
                Set.of(),
                Set.of()
            ),
            List.of()
        )),

        /// Activates only while the owner carries `debug/ready`.
        REQUIRER(taggedInstant(new SystematicAbility.Tags(
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(DebugTag.READY), Set.of()
        ))),

        /// Refuses to activate while the owner carries `debug/stunned`.
        STUNNABLE(taggedInstant(new SystematicAbility.Tags(
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(DebugTag.STUNNED)
        ))),

        /// Cancels anything whose id tags sit under `debug/stance`.
        CANCELLER(taggedInstant(new SystematicAbility.Tags(
            Set.of(), Set.of(DebugTag.STANCE), Set.of(), Set.of(), Set.of(), Set.of()
        ))),

        /// While it runs, refuses anything whose id tags sit under `debug/stance`.
        BLOCKER(new SystematicAbility.Settings(
            ActivationType.DURATION,
            List.of(),
            null,
            TestSystematicAbility.LONG_DURATION,
            null,
            new SystematicAbility.Tags(
                Set.of(), Set.of(), Set.of(DebugTag.STANCE), Set.of(), Set.of(), Set.of()
            ),
            List.of()
        )),

        /// Activates when `debug/signal` is raised as an event.
        ON_EVENT(new SystematicAbility.Settings(
            ActivationType.INSTANT,
            List.of(),
            null,
            null,
            null,
            SystematicAbility.Tags.NONE,
            List.of(new SystematicAbility.Trigger(
                DebugTag.SIGNAL, SystematicAbility.Trigger.Source.EVENT
            ))
        )),

        /// Activates when `debug/signal` is released from the owner.
        ON_TAG_REMOVED(new SystematicAbility.Settings(
            ActivationType.INSTANT,
            List.of(),
            null,
            null,
            null,
            SystematicAbility.Tags.NONE,
            List.of(new SystematicAbility.Trigger(
                DebugTag.SIGNAL, SystematicAbility.Trigger.Source.ON_TAG_REMOVED
            ))
        )),

        /// Activates when `debug/signal` is granted to the owner.
        ON_TAG_ADDED(new SystematicAbility.Settings(
            ActivationType.INSTANT,
            List.of(),
            null,
            null,
            null,
            SystematicAbility.Tags.NONE,
            List.of(new SystematicAbility.Trigger(
                DebugTag.SIGNAL, SystematicAbility.Trigger.Source.ON_TAG_ADDED
            ))
        ));

        private final SystematicAbility.Settings settings;

        TaggedScenario(final SystematicAbility.Settings settings) {
            this.settings = settings;
        }

        public SystematicAbility<AbilityState> build() {
            return new TaggedTestAbility(settings, name());
        }

        private static SystematicAbility.Settings taggedInstant(final SystematicAbility.Tags tags) {
            return new SystematicAbility.Settings(
                ActivationType.INSTANT, List.of(), null, null, null, tags, List.of()
            );
        }
    }

    /// A [SystematicAbility] built straight from a [TaggedScenario], carrying its name.
    public static final class TaggedTestAbility extends SystematicAbility<AbilityState> {
        private final String label;

        public TaggedTestAbility(final Settings settings, final String label) {
            super(settings);

            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /// A cue that reports every lifecycle step it receives.
    public static final class TracingCueBehavior implements AbilityCue.Behavior {
        private final String label;

        public TracingCueBehavior(final String label) {
            this.label = label;
        }

        @Override
        public void onExecute(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance,
            final @Nullable AbilityState abilityState
        ) {
            Akythera.LOGGER.info("cue {} executed ({} ticks)", label, cueInstance.getRemainingTicks());
        }

        @Override
        public void onEnd(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance
        ) {
            Akythera.LOGGER.info("cue {} ended", label);
        }

        @Override
        public void onCanceled(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance
        ) {
            Akythera.LOGGER.info("cue {} canceled at {} ticks", label, cueInstance.getRemainingTicks());
        }
    }

    /// Held in a nested class: these name [DebugTag]s, which cannot be built until
    /// [#registerEnums] has run.
    public static final class DebugCues {
        /// Runs three seconds, tagged `debug/stance`.
        public static final AbilityCue TIMED = new AbilityCue(
            Akythera.identifier("debug_timed_cue"),
            Set.of(DebugTag.STANCE),
            true,
            60,
            new TracingCueBehavior("timed")
        );

        /// Runs until cancelled, tagged `debug/signal`.
        public static final AbilityCue ENDLESS = new AbilityCue(
            Akythera.identifier("debug_endless_cue"),
            Set.of(DebugTag.SIGNAL),
            true,
            0,
            new TracingCueBehavior("endless")
        );

        private DebugCues() {}
    }

    /// Registers the ability constructors the test data pack names, for as long as the scaffolding
    /// is enabled.
    @ApiStatus.Internal
    public static void registerScriptFunctions() {
        AkytheraScriptFunctions.AKYTHERA_REGISTRAR.register("probe_stateful_ability_ctor", () ->
            ScriptModularizeHelper.makeCustomScript(
                (Function<Holder.Reference, ProbeStatefulAbility>) ProbeStatefulAbility::new,
                FunctionSignature.create(ProbeStatefulAbility.class, List.of("blueprint"), Holder.Reference.class),
                "Probe Stateful Ability",
                "Ability",
                "Debug scaffolding: a Systematic Ability whose state counts activations and syncs them",
                true,
                ScriptFunction.Usage.ABILITY_CTOR
            )
        );

        AkytheraScriptFunctions.AKYTHERA_REGISTRAR.register("wait_ticks_task_ctor", () ->
            ScriptModularizeHelper.makeCustomScript(
                (BiFunction<Integer, Runnable, WaitTicksTask>)
                    (duration, onCompleted) -> new WaitTicksTask(duration, null, onCompleted),
                FunctionSignature.create(
                    WaitTicksTask.class, List.of("duration", "onCompleted"), Integer.class, Runnable.class
                ),
                "Wait Ticks Task",
                "Ability/Task",
                "Debug scaffolding: waits a number of ticks, then runs its completed callback",
                true,
                ScriptFunction.Usage.ABILITY_TASK
            )
        );

        AkytheraScriptFunctions.AKYTHERA_REGISTRAR.register("broken_state_ability_ctor", () ->
            ScriptModularizeHelper.makeCustomScript(
                (Function<Holder.Reference, BrokenStateAbility>) BrokenStateAbility::new,
                FunctionSignature.create(BrokenStateAbility.class, List.of("blueprint"), Holder.Reference.class),
                "Broken State Ability",
                "Ability",
                "Debug scaffolding: a Systematic Ability whose state codec throws on every encode",
                true,
                ScriptFunction.Usage.ABILITY_CTOR
            )
        );
    }

    /// Hands the debug cues to [AbilityCues], which every play is checked against.
    @ApiStatus.Internal
    public static void registerCues() {
        AbilityCues.register(DebugCues.TIMED);
        AbilityCues.register(DebugCues.ENDLESS);
    }

    /// A state that actually travels, so the state sync has something to carry. Counts the
    /// activations this instance has seen.
    public static final class ProbeAbilityState implements AbilityState {
        private static final StreamCodec<RegistryFriendlyByteBuf, ProbeAbilityState> STREAM_CODEC =
            StreamCodec.of(
                (buffer, state) -> ByteBufCodecs.VAR_INT.encode(buffer, state.activations),
                buffer -> {
                    ProbeAbilityState state = new ProbeAbilityState();
                    state.activations = ByteBufCodecs.VAR_INT.decode(buffer);

                    return state;
                }
            );

        private int activations;

        public int getActivations() {
            return activations;
        }

        void bump() {
            activations++;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        public boolean checkDirtyToSend(final AbilityState previous) {
            return !(previous instanceof ProbeAbilityState sent) || sent.activations != activations;
        }

        @Override
        public void copyState(final AbilityState from) {
            if (from instanceof ProbeAbilityState source) activations = source.activations;
        }

        @Override
        public void decodeInto(
            final PacketFlow flow,
            final RegistryFriendlyByteBuf buffer,
            final Level level
        ) {
            activations = ByteBufCodecs.VAR_INT.decode(buffer);
        }
    }

    /// A [SystematicAbility] carrying a [ProbeAbilityState], reachable from a data pack so its
    /// state has a blueprint key to travel under.
    public static final class ProbeStatefulAbility extends SystematicAbility<ProbeAbilityState> {
        public ProbeStatefulAbility(final Holder.Reference<AbilityBlueprint> blueprint) {
            super(blueprint);
        }

        @Override
        public ProbeAbilityState createState() {
            return new ProbeAbilityState();
        }

        @Override
        public void onActivated(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityInstance abilityInstance,
            final ProbeAbilityState abilityState
        ) {
            super.onActivated(abilitySystemComponent, abilityInstance, abilityState);

            if (!abilitySystemComponent.getOwner().isLogicalServer()) {
                Akythera.LOGGER.info("client probe state reads {} activations", abilityState.getActivations());

                return;
            }

            abilityState.bump();
        }
    }

    /// A state whose codec throws, which is what a third-party state does when it hands back a
    /// codec typed for another class. Proves the flush latches instead of taking the tick with it.
    public static final class ThrowingAbilityState implements AbilityState {
        private static final StreamCodec<RegistryFriendlyByteBuf, ThrowingAbilityState> STREAM_CODEC =
            StreamCodec.of(
                (buffer, state) -> {
                    throw new ClassCastException("debug_broken_state");
                },
                buffer -> new ThrowingAbilityState()
            );

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        public boolean checkDirtyToSend(final AbilityState previous) {
            return true;
        }

        @Override
        public void copyState(final AbilityState from) {

        }

        @Override
        public void decodeInto(
            final PacketFlow flow,
            final RegistryFriendlyByteBuf buffer,
            final Level level
        ) {

        }
    }

    /// Carries a [ThrowingAbilityState].
    public static final class BrokenStateAbility extends SystematicAbility<ThrowingAbilityState> {
        public BrokenStateAbility(final Holder.Reference<AbilityBlueprint> blueprint) {
            super(blueprint);
        }

        @Override
        public ThrowingAbilityState createState() {
            return new ThrowingAbilityState();
        }
    }

    /// An ability whose callbacks do nothing.
    public static final class TestAbility extends Ability<AbilityState> {
        @Override
        public void onActivated(final AbilitySystemComponent c, final AbilityInstance i, final AbilityState s) {}

        @Override
        public void onRecast(final AbilitySystemComponent c, final AbilityInstance i, final AbilityState s) {}

        @Override
        public void onCanceled(final AbilitySystemComponent c, final AbilityInstance i, final AbilityState s) {}

        @Override
        public void onFinished(final AbilitySystemComponent c, final AbilityInstance i, final AbilityState s) {}
    }

    /// The worked example from the design document: a 10 second ability on a 5 second recharge
    /// that banks up to four charges. Neither effect carries modifiers.
    public static final class TestSystematicAbility extends SystematicAbility<AbilityState> {
        public static final AbilityEffect COOLDOWN = new AbilityEffect(
            Akythera.identifier("debug_cooldown"),
            List.of(),
            AbilityEffect.ApplicationType.DURATION,
            100,
            4
        );

        /// Long enough that a tag test can act while it is still running.
        public static final AbilityEffect LONG_DURATION = new AbilityEffect(
            Akythera.identifier("debug_long_duration"),
            List.of(),
            AbilityEffect.ApplicationType.DURATION,
            1200,
            1
        );

        public static final AbilityEffect DURATION = new AbilityEffect(
            Akythera.identifier("debug_duration"),
            List.of(),
            AbilityEffect.ApplicationType.DURATION,
            200,
            1
        );

        public TestSystematicAbility() {
            super(new Settings(ActivationType.DURATION, List.of(), COOLDOWN, DURATION));
        }
    }

    /// Costs four health on activation, authored positive.
    public static final class TestCostAbility extends SystematicAbility<AbilityState> {
        public static final class Cost {
            public static final AbilityEffect HEALTH = new AbilityEffect(
                Akythera.identifier("debug_health_cost"),
                List.of(new AbilityEffect.Modifier<>(
                    AbilityEffect.ModifierType.RESOURCE,
                    new AbilityEffect.AbilityResourceSpec(
                        AbilityResources.getOrThrow(Akythera.identifier("health")), 4.0F
                    )
                )),
                AbilityEffect.ApplicationType.INSTANT,
                0,
                1
            );

            private Cost() {}
        }

        public TestCostAbility() {
            super(new Settings(ActivationType.INSTANT, List.of(Cost.HEALTH), null, null));
        }
    }

    /// Two health costs of twelve. Each passes alone against twenty health; the pair must not.
    public static final class TestDoubleCostAbility extends SystematicAbility<AbilityState> {
        private static AbilityEffect healthCost(final String id) {
            return new AbilityEffect(
                Akythera.identifier(id),
                List.of(new AbilityEffect.Modifier<>(
                    AbilityEffect.ModifierType.RESOURCE,
                    new AbilityEffect.AbilityResourceSpec(
                        AbilityResources.getOrThrow(Akythera.identifier("health")), 12.0F
                    )
                )),
                AbilityEffect.ApplicationType.INSTANT,
                0,
                1
            );
        }

        public TestDoubleCostAbility() {
            super(new Settings(
                ActivationType.INSTANT,
                List.of(healthCost("debug_cost_a"), healthCost("debug_cost_b")),
                null,
                null
            ));
        }
    }

    /// A second ability on the same [TestSystematicAbility#COOLDOWN] object, to watch that the two
    /// recharge separately.
    public static final class TestSharedCooldownAbility extends SystematicAbility<AbilityState> {
        public TestSharedCooldownAbility() {
            super(new Settings(
                ActivationType.INSTANT,
                List.of(),
                TestSystematicAbility.COOLDOWN,
                null
            ));
        }
    }

    /// An Instant ability on the same 5 second recharge.
    public static final class TestInstantAbility extends SystematicAbility<AbilityState> {
        public TestInstantAbility() {
            super(new Settings(
                ActivationType.INSTANT,
                List.of(),
                TestSystematicAbility.COOLDOWN,
                null
            ));
        }
    }

    /// Counts down and ends, reporting each transition.
    public static final class WaitTicksTask extends AbilityTask {
        private final int duration;

        private int elapsed;

        public WaitTicksTask(final int duration) {
            this.duration = duration;
        }

        public WaitTicksTask(
            final int duration,
            final @Nullable Runnable onInterrupted,
            final @Nullable Runnable onCompleted
        ) {
            super(onInterrupted, onCompleted);

            this.duration = duration;
        }

        @Override
        public void onStart(final AbilitySystemComponent abilitySystemComponent) {
            AbilityTrace.log(abilitySystemComponent, getOwner(), "task started (" + duration + " ticks)");
        }

        @Override
        public void tick(final AbilitySystemComponent abilitySystemComponent) {
            elapsed++;
        }

        @Override
        public boolean isEnd() {
            return elapsed >= duration;
        }

        @Override
        public void onEnd(final AbilitySystemComponent abilitySystemComponent) {
            super.onEnd(abilitySystemComponent);

            AbilityTrace.log(abilitySystemComponent, getOwner(), "task ended after " + elapsed + " ticks");
        }

        @Override
        public void onCanceled(final AbilitySystemComponent abilitySystemComponent) {
            super.onCanceled(abilitySystemComponent);

            AbilityTrace.log(abilitySystemComponent, getOwner(), "task canceled at " + elapsed + " ticks");
        }
    }

    @ApiStatus.Internal
    public static void registerEnums() {
        AbilitySlotGroup.ENUM_MANAGER.registerEnumCls(Akythera.MODID, DebugSlotGroup.class);
        AbilitySlot.ENUM_MANAGER.registerEnumCls(Akythera.MODID, DebugSlot.class);
        AbilityTag.ENUM_MANAGER.registerEnumCls(Akythera.MODID, DebugTag.class);
    }

    @ApiStatus.Internal
    public static void loadEnums() {
        AbilitySlotGroup.ENUM_MANAGER.loadEnum();
        AbilitySlot.ENUM_MANAGER.loadEnum();
        AbilityTag.ENUM_MANAGER.loadEnum();
    }

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Entity.CONSTRUCTED.registerEvent(AbilityDebugScaffolding::attach);
        BuiltInEventHooks.Entity.SPAWNED.registerEvent(AbilityDebugScaffolding::equipLoadout);
        BuiltInEventHooks.Entity.TRACKING_START.registerEvent(AbilityDebugScaffolding::sendSnapshot);
    }

    /// Equips the owner's declared loadout as it joins a level, ahead of any tracker's snapshot.
    private static void equipLoadout(final EntitySpawnEvent event) {
        AbilitySystemComponent component =
            event.getEntityPatch().getComponent(AbilitySystemComponent.class);

        if (component != null) component.equipPendingLoadout();
    }

    /// Hands a new tracker the owner's ability state.
    ///
    /// A global subscriber, not a per-patch listener: the component survives patch renewal while
    /// the patch's own listener set is rebuilt.
    private static void sendSnapshot(final EntityTrackingEvent.Start event) {
        AbilitySystemComponent component =
            event.getEntityPatch().getComponent(AbilitySystemComponent.class);

        if (component != null) component.sendSnapshotTo(event.getTracker());
    }

    /// Gives players and zombies a [LivingEntityPatch] when no data pack declares one for them.
    /// Akythera registers no patch provider and ships no entity patch data.
    @ApiStatus.Internal
    public static void registerTestPatchProvider() {
        ComponentsFactory.getImpl().registerEntityPatchProvider(
            EntityTypes.ZOMBIE,
            (Zombie zombie) -> {
                EntityPatch<Zombie> declared = ComponentsFactory.getImpl().getDataPackEntityPatch(zombie);

                return declared != null ? declared : new LivingEntityPatch<>(zombie);
            }
        );

        ComponentsFactory.getImpl().registerEntityPatchProvider(
            EntityTypes.PLAYER,
            (Player player) -> {
                EntityPatch<Player> declared = ComponentsFactory.getImpl().getDataPackEntityPatch(player);

                return declared != null
                    ? declared
                    : new PlayerPatch((Holder.Reference<EntityPatchBlueprint>)null, player);
            }
        );
    }

    /// Attaches an [AbilitySystemComponent] to every living entity that does not already carry one.
    private static void attach(final EntityConstructEvent event) {
        if (!(event.getEntityPatch() instanceof LivingEntityPatch<?> livingPatch)) return;
        if (!(livingPatch.getOriginal() instanceof LivingEntity)) return;
        if (event.getComponent(AbilitySystemComponent.COMPONENT_NAME) != null) return;

        event.addComponent(AbilitySystemComponent.COMPONENT_NAME, new AbilitySystemComponent(livingPatch));
    }
}
