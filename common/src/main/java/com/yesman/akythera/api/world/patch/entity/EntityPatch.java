package com.yesman.akythera.api.world.patch.entity;

import com.yesman.akythera.api.animation.ArmatureComponent;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.ReloadablePatchComponent;
import com.yesman.akythera.api.component.SerializablePatchComponent;
import com.yesman.akythera.api.component.TickablePatchComponent;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/// A modularized extra entity data required by Epic Fight. To avoid
/// massive mixin shadows and unique fields, everything used by Epic Fight
/// API is here.
///
/// Class Inheritance opened, but not recommended for commonly-shared entities
/// like player. Use the [PatchComponent] system for custom behaviors you want
/// while keeping compatibility with other addons.
///
/// Register custom entity patches via [ComponentsFactory#registerEntityPatchProvider].
public class EntityPatch<T extends Entity> implements AnimatableObject, PatchComponent<T> {
    /// A owner of this patch. Use [#getOwner()] to access this field.
    @NonNull
	private final T owner;

    // Null if entity patch is provided by custom
    @Nullable
    private final EntityPatchScript script;

    /// Dynamic event listeners
    private final EntityEventListener entityEventListener;

    private final Map<Class<? extends PatchComponent<T>>, ? extends PatchComponent<T>> components;

    // Constructor for hardcoded entity patches. Register components manually using
    // `BuiltInEventHooks.Entity.CONSTRUCTED` event in subclasses.
    public EntityPatch(@NonNull final T owner) {
        this((EntityPatchBlueprint)null, owner);
    }

    // Data-driven entity patches
    public EntityPatch(final Holder.@Nullable Reference<EntityPatchBlueprint> blueprint, @NonNull final T owner) {
        this(blueprint == null ? null : blueprint.value(), owner);
    }

    // Generate entity patch components by script.
    @SuppressWarnings("unchecked")
    public EntityPatch(final EntityPatchBlueprint blueprint, @NonNull final T owner) {
        this.owner = owner;
        this.script = blueprint == null ? null : blueprint.instantiate((EntityPatch<Entity>)this);

        entityEventListener = new EntityEventListener(this);
        buildEventListeners();

        Map<Class<? extends PatchComponent<T>>, PatchComponent<T>> componentGather = new HashMap<>();
        EntityConstructEvent event = new EntityConstructEvent(this, componentGather, false);

        if (script != null) {
            for (ComponentBlueprint<?, ?> componentBp : script.componentsToAttach()) {
                event.addComponent(((ComponentBlueprint<?, Object>)componentBp).instantiate(this));
            }

            script.onConstruct();
        }

        BuiltInEventHooks.Entity.CONSTRUCTED.post(event);

        // Type casting exception may occur, but this is not the engine's responsibility.
        components = Collections.unmodifiableMap(componentGather);
    }

    /// Copy constructor. Called on datapack reload and dimension changes.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EntityPatch(final EntityPatch<T> copyTarget) {
        owner = copyTarget.owner;
        script = copyTarget.script;

        // Note: event listener must be fully reloaded with new lambda because the old lambda points the old entity patch's methods.
        entityEventListener = new EntityEventListener(this);
        buildEventListeners();

        final Map<Class<? extends PatchComponent<T>>, PatchComponent<T>> componentGather = new HashMap<>();

        copyTarget.components.values().stream()
            .map(entry ->
                entry instanceof ReloadablePatchComponent reloadableComponent ? reloadableComponent.getRenewed(this) : entry
            ).forEach(renewedComponent ->
                componentGather.put((Class<? extends PatchComponent<T>>)renewedComponent.getClass(), renewedComponent)
            );

        EntityConstructEvent event = new EntityConstructEvent(this, componentGather, true);
        BuiltInEventHooks.Entity.CONSTRUCTED.post(event);

        components = Collections.unmodifiableMap(componentGather);
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

