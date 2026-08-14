package com.yesman.akythera.api.world.patch.entity;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.*;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityConstructEvent;
import com.yesman.akythera.api.event.entity.HandlePersistentDataEvent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.component.EntityEventListener;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/// A modularized extra entity data required by Epic Fight. To avoid
/// massive mixin shadows and unique fields, everything used by Epic Fight
/// API is here.
///
/// Class Inheritance opened, but not recommended for commonly-shared entities
/// like player. Use the [DynamicPatchComponent] system for custom behaviors you want
/// while keeping compatibility with other addons.
///
/// Register custom entity patches via [ComponentsFactory#registerEntityPatchProvider].
public class EntityPatch<T extends Entity> implements AnimatableObject, ComponentAttachable {
    /// An owner of the patch. Use [#getOriginal()] to access this field.
    @NonNull
	private final T original;

    // Null if entity patch is provided by custom
    @Nullable
    private final EntityPatchScript script;

    /// Dynamic event listeners
    private final EntityEventListener entityEventListener;

    private final Map<String, ? extends PatchComponent> components;

    /// Components by type. Each component is unique in the patch
    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;

    // Constructor for hardcoded entity patches. Register components manually using
    // `BuiltInEventHooks.Entity.CONSTRUCTED` event in subclasses.
    public EntityPatch(@NonNull final T original) {
        this((EntityPatchBlueprint)null, original);
    }

    // Data-driven entity patches
    public EntityPatch(final Holder.@Nullable Reference<EntityPatchBlueprint> blueprint, @NonNull final T original) {
        this(blueprint == null ? null : blueprint.value(), original);
    }

    // Generate entity patch components by script.
    @SuppressWarnings("unchecked")
    public EntityPatch(final EntityPatchBlueprint blueprint, @NonNull final T original) {
        this.original = original;
        this.script = blueprint == null ? null : blueprint.instantiate((EntityPatch<Entity>)this);

        entityEventListener = new EntityEventListener(this);
        buildEventListeners();

        Map<String, PatchComponent> componentGather = new HashMap<>();
        EntityConstructEvent event = new EntityConstructEvent(this, componentGather, false);

        if (script != null) {
            for (Map.Entry<String, ComponentBlueprint<?, ?>> entry : script.componentsToAttach().entrySet()) {
                event.addComponent(entry.getKey(), ((ComponentBlueprint<?, Object>)entry.getValue()).instantiate(this));
            }

            script.onConstruct();
        }

        BuiltInEventHooks.Entity.CONSTRUCTED.post(event);

        // Type casting exception may occur, but this is not the engine's responsibility.
        components = Collections.unmodifiableMap(componentGather);

        Map<Class<? extends UniqueComponent>, UniqueComponent> uniqueComponentMap = new HashMap<>();

        for (Map.Entry<String, ? extends PatchComponent> entry : components.entrySet()) {
            if (entry.getValue() instanceof UniqueComponent uc) {
                if (uniqueComponentMap.containsKey(uc.getClass())) {
                    throw new IllegalStateException(
                        MessageFormat.format("""
                            Detected more than one component of {0}, {0} is UniqueComponent that does not allow duplicated types of components. {1} must be removed to properly launch the game.
                        """, uc.getClass(), entry.getKey())
                    );
                }

                uniqueComponentMap.put(uc.getClass(), uc);
            }
        }

        uniqueComponents = Collections.unmodifiableMap(uniqueComponentMap);
    }

    /// Copy constructor. Called on datapack reload and dimension changes.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EntityPatch(final EntityPatch<T> copyTarget) {
        original = copyTarget.original;
        script = copyTarget.script;

        // Note: event listener must be fully reloaded with new lambda because the old lambda points the old entity patch's methods.
        entityEventListener = new EntityEventListener(this);
        buildEventListeners();

        final Map<String, PatchComponent> componentGather = new HashMap<>();

        copyTarget.components.entrySet().stream()
            .map(entry ->
                Pair.of(
                    entry.getKey(),
                    entry.getValue() instanceof DynamicPatchComponent reloadableComponent
                        ? reloadableComponent.getRenewed(this)
                        : entry.getValue()
                )
            ).forEach(pair ->
                componentGather.put(pair.getFirst(), pair.getSecond())
            );

        EntityConstructEvent event = new EntityConstructEvent(this, componentGather, true);
        BuiltInEventHooks.Entity.CONSTRUCTED.post(event);

        components = Collections.unmodifiableMap(componentGather);

        Map<Class<? extends UniqueComponent>, UniqueComponent> uniqueComponentMap = new HashMap<>();

        for (Map.Entry<String, ? extends PatchComponent> entry : components.entrySet()) {
            if (entry.getValue() instanceof UniqueComponent uc) {
                // No validation, the old patch has already been verified no duplicated unique components.
                uniqueComponentMap.put(uc.getClass(), uc);
            }
        }

