package com.yesman.akythera.api.data.modularize;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.data.modularize.impl.ScriptComparators;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class AkytheraScriptTypes {
    private AkytheraScriptTypes() {}

    public static final ModPlatform.Registrar<ScriptType> MINECRAFT_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_TYPE_KEY, "minecraft");

    // primitives

    public static final Holder<ScriptType> BYTE = MINECRAFT_REGISTRAR.register("byte", () -> new ScriptType(Byte.class, Codec.BYTE));
    public static final Holder<ScriptType> SHORT = MINECRAFT_REGISTRAR.register("short", () -> new ScriptType(Short.class, Codec.SHORT,
        Map.of(
            Byte.class, (Function<Short, Byte>)Short::byteValue
        )
    ));
    public static final Holder<ScriptType> INTEGER = MINECRAFT_REGISTRAR.register("integer", () -> new ScriptType(Integer.class, Codec.INT,
        Map.of(
            Byte.class, (Function<Integer, Byte>)Integer::byteValue,
            Short.class, (Function<Integer, Short>)Integer::shortValue
        )
    ));
    public static final Holder<ScriptType> LONG = MINECRAFT_REGISTRAR.register("long", () -> new ScriptType(Long.class, Codec.LONG,
        Map.of(
            Byte.class, (Function<Long, Byte>)Long::byteValue,
            Short.class, (Function<Long, Short>)Long::shortValue,
            Integer.class, (Function<Long, Integer>)Long::intValue
        )
    ));
    public static final Holder<ScriptType> FLOAT = MINECRAFT_REGISTRAR.register("float", () -> new ScriptType(Float.class, Codec.FLOAT,
        Map.of(
            Byte.class, (Function<Float, Byte>)Float::byteValue,
            Short.class, (Function<Float, Short>)Float::shortValue,
            Integer.class, (Function<Float, Integer>)Float::intValue,
            Long.class, (Function<Float, Long>)Float::longValue
        )
    ));
    public static final Holder<ScriptType> DOUBLE = MINECRAFT_REGISTRAR.register("double", () -> new ScriptType(Double.class, Codec.DOUBLE,
        Map.of(
            Byte.class, (Function<Double, Byte>)Double::byteValue,
            Short.class, (Function<Double, Short>)Double::shortValue,
            Integer.class, (Function<Double, Integer>)Double::intValue,
            Long.class, (Function<Double, Long>)Double::longValue,
            Float.class, (Function<Double, Float>)Double::floatValue
        )
    ));
    public static final Holder<ScriptType> BOOLEAN = MINECRAFT_REGISTRAR.register("boolean", () -> new ScriptType(Boolean.class, Codec.BOOL));

    // java.lang

    public static final Holder<ScriptType> STRING = MINECRAFT_REGISTRAR.register("string", () -> new ScriptType(String.class, Codec.STRING));
    public static final Holder<ScriptType> MAP = MINECRAFT_REGISTRAR.register("map", () -> new ScriptType(Map.class, null, Map.of(), true));
    public static final Holder<ScriptType> SET = MINECRAFT_REGISTRAR.register("set", () -> new ScriptType(Set.class, null, Map.of(), true));
    public static final Holder<ScriptType> NUMERIC_COMPARATOR = MINECRAFT_REGISTRAR.register("comparator", () -> new ScriptType(Comparator.class, ScriptComparators.CODEC, Map.of(), false));

    // Physics

    public static final Holder<ScriptType> VEC3D = MINECRAFT_REGISTRAR.register("vec3d", () -> new ScriptType(Vec3.class, Vec3.CODEC));

    // Minecraft

    public static final Holder<ScriptType> LEVEL = MINECRAFT_REGISTRAR.register("level", () -> new ScriptType(Level.class));

    public static final Holder<ScriptType> ENTITY = MINECRAFT_REGISTRAR.register("entity",
        () -> new ScriptType(Entity.class));

    public static final Holder<ScriptType> LIVING_ENTITY = MINECRAFT_REGISTRAR.register("living_entity",
        () -> new ScriptType(LivingEntity.class));

    public static final Holder<ScriptType> PLAYER = MINECRAFT_REGISTRAR.register("player",
        () -> new ScriptType(Player.class));

    public static final Holder<ScriptType> EQUIPMENT_SLOT = MINECRAFT_REGISTRAR.register("equipment_slot",
        () -> new ScriptType(EquipmentSlot.class, EquipmentSlot.CODEC));

    // ************************************************
    // Akythera API
    // ************************************************

    public static final ModPlatform.Registrar<ScriptType> AKYTHERA_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_TYPE_KEY, Akythera.MODID);

    // Animation API

    public static final Holder<ScriptType> ANIMATION_LAYER = AKYTHERA_REGISTRAR.register("animation_layer",
        () -> new ScriptType(AnimationLayer.class));
    public static final Holder<ScriptType> ANIM_INTERFACE_KEY = AKYTHERA_REGISTRAR.register("anim_interface_key",
        () -> new ScriptType(AnimationInterfaceKey.class, AnimationInterfaceKey.ENUM_MANAGER.getNameCodec(), Map.of(), false));
    public static final Holder<ScriptType> POSE = AKYTHERA_REGISTRAR.register("pose",
        () -> new ScriptType(Pose.class));

    // Entity Patch

    public static final Holder<ScriptType> ENTITY_PATCH = AKYTHERA_REGISTRAR.register("entity_patch",
        () -> new ScriptType(EntityPatch.class, null,
            Map.of(
                Entity.class, (Function<EntityPatch<?>, Entity>)EntityPatch::getOriginal
            ),
            false));

    public static final Holder<ScriptType> LIVING_ENTITY_PATCH = AKYTHERA_REGISTRAR.register("living_entity_patch",
        () -> new ScriptType(LivingEntityPatch.class, null,
            Map.of(
                LivingEntity.class, (Function<LivingEntityPatch<?>, LivingEntity>)LivingEntityPatch::getOriginal,
                Entity.class, (Function<LivingEntityPatch<?>, Entity>)LivingEntityPatch::getOriginal
            ),
            false));

    public static final Holder<ScriptType> PLAYER_PATCH = AKYTHERA_REGISTRAR.register("player_patch",
        () -> new ScriptType(PlayerPatch.class, null,
            Map.of(
                Player.class, (Function<PlayerPatch, Player>)PlayerPatch::getOriginal,
                LivingEntity.class, (Function<PlayerPatch, LivingEntity>)PlayerPatch::getOriginal,
                Entity.class, (Function<PlayerPatch, Entity>)PlayerPatch::getOriginal
            ),
            false));

    // Item Patch

    public static final Holder<ScriptType> ITEM_PATCH = AKYTHERA_REGISTRAR.register("item_patch",
        () -> new ScriptType(ItemPatch.class));

    // EnumerableDataBlocks

    public static final Holder<ScriptType> ANIM_LAYER_SIGNATURE = AKYTHERA_REGISTRAR.register("anim_layer_signature",
        () -> new ScriptType(AnimationLayerSignature.class, AnimationLayerSignature.ENUM_MANAGER.getNameCodec(), Map.of(), false));

    public static final Holder<ScriptType> WEAPON_STANCE = AKYTHERA_REGISTRAR.register("weapon_stance",
        () -> new ScriptType(WeaponStance.class, WeaponStance.ENUM_MANAGER.getNameCodec(), Map.of(), false));
}
