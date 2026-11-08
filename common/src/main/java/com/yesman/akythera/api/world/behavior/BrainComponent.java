package com.yesman.akythera.api.world.behavior;

import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/// Runs a behavior tree for a living entity on the logical server, in place of its vanilla AI.
@ScriptType(autoRegister = false)
public final class BrainComponent implements DynamicPatchComponent<EntityPatch<?>>, UniqueComponent {
    /// Counts the data pack registry reloads. A brain built before the last one builds its tree again.
    private static int registryGeneration;

    private EntityPatch<?> owner;

    /// Entity types the owner treats as hostile before its vanilla goals are asked.
    private final List<TagKey<EntityType<?>>> hostileTypes;

    /// Entity types the owner treats as friendly before its vanilla goals are asked.
    private final List<TagKey<EntityType<?>>> friendlyTypes;

    /// The selected tree, or null for none.
    private @Nullable ResourceKey<BehaviorTreeBlueprint> behaviorTree;

    /// The selected tree built for this brain. Null until the owner joins a level, and for a missing or broken tree.
    private @Nullable BehaviorTreeInstance treeInstance;

    /// The [#registryGeneration] [#treeInstance] was built in.
    private int builtGeneration;

    /// Whether the brain tick in progress is a full one.
    private boolean fullTick;

    private boolean enabled = true;

    /// Builds each brain's tree as its owner joins a level, and again after a data pack registry reload.
    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Entity.SPAWNED.registerEvent(event -> {
            BrainComponent brain = event.getEntityPatch().getComponent(BrainComponent.class);

            if (brain != null) brain.setBehaviorTree(brain.behaviorTree);
        });

        BuiltInEventHooks.Registry.REGISTRY_ACCESS_RELOAD.registerEvent(_ -> registryGeneration++);
    }

    public BrainComponent(
        final EntityPatch<?> owner,
        final @Nullable ResourceKey<BehaviorTreeBlueprint> behaviorTree,
        final List<TagKey<EntityType<?>>> hostileTypes,
        final List<TagKey<EntityType<?>>> friendlyTypes
    ) {
        this.owner = owner;
        this.behaviorTree = behaviorTree;
        this.hostileTypes = List.copyOf(hostileTypes);
        this.friendlyTypes = List.copyOf(friendlyTypes);
    }

    /// Replaces the running tree with `behaviorTree`, which starts from its root. The running tasks of the old tree
    /// are canceled first.
    @ScriptFunction(label = "Set Behavior Tree", category = "Behavior", desc = """
        Replaces the running behavior tree, which starts from its root
    """)
    public void setBehaviorTree(final @Nullable ResourceKey<BehaviorTreeBlueprint> behaviorTree) {
        if (treeInstance != null) treeInstance.getRunner().stop();

        this.behaviorTree = behaviorTree;
        this.treeInstance = null;
        this.builtGeneration = registryGeneration;

        if (behaviorTree == null || owner.isLogicalClient()) return;

        Optional<Holder.Reference<BehaviorTreeBlueprint>> blueprint =
            owner.getLevel().registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY).get(behaviorTree);

        if (blueprint.isEmpty()) {
            Akythera.LOGGER.warn("No behavior tree {} for {}", behaviorTree.identifier(), owner.getOriginal());
            return;
        }

        try {
            treeInstance = blueprint.get().value().instantiate(this);
        } catch (IllegalStateException e) {
            Akythera.LOGGER.warn("Can't build behavior tree {} for {}", behaviorTree.identifier(), owner.getOriginal(), e);
        }
    }

    public @Nullable ResourceKey<BehaviorTreeBlueprint> getBehaviorTree() {
        return behaviorTree;
    }

    public @Nullable BehaviorTreeInstance getTreeInstance() {
        return treeInstance;
    }

    /// Advances the tree. A full tick is the one vanilla runs its goal selectors fully on.
    @ApiStatus.Internal
    public void tick(final boolean fullTick) {
        this.fullTick = fullTick;

        if (builtGeneration != registryGeneration) setBehaviorTree(behaviorTree);
        if (treeInstance == null) return;
        if (fullTick) treeInstance.tickSight();

        treeInstance.getRunner().tick(fullTick);
    }

    /// Whether the brain tick in progress is a full one.
    public boolean isFullTick() {
        return fullTick;
    }

    /// Visits the running tree of every brain whose owner stands within `radius` of `location`.
    public static void forEachTreeNear(final ServerLevel level, final Vec3 location, final double radius, final Consumer<BehaviorTreeInstance> visitor) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(location, radius * 2.0D, radius * 2.0D, radius * 2.0D))) {
            BehaviorTreeInstance treeInstance = treeOf(entity);

            if (treeInstance != null && entity.position().distanceToSqr(location) <= radius * radius) visitor.accept(treeInstance);
        }
    }

    /// The running tree of the brain of `entity`, or null when it has none.
    public static @Nullable BehaviorTreeInstance treeOf(final Entity entity) {
        EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);
        BrainComponent brain = entityPatch == null ? null : entityPatch.getComponent(BrainComponent.class);

        return brain == null ? null : brain.treeInstance;
    }

    /// The value of `key` in the blackboard, or null while no tree runs or the tree has no such variable.
    public @Nullable Object getBlackboardValue(final BlackboardKey key) {
        return treeInstance != null && treeInstance.getBlackboard().hasVariable(key.name())
            ? treeInstance.getBlackboard().getValue(key.name())
            : null;
    }

    /// Writes `value` to `key` in the blackboard. Does nothing while no tree runs or the tree has no such variable.
    public void setBlackboardValue(final BlackboardKey key, final @Nullable Object value) {
        if (treeInstance != null && treeInstance.getBlackboard().hasVariable(key.name())) {
            treeInstance.getBlackboard().setValue(key.name(), value);
        }
    }

    /// Whether `entity` is of a type in the hostile tag list.
    public boolean isListedHostile(final Entity entity) {
        return hostileTypes.stream().anyMatch(entity.typeHolder()::is);
    }

    /// Whether `entity` is of a type in the friendly tag list.
    public boolean isListedFriendly(final Entity entity) {
        return friendlyTypes.stream().anyMatch(entity.typeHolder()::is);
    }

    // ***************************************************************
    // DynamicPatchComponent
    // ***************************************************************

    @Override
    public EntityPatch<?> getOwner() {
        return owner;
    }

    /// Drops the running tree, which is built again as the new owner joins its level.
    @Override
    public BrainComponent getRenewed(final EntityPatch<?> newOwner) {
        if (treeInstance != null) treeInstance.getRunner().stop();

        owner = newOwner;
        treeInstance = null;

        return this;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
