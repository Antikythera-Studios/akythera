package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.item.component.AttributeModifiersComponent;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.client.api.component.impl.EntityModelComponent;
import com.yesman.akythera.client.api.component.impl.EquipmentComponent;
import com.yesman.akythera.client.api.component.impl.MeshComponentBlueprint;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

public final class AkytheraComponentBlueprints {
    private AkytheraComponentBlueprints() {}

    public static final ModPlatform.Registrar<MapCodec<? extends ComponentBlueprint<?, ?>>> ENTITY_PATCH_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES_KEY, Akythera.MODID);

    public static final ModPlatform.Registrar<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_PATCH_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.ITEM_PATCH_COMPONENT_TYPES_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ARMATURE =
        ENTITY_PATCH_REGISTRAR.register("armature", () -> ArmatureComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> BONE_SOCKET =
        ENTITY_PATCH_REGISTRAR.register("bone_socket", () -> BoneSocket.CODEC);

    /// One entry for every shape — [Collider#MAP_CODEC] dispatches on `shape` inside it.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> COLLIDER =
        ENTITY_PATCH_REGISTRAR.register("collider", () -> Collider.MAP_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ENTITY_MODEL =
        ENTITY_PATCH_REGISTRAR.register("entity_model", () -> EntityModelComponent.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> MESH =
        ENTITY_PATCH_REGISTRAR.register("mesh", () -> MeshComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> EQUIPMENT =
        ENTITY_PATCH_REGISTRAR.register("equipment", () -> EquipmentComponent.CODEC);

    /// One entry for every shape — [Collider#MAP_CODEC] dispatches on `shape` inside it.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_COLLIDER =
        ITEM_PATCH_REGISTRAR.register("collider", () -> Collider.MAP_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ATTRIBUTE_MODIFIERS =
        ITEM_PATCH_REGISTRAR.register("attribute_modifiers", () -> AttributeModifiersComponent.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> REPLACE_ANIMATIONS =
        ITEM_PATCH_REGISTRAR.register("replace_animations", () -> ReplaceAnimations.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> WEAPON_STANCE_VARIATION_ADAPTER =
        ITEM_PATCH_REGISTRAR.register("weapon_stance_variation_adapter", () -> WeaponStanceVariationAdapter.CODEC);
}
