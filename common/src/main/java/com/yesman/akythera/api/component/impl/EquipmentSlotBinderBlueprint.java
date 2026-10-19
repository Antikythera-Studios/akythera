package com.yesman.akythera.api.component.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.function.Function;

/// Builds one [EquipmentSlotBinder] per host, so each host owns its own switches. Attachable under a
/// [TransformSocket], whose bone places the item.
///
/// @param itemDisplayContext the context the item model is resolved and posed in
/// @param model3d            whether the item is worn as a 3D model, see [EquipmentSlotBinder#isModel3d]
public record EquipmentSlotBinderBlueprint(
    EquipmentSlot equipmentSlot,
    ItemDisplayContext itemDisplayContext,
    boolean model3d,
    boolean enabled
)
    implements ComponentBlueprint<EquipmentSlotBinder, ComponentAttachable>
{
    public static final MapCodec<EquipmentSlotBinderBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(EquipmentSlotBinderBlueprint::equipmentSlot),
                ItemDisplayContext.CODEC.fieldOf("display_context").forGetter(EquipmentSlotBinderBlueprint::itemDisplayContext),
                Codec.BOOL.optionalFieldOf("model_3d", false).forGetter(EquipmentSlotBinderBlueprint::model3d),
                ENABLED_CODEC.forGetter(EquipmentSlotBinderBlueprint::enabled)
            )
            .apply(instance, EquipmentSlotBinderBlueprint::new)
    );

    /// [#CODEC] under the former type id `akythera:item_in_slot`, read only: a blueprint is written under
    /// `akythera:equipment_slot_binder`.
    public static final MapCodec<EquipmentSlotBinderBlueprint> LEGACY_CODEC = CODEC.xmap(Function.identity(), Function.identity());

    @Override
    public EquipmentSlotBinder instantiate(final ComponentAttachable host) {
        return ComponentsFactory.getImpl().createEquipmentSlotBinder(this, host);
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<EquipmentSlotBinder, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
