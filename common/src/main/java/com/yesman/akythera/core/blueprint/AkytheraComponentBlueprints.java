package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.patch.item.component.PatchAttributeModifiers;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
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

public final class AkytheraComponentBlueprints {
    private AkytheraComponentBlueprints() {}

    public static final ModPlatform.Registrar<MapCodec<? extends ComponentBlueprint<?, ?>>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES_KEY, Akythera.MODID);

    // ********************* Entity patch components *********************
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ARMATURE =
        REGISTRAR.register("armature", () -> ArmatureComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> BONE_SOCKET =
        REGISTRAR.register("bone_socket", () -> BoneSocket.CODEC);

    /// One entry for every shape — [Collider#MAP_CODEC] dispatches on `shape` inside it.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> COLLIDER =
        REGISTRAR.register("collider", () -> Collider.MAP_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ENTITY_MODEL =
        REGISTRAR.register("entity_model", () -> EntityModelComponent.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> MESH =
        REGISTRAR.register("mesh", () -> MeshComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> EQUIPMENT =
        REGISTRAR.register("equipment", () -> EquipmentComponent.CODEC);

    // ********************* Item patch components *********************
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ATTRIBUTE_MODIFIERS =
        REGISTRAR.register("attribute_modifiers", () -> PatchAttributeModifiers.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> REPLACE_ANIMATIONS =
        REGISTRAR.register("replace_animations", () -> ReplaceAnimations.CODEC);
}