        uniqueComponents = Collections.unmodifiableMap(uniqueComponentMap);
    }

    protected void buildEventListeners() {
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.SPAWNED, event -> onJoinWorld(event.getLevel(), event.isWorldGenSpawn()));
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.TRACKING_START, event -> onStartTracking(event.getTracker()));
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.TRACKING_END, event -> onStopTracking(event.getTracker()));
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.TICK_PRE, _ -> preTick());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.TICK_POST, _ -> postTick());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.CLIENT_TICK_PRE, _ -> preTickClient());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.CLIENT_TICK_POST, _ -> postTickClient());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.RENDER_TICK, event -> beforeRender(event.getDeltaTime(), event.getPartialTick()));
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.SERVER_TICK_PRE, _ -> preTickServer());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.SERVER_TICK_POST, _ -> postTickServer());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.SEND_SYNC_DATA, _ -> sendData());
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.SERIALIZE, event -> writeData(event.getCompoundTag()));
        entityEventListener.registerEvent(BuiltInEventHooks.Entity.DESERIALIZE, event -> readData(event.getCompoundTag()));
    }

    @Override
    public Map<String, ? extends PatchComponent> getComponents() {
        return components;
    }

    /// Called when a player in a server level starts to track this entity in their local client
    ///
    /// Send data that requires to be synced
	public void onStartTracking(ServerPlayer trackingPlayer) {
	}

    /// Called when a player in a server level stops tracking this entity
	public void onStopTracking(ServerPlayer trackingPlayer) {
	}

	public void onJoinWorld(Level level, boolean worldGenSpawn) {
        if (script != null) script.onJoinWorld(level);
	}

    /// Ticks before [Entity#tick]
	public void preTick() {
	}

    /// Ticks before [Entity#tick] in a client
	public void preTickClient() {
	}

    /// Ticks before [Entity#tick] in a server
	public void preTickServer() {
    }

    /// Ticks after [Entity#tick]
	public void postTick() {
        components.values().forEach(component -> {
            if (component instanceof TickableDynamicComponent<?> tickableDynamicComponent) {
                tickableDynamicComponent.tick();
            }
        });

        if (script != null) script.update();
	}

    /// Ticks after [Entity#tick] in a client
	public void postTickClient() {
	}

    /// Ticks after [Entity#tick] in a server
	public void postTickServer() {
	}

    /// Called before the entity is drawn in the screen
    ///
    /// @param deltaTime    the frame time since the last render ends
    /// @param partialTick  the partial time ratio from the last [Entity#tick] is called
    @ClientOnly
    public void beforeRender(final float deltaTime, final float partialTick) {
        // Built-in support: updating armature component if exists.
        // deltaTime == 0.0 means the game is paused, so don't call the update logic
        if (deltaTime > 0.0F) {
            components.values().forEach(component -> {
                if (component instanceof TickableDynamicComponent<?> tickableDynamicComponent) {
                    tickableDynamicComponent.frameUpdate(deltaTime, partialTick);
                }
            });
        }
    }

    /// Saves permanent data
	public void writeData(final CompoundTag compound) {
        components.values().forEach(component -> {
            if (component instanceof SerializableDynamicComponent<?> serializableComponent) {
                serializableComponent.writeData(compound);
            }
        });

        BuiltInEventHooks.Entity.SERIALIZE.post(new HandlePersistentDataEvent.Serialize(this, compound));
	}

    /// Read the entity data from saved file
	public void readData(final CompoundTag compound) {
        components.values().forEach(component -> {
            if (component instanceof SerializableDynamicComponent<?> serializableComponent) {
                serializableComponent.readData(compound);
            }
        });

        BuiltInEventHooks.Entity.DESERIALIZE.post(new HandlePersistentDataEvent.Deserialize(this, compound));
	}

    /// Called in syncing data to client
    public void sendData() {
    }

    /// Returns an owner of the patch. Behaves same as
	public final @NonNull T getOriginal() {
		return original;
	}

    /// Returns a level that an owner is belonged to
	public final Level getLevel() {
		return original.level();
	}

    /// Returns a dynamic event listener manager
    ///
    /// Use [EntityEventListener#registerEvent] to add event listeners or
    /// [EntityEventListener#removeListenersBelongTo] to remove listeners.
    public EntityEventListener getEntityEventListener() {
        return entityEventListener;
    }

    /// Transform the given vector to the world transform applied one, based on the entity's yaw and
    /// optionally the entity's pitch rotation.
    public Vec3 fromRelativeToWorldVector(final Vec3 relativeVector, final boolean calculatePitch) {
        final float yawRad = getVisualYRot() * Mth.DEG_TO_RAD;
        final float pitchRad = calculatePitch ? getOriginal().getXRot() * Mth.DEG_TO_RAD : 0.0F;
        final float cosYaw = Mth.cos(yawRad);
        final float sinYaw = Mth.sin(yawRad);
        final float cosPitch = Mth.cos(pitchRad);
        final float sinPitch = Mth.sin(pitchRad);
        final double lx = relativeVector.x;
        final double ly = relativeVector.y;
        final double lz = relativeVector.z;

        // Apply pitch around X axis first
        final double py = ly * cosPitch - lz * sinPitch;
        final double pz = ly * sinPitch + lz * cosPitch;

        // Then apply Minecraft yaw around Y axis (yaw=0 faces +Z, increases clockwise viewed from above)
        final double worldX = lx * cosYaw - pz * sinYaw;
        final double worldZ = lx * sinYaw + pz * cosYaw;

        return new Vec3(worldX, py, worldZ);
    }

    @SuppressWarnings("unchecked") @Override
    public <C extends UniqueComponent> C getComponent(final Class<C> type) {
        return (C)uniqueComponents.get(type);
    }

    @Override
    public @Nullable ArmatureComponent getArmatureComponent() {
        return getComponent(ArmatureComponent.class);
    }

    // *************************************
    // `Entity` reflection methods
    // *************************************

    @ScriptFunction(pure = true, label = "Get Id")
    public final int getId() {
        return original.getId();
    }

    @ScriptFunction(pure = true, label = "Is Logical Client", desc = "Returns whether the entity is in a client level")
    public boolean isLogicalClient() {
        return original.level().isClientSide();
    }

    @ScriptFunction(pure = true, label = "Is Logical Server", desc = "Returns whether the entity is in a server level")
    public boolean isLogicalServer() {
        return !isLogicalClient();
    }

    @ScriptFunction(pure = true, label = "Get View Vector", desc = "Get a view vector of an entity based on x, y rotations.")
    public final Vec3 calculateViewVector(final float xRot, final float yRot) {
        return getOriginal().calculateViewVector(xRot, yRot);
    }

    @ScriptFunction(pure = true, label = "Get X Rot")
    public float getXRot(final float partialTick) {
        return getOriginal().getXRot(partialTick);
    }

    @ScriptFunction(pure = true, label = "Get Y Rot")
    public float getYRot(final float partialTick) {
        return getOriginal().getYRot(partialTick);
    }

    @ScriptFunction(pure = true, label = "Get Y Body Rot", desc = "Returns the entity body model's visual rotation.")
    public float getVisualYRot() {
        return getOriginal() instanceof LivingEntity livingEntity ? livingEntity.yBodyRot : getOriginal().getYRot();
    }

    @ScriptFunction(pure = true, label = "Get Animation Speed", desc = "Returns animation playback speed by movement.")
    public double getAnimationSpeed(final float partialTick) {
        if (original instanceof LivingEntity livingEntity) {
            return livingEntity.walkAnimation.speed(partialTick);
        }

        return original.getDeltaMovement().length();
    }

    @ScriptFunction(pure = true, label = "Get Position")
    public Vec3 position() {
        return getOriginal().position();
    }

    @ScriptFunction(pure = true, label = "Get Position (Partial)")
    public Vec3 getPosition(final float partialTick) {
        return getOriginal().getPosition(partialTick);
    }

    @ScriptFunction(pure = true, label = "On Ground")
    public boolean onGround() {
        return getOriginal().onGround();
    }

    @ScriptFunction(pure = true, label = "Is Under Water", desc = "Returns whether the entity's eyes are in water.")
    public boolean isUnderWater() {
        return getOriginal().isUnderWater();
    }

    @ScriptFunction(pure = true, label = "Flying", desc = "Returns whether the entity is flying. Always false for entities that can't fly")
    public boolean isFlying() {
        if (getOriginal() instanceof Player player) {
            return player.getAbilities().flying;
        }

        return false;
    }

    @ScriptFunction(pure = true, label = "Is Controlled by Player", desc = "Returns whether the entity is controlled by users. If false, the entity is controlled by AI.")
    public boolean isControlledByPlayer() {
        return false;
    }

/*
    /// TODO: Triggers some synchronization packet
	@ClientOnly
	public void fireEntityPairingEvent(SPEntityPairingPacket packet) {
        ProcessEntityPairingPacketEvent pairingPacketEvent = new ProcessEntityPairingPacketEvent(this, packet);
        BuiltInClientEventHooks.Entity.HANDLE_ENTITY_PAIRING_PACKET.post(pairingPacketEvent);

		if (!pairingPacketEvent.isCanceled()) {
			entityPairing(packet);
		}
	}

    /// TODO: Processes a sync packet sent from a server
    @ClientOnly
	public void entityPairing(SPEntityPairingPacket packet) {
	}
*/
}