package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinderBlueprint;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.item.component.AttributeModifiersComponent;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.client.api.component.impl.MeshComponentBlueprint;
import com.yesman.akythera.client.api.component.impl.PALPoseBinder;
import com.yesman.akythera.client.api.component.impl.SkinnedArmorRendererBlueprint;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.widget.ButtonBlueprint;
import com.yesman.akythera.core.blueprint.widget.CheckBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.ColorPickerBlueprint;
import com.yesman.akythera.core.blueprint.widget.ComboBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.DragValueBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.EditBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.GridBlueprint;
import com.yesman.akythera.core.blueprint.widget.ImageBlueprint;
import com.yesman.akythera.core.blueprint.widget.LinearContainerBlueprint;
import com.yesman.akythera.core.blueprint.widget.ProgressionBarBlueprint;
import com.yesman.akythera.core.blueprint.widget.TextAreaBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprintComponentBlueprint;
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

    public static final ModPlatform.Registrar<MapCodec<? extends ComponentBlueprint<?, ?>>> WIDGET_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.WIDGET_COMPONENT_TYPES_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ARMATURE =
        ENTITY_PATCH_REGISTRAR.register("armature", () -> ArmatureComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ENTITY_MOVEMENT =
        ENTITY_PATCH_REGISTRAR.register("entity_movement", () -> EntityMovementComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ABILITY_SYSTEM =
        ENTITY_PATCH_REGISTRAR.register("ability_system", () -> AbilitySystemComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> TRANSFORM_SOCKET =
        ENTITY_PATCH_REGISTRAR.register("transform_socket", () -> TransformSocket.CODEC);

    /// One entry for every shape — [Collider#MAP_CODEC] dispatches on `shape` inside it.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> COLLIDER =
        ENTITY_PATCH_REGISTRAR.register("collider", () -> Collider.MAP_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> PAL_POSE_BINDER =
        ENTITY_PATCH_REGISTRAR.register("pal_pose_binder", () -> PALPoseBinder.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> MESH =
        ENTITY_PATCH_REGISTRAR.register("mesh", () -> MeshComponentBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> EQUIPMENT_SLOT_BINDER =
        ENTITY_PATCH_REGISTRAR.register("equipment_slot_binder", () -> EquipmentSlotBinderBlueprint.CODEC);

    /// The former id of [#EQUIPMENT_SLOT_BINDER], read only.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_IN_SLOT =
        ENTITY_PATCH_REGISTRAR.register("item_in_slot", () -> EquipmentSlotBinderBlueprint.LEGACY_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> SKINNED_ARMOR =
        ENTITY_PATCH_REGISTRAR.register("skinned_armor", () -> SkinnedArmorRendererBlueprint.CODEC);

    /// The same socket type as [#TRANSFORM_SOCKET]—an item patch places things too, it just has no
    /// bone to place them on. One codec, one entry per registry, as [Collider] already is.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_TRANSFORM_SOCKET =
        ITEM_PATCH_REGISTRAR.register("transform_socket", () -> TransformSocket.CODEC);

    /// One entry for every shape — [Collider#MAP_CODEC] dispatches on `shape` inside it.
    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_COLLIDER =
        ITEM_PATCH_REGISTRAR.register("collider", () -> Collider.MAP_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ATTRIBUTE_MODIFIERS =
        ITEM_PATCH_REGISTRAR.register("attribute_modifiers", () -> AttributeModifiersComponent.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> REPLACE_ANIMATIONS =
        ITEM_PATCH_REGISTRAR.register("replace_animations", () -> ReplaceAnimations.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> WEAPON_STANCE_VARIATION_ADAPTER =
        ITEM_PATCH_REGISTRAR.register("weapon_stance_variation_adapter", () -> WeaponStanceVariationAdapter.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> BUTTON =
        WIDGET_REGISTRAR.register("button", () -> ButtonBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> CHECK_BOX =
        WIDGET_REGISTRAR.register("check_box", () -> CheckBoxBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> COMBO_BOX =
        WIDGET_REGISTRAR.register("combo_box", () -> ComboBoxBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> EDIT_BOX =
        WIDGET_REGISTRAR.register("edit_box", () -> EditBoxBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> DRAG_VALUE_BOX =
        WIDGET_REGISTRAR.register("drag_value_box", () -> DragValueBoxBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> TEXT_AREA =
        WIDGET_REGISTRAR.register("text_area", () -> TextAreaBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> IMAGE =
        WIDGET_REGISTRAR.register("image", () -> ImageBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> PROGRESSION_BAR =
        WIDGET_REGISTRAR.register("progression_bar", () -> ProgressionBarBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> COLOR_PICKER =
        WIDGET_REGISTRAR.register("color_picker", () -> ColorPickerBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> GRID =
        WIDGET_REGISTRAR.register("grid", () -> GridBlueprint.CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> HORIZONTAL_CONTAINER =
        WIDGET_REGISTRAR.register("horizontal_container", () -> LinearContainerBlueprint.HORIZONTAL_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> VERTICAL_CONTAINER =
        WIDGET_REGISTRAR.register("vertical_container", () -> LinearContainerBlueprint.VERTICAL_CODEC);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> WIDGET_BLUEPRINT =
        WIDGET_REGISTRAR.register("widget_blueprint", () -> WidgetBlueprintComponentBlueprint.CODEC);
}