    /// Returns component
    @SuppressWarnings("unchecked")
    public <C extends PatchComponent<?>> @Nullable C getComponent(Class<C> type) {
        return (C)components.get(type);
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
            if (component instanceof TickablePatchComponent<?> tickablePatchComponent) {
                tickablePatchComponent.tick();
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
                if (component instanceof TickablePatchComponent<?> tickablePatchComponent) {
                    tickablePatchComponent.frameUpdate(deltaTime, partialTick);
                }
            });
        }
    }

    /// Saves permanent data
	public void writeData(final CompoundTag compound) {
        components.values().forEach(component -> {
            if (component instanceof SerializablePatchComponent<?> serializableComponent) {
                serializableComponent.writeData(compound);
            }
        });

        BuiltInEventHooks.Entity.SERIALIZE.post(new HandlePersistentDataEvent.Serialize(this, compound));
	}

    /// Read the entity data from saved file
	public void readData(final CompoundTag compound) {
        components.values().forEach(component -> {
            if (component instanceof SerializablePatchComponent<?> serializableComponent) {
                serializableComponent.readData(compound);
            }
        });

        BuiltInEventHooks.Entity.DESERIALIZE.post(new HandlePersistentDataEvent.Deserialize(this, compound));
	}

    /// Called in syncing data to client
    public void sendData() {
    }

    /// Returns an owner of the patch. Behaves same as
	public final @NonNull T getOwner() {
		return owner;
	}

    /// Returns a level that an owner is belonged to
	public final Level getLevel() {
		return owner.level();
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
        final float pitchRad = calculatePitch ? getOwner().getXRot() * Mth.DEG_TO_RAD : 0.0F;
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

    @Override
    public @Nullable ArmatureComponent getArmatureComponent() {
        return getComponent(ArmatureComponent.class);
    }

    // *************************************
    // `Entity` reflection methods
    // *************************************

    @ScriptFunction(pure = true, label = "Get Id")
    public final int getId() {
        return owner.getId();
    }

    @ScriptFunction(pure = true, label = "Is Logical Client", desc = "Returns whether the entity is in a client level")
    public boolean isLogicalClient() {
        return owner.level().isClientSide();
    }

    @ScriptFunction(pure = true, label = "Is Logical Server", desc = "Returns whether the entity is in a server level")
    public boolean isLogicalServer() {
        return !isLogicalClient();
    }

    @ScriptFunction(pure = true, label = "Get View Vector", desc = "Get a view vector of an entity based on x, y rotations.")
    public final Vec3 calculateViewVector(float xRot, float yRot) {
        return getOwner().calculateViewVector(xRot, yRot);
    }

    @ScriptFunction(pure = true, label = "Get X Rot")
    public float getXRot(float partialTick) {
        return getOwner().getXRot(partialTick);
    }

    @ScriptFunction(pure = true, label = "Get Y Rot")
    public float getYRot(float partialTick) {
        return getOwner().getYRot(partialTick);
    }

    @ScriptFunction(pure = true, label = "Get Y Body Rot", desc = "Returns the entity body model's visual rotation.")
    public float getVisualYRot() {
        return getOwner() instanceof LivingEntity livingEntity ? livingEntity.yBodyRot : getOwner().getYRot();
    }

    @ScriptFunction(pure = true, label = "Get Position")
    public Vec3 position() {
        return getOwner().position();
    }

    @ScriptFunction(pure = true, label = "Get Position (Partial)")
    public Vec3 getPosition(final float partialTick) {
        return getOwner().getPosition(partialTick);
    }

    @ScriptFunction(pure = true, label = "On Ground")
    public boolean onGround() {
        return getOwner().onGround();
    }

    @ScriptFunction(pure = true, label = "Is Under Water", desc = "Returns whether the entity's eyes are in water.")
    public boolean isUnderWater() {
        return getOwner().isUnderWater();
    }

    @ScriptFunction(pure = true, label = "Flying", desc = "Returns whether the entity is flying. Always false for entities that can't fly")
    public boolean isFlying() {
        if (getOwner() instanceof Player player) {
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