package com.yesman.akythera.api.lifecycle;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.animator.MontageOnlyAnimator;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;

/// Provides components used by Epic Fight. Some components are generated
/// depending on the physical side. It effectively decouples client side
/// code from a dedicated server, to prevent invalid dist exception occurred
/// by referencing client-only code.
///
/// @see ClientComponentsFactory a client side component factory
/// @see ServerComponentsFactory a server side component factory
public abstract class ComponentsFactory {
    private static ComponentsFactory impl;

    /// Initialize the factory instance. This should be called only once in
    /// mod lifecycle.
    public static void initialize(ComponentsFactory factory) {
        if (impl != null) {
            Akythera.LOGGER.error("Sided factory already initialized!");
        } else {
            impl = factory;
        }
    }

    /// Returns implementation
    public static ComponentsFactory getImpl() {
        if (impl == null) {
            throw new IllegalStateException("ComponentsFactory not initialized!");
        }

        return impl;
    }

    /// Maps entity patch with default entity patch providers
    private final Map<EntityType<?>, Function<? extends Entity, ? extends EntityPatch<?>>> entityPatchProviders = new HashMap<>();

    /// Register a provider for [EntityPatch] for given entity type.
    ///
    /// These providers have a higher priority than data pack entity patches.
    public <T extends Entity> void registerEntityPatchProvider(
        EntityType<?> entityType,
        Function<T, ? extends EntityPatch<? extends T>> entityPatchProvider
    ) {
        if (entityPatchProviders.containsKey(entityType)) {
            Akythera.LOGGER.warn(
                "{} has already registered entity patch provider. You're overwriting previous entry.",
                entityType
            );
        }

        entityPatchProviders.put(entityType, entityPatchProvider);
    }

    /// Returns an entity patch for a corresponding entity type
    ///
    /// Entity patches registered by [#registerEntityPatchProvider] will be returned first
    /// than data-pack entity patches
    @SuppressWarnings("unchecked")
    public <T extends Entity> @Nullable EntityPatch<T> getEntityPatch(T entity) {
        // Downcast <? extends Entity> to Entity
        var provider = (Function<Entity, ? extends EntityPatch<?>>)entityPatchProviders.get(entity.getType());

        if (provider != null) {
            // Return built-in entity patch
            return (EntityPatch<T>)provider.apply(entity);
        }

        return getDataPackEntityPatch(entity);
    }

    /// The patch a data pack declares for `entity`'s type, or null when it declares none.
    @SuppressWarnings("unchecked")
    public <T extends Entity> @Nullable EntityPatch<T> getDataPackEntityPatch(T entity) {
        var registryOptional = entity.level().registryAccess().lookup(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);

        if (registryOptional.isEmpty()) {
            return null;
        }

        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Holder.Reference<EntityPatchBlueprint> entityPatchBlueprint = registryOptional.get().get(entityId).orElse(null);

        if (entityPatchBlueprint == null) return null;

        ScriptFunction patchCtor = entityPatchBlueprint.value().ctor();

        try {
            return (EntityPatch<T>)patchCtor.castToBiFunction().biFunction().apply(entityPatchBlueprint, entity);
        } catch (ClassCastException | IllegalStateException e) {
            // The constructor's entity parameter is checked at the erased invocation boundary, so a
            // patch parented on a narrower one (a player patch on a zombie) only fails here.
            Akythera.LOGGER.error("Entity patch '{}' cannot be built for {}", entityId, entity.getType(), e);
            return null;
        }
    }

    /// Maps the entity type with armature asset
    private final Map<EntityType<?>, AssetAccessor<? extends Armature>> entityArmatures = new HashMap<> ();

    /// Register an armature accessor for an entity type. The mapping works for outside-level model previews like [Model3DViewer]
    ///
    /// Passing a null armature removes the entity type's mapping.
    public void registerDefaultArmature(EntityType<?> entityType, @Nullable AssetAccessor<? extends Armature> armature) {
        if (armature == null) {
            entityArmatures.remove(entityType);
        } else {
            entityArmatures.put(entityType, armature);
        }
    }

    public void clearArmatureMappings() {
        entityArmatures.clear();
    }

    /// Returns an armature by entity type.
    public @Nullable AssetAccessor<? extends Armature> getExternalArmatureMappingFor(EntityType<?> entityType)
        throws NoSuchElementException
    {
        return entityArmatures.get(entityType);
    }

    /// Custom animator providers by entity types. Register a new provider that doesn't follow
    /// the default animator supply rule. ([MontageOnlyAnimator] in a server and
    /// [BlendingAnimator] in a client)
    private final Map<Class<?>, BiFunction<EntityPatch<Entity>, Function<Animator, AnimatorScript>, ? extends Animator>> customAnimatorSuppliers = new HashMap<>();

    /// Register a new animator provider rule. The default rule is returning [MontageOnlyAnimator] in
    /// logical server and [BlendingAnimator] in logical client to optimize redundant animator state
    /// updates and pose states.
    ///
    /// Consider assigning [BlendingAnimator] for huge scaled mobs with IK, whose require to update
    /// hitboxes and position corresponding to [Bone]s.
    public void registerCustomAnimatorSupplyRule(
        Class<?> entityType,
        BiFunction<EntityPatch<Entity>, Function<Animator, AnimatorScript>, ? extends Animator> supplier
    ) {
        if (customAnimatorSuppliers.containsKey(entityType)) {
            Akythera.LOGGER.warn(
                "{} has already registered for custom animator supply rule. You're overwriting previous entry.",
                entityType
            );
        }

        customAnimatorSuppliers.put(entityType, supplier);
    }

    /// Provides an animator for given entity patch. If no custom animator provider rule is registered, follow the
    /// default rule.
    ///
    /// Since this method calls [StaticRegistryAccess] to get [AnimatorScript], it must be called after world-loading is done.
    ///
    /// @see #registerCustomAnimatorSupplyRule
    public Animator getAnimator(final EntityPatch<Entity> owner, final Function<Animator, AnimatorScript> scriptProvider) {
        if (customAnimatorSuppliers.containsKey(owner.getClass())) {
            // Force cast to root type
            return customAnimatorSuppliers.get(owner.getClass()).apply(owner, scriptProvider);
        }

        // default behavior
        return owner.isLogicalClient() ? new BlendingAnimator(owner, scriptProvider) : new MontageOnlyAnimator(owner);
    }

    /// Returns the resource manager the assets are read from.
    public abstract ResourceManager getResourceManager();

    /// Returns [PlayerSkin] – Player model & cosmetic information belongs to [AbstractClientPlayer],
    /// a client side only class.
    public abstract @Nullable PlayerSkin getPlayerSkin(final Player player);
}
