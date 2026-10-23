package com.yesman.akythera.api.data.modularize;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.impl.ScriptComparators;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.api.world.ability.ActivationType;
import com.yesman.akythera.api.world.ability.InstantiationScope;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.client.api.data.modularize.AkytheraClientScriptTypes;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.driver.LayeredBlendPerBoneDriver;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class AkytheraScriptTypes {
    private AkytheraScriptTypes() {}

    public static final ModPlatform.Registrar<ScriptType> MINECRAFT_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_TYPE_KEY, "minecraft");

    // primitives

    public static final Holder<ScriptType> BYTE = MINECRAFT_REGISTRAR.register("byte", () -> new ScriptType(Byte.class, Codec.BYTE, ByteBufCodecs.BYTE));
    public static final Holder<ScriptType> SHORT = MINECRAFT_REGISTRAR.register("short", () -> new ScriptType(Short.class, Codec.SHORT,
        Map.of(
            Byte.class, (Function<Short, Byte>)Short::byteValue
        ),
        ByteBufCodecs.SHORT
    ));
    public static final Holder<ScriptType> INTEGER = MINECRAFT_REGISTRAR.register("integer", () -> new ScriptType(Integer.class, Codec.INT,
        Map.of(
            Byte.class, (Function<Integer, Byte>)Integer::byteValue,
            Short.class, (Function<Integer, Short>)Integer::shortValue
        ),
        ByteBufCodecs.VAR_INT
    ));
    public static final Holder<ScriptType> LONG = MINECRAFT_REGISTRAR.register("long", () -> new ScriptType(Long.class, Codec.LONG,
        Map.of(
            Byte.class, (Function<Long, Byte>)Long::byteValue,
            Short.class, (Function<Long, Short>)Long::shortValue,
            Integer.class, (Function<Long, Integer>)Long::intValue
        ),
        ByteBufCodecs.VAR_LONG
    ));
    public static final Holder<ScriptType> FLOAT = MINECRAFT_REGISTRAR.register("float", () -> new ScriptType(Float.class, Codec.FLOAT,
        Map.of(
            Byte.class, (Function<Float, Byte>)Float::byteValue,
            Short.class, (Function<Float, Short>)Float::shortValue,
            Integer.class, (Function<Float, Integer>)Float::intValue,
            Long.class, (Function<Float, Long>)Float::longValue
        ),
        ByteBufCodecs.FLOAT
    ));
    public static final Holder<ScriptType> DOUBLE = MINECRAFT_REGISTRAR.register("double", () -> new ScriptType(Double.class, Codec.DOUBLE,
        Map.of(
            Byte.class, (Function<Double, Byte>)Double::byteValue,
            Short.class, (Function<Double, Short>)Double::shortValue,
            Integer.class, (Function<Double, Integer>)Double::intValue,
            Long.class, (Function<Double, Long>)Double::longValue,
            Float.class, (Function<Double, Float>)Double::floatValue
        ),
        ByteBufCodecs.DOUBLE
    ));
    public static final Holder<ScriptType> BOOLEAN = MINECRAFT_REGISTRAR.register("boolean", () -> new ScriptType(Boolean.class, Codec.BOOL, ByteBufCodecs.BOOL));

    // java.lang

    public static final Holder<ScriptType> STRING = MINECRAFT_REGISTRAR.register("string", () -> new ScriptType(String.class, Codec.STRING, ByteBufCodecs.STRING_UTF8));
    public static final Holder<ScriptType> MAP = MINECRAFT_REGISTRAR.register("map", () -> new ScriptType(Map.class, null));
    public static final Holder<ScriptType> SET = MINECRAFT_REGISTRAR.register("set", () -> new ScriptType(Set.class, null));
    public static final Holder<ScriptType> NUMERIC_COMPARATOR = MINECRAFT_REGISTRAR.register("comparator", () -> new ScriptType(Comparator.class, ScriptComparators.CODEC));

    // Physics

    public static final Holder<ScriptType> VEC3D = MINECRAFT_REGISTRAR.register("vec3d", () -> new ScriptType(Vec3.class, Vec3.CODEC, Vec3.STREAM_CODEC));

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

    /// The client registers [AkytheraClientScriptTypes#renderType]. A dedicated server, which has no render
    /// types, registers a placeholder under the same id.
    public static final Holder<ScriptType> RENDER_TYPE = MINECRAFT_REGISTRAR.register("render_type",
        () -> ModPlatformProvider.get().getPhysicalSide().isClient()
            ? AkytheraClientScriptTypes.renderType()
            : new ScriptType(Object.class));

    // ************************************************
    // Akythera API
    // ************************************************

    public static final ModPlatform.Registrar<ScriptType> AKYTHERA_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_TYPE_KEY, Akythera.MODID);

    // Animation API

    public static final Holder<ScriptType> ANIM_INTERFACE_KEY = AKYTHERA_REGISTRAR.register("anim_interface_key",
        () -> new ScriptType(AnimationInterfaceKey.class, AnimationInterfaceKey.ENUM_MANAGER.getNameCodec()));
    public static final Holder<ScriptType> ANIM_MONTAGE = AKYTHERA_REGISTRAR.register("anim_montage",
        () -> ScriptType.registryEntry(AnimationMontage.class, AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY));

    // Entity Patch

    public static final Holder<ScriptType> ENTITY_PATCH = AKYTHERA_REGISTRAR.register("entity_patch",
        () -> new ScriptType(EntityPatch.class, null,
            Map.of(
                Entity.class, (Function<EntityPatch<?>, Entity>)EntityPatch::getOriginal
            )
        ));

    public static final Holder<ScriptType> LIVING_ENTITY_PATCH = AKYTHERA_REGISTRAR.register("living_entity_patch",
        () -> new ScriptType(LivingEntityPatch.class, null,
            Map.of(
                LivingEntity.class, (Function<LivingEntityPatch<?>, LivingEntity>)LivingEntityPatch::getOriginal,
                Entity.class, (Function<LivingEntityPatch<?>, Entity>)LivingEntityPatch::getOriginal
            )
        ));

    public static final Holder<ScriptType> PLAYER_PATCH = AKYTHERA_REGISTRAR.register("player_patch",
        () -> new ScriptType(PlayerPatch.class, null,
            Map.of(
                Player.class, (Function<PlayerPatch, Player>)PlayerPatch::getOriginal,
                LivingEntity.class, (Function<PlayerPatch, LivingEntity>)PlayerPatch::getOriginal,
                Entity.class, (Function<PlayerPatch, Entity>)PlayerPatch::getOriginal
            )
        ));

    // Item Patch

    // Ability

    public static final Holder<ScriptType> ABILITY_BLUEPRINT = AKYTHERA_REGISTRAR.register("ability_blueprint",
        () -> ScriptType.registryEntry(AbilityBlueprint.class, AkytheraRegistries.DataPack.ABILITY_KEY));

    public static final Holder<ScriptType> ABILITY_SYSTEM_COMPONENT = AKYTHERA_REGISTRAR.register("ability_system_component",
        () -> new ScriptType(AbilitySystemComponent.class, null,
            Map.of(
                LivingEntityPatch.class, (Function<AbilitySystemComponent, LivingEntityPatch<?>>)AbilitySystemComponent::getOwner
            )
        ));

    public static final Holder<ScriptType> ABILITY_EFFECT = AKYTHERA_REGISTRAR.register("ability_effect",
        () -> ScriptType.registryEntry(AbilityEffect.class, AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY));

    public static final Holder<ScriptType> ACTIVATION_TYPE = AKYTHERA_REGISTRAR.register("activation_type",
        () -> new ScriptType(ActivationType.class, ParseUtils.enumCodec(ActivationType.class)));

    public static final Holder<ScriptType> INSTANTIATION_SCOPE = AKYTHERA_REGISTRAR.register("instantiation_scope",
        () -> new ScriptType(InstantiationScope.class, ParseUtils.enumCodec(InstantiationScope.class)));

    public static final Holder<ScriptType> ABILITY_TAGS = AKYTHERA_REGISTRAR.register("ability_tags",
        () -> new ScriptType(SystematicAbility.Tags.class, SystematicAbility.Tags.CODEC));

    public static final Holder<ScriptType> ABILITY_TRIGGER = AKYTHERA_REGISTRAR.register("ability_trigger",
        () -> new ScriptType(SystematicAbility.Trigger.class, SystematicAbility.Trigger.CODEC));

    // Input

    public static final Holder<ScriptType> INPUT_ACTION = AKYTHERA_REGISTRAR.register("input_action",
        () -> ScriptType.registryEntry(InputAction.class, AkytheraRegistries.DataPack.INPUT_ACTION_KEY));

    // EnumerableDataBlocks

    public static final Holder<ScriptType> ANIM_LAYER_SIGNATURE = AKYTHERA_REGISTRAR.register("anim_layer_signature",
        () -> new ScriptType(AnimationLayerSignature.class, AnimationLayerSignature.ENUM_MANAGER.getNameCodec()));

    public static final Holder<ScriptType> WEAPON_STANCE = AKYTHERA_REGISTRAR.register("weapon_stance",
        () -> new ScriptType(WeaponStance.class, WeaponStance.ENUM_MANAGER.getNameCodec()));

    public static final Holder<ScriptType> ABILITY_TAG = AKYTHERA_REGISTRAR.register("ability_tag",
        () -> new ScriptType(AbilityTag.class, AbilityTag.ENUM_MANAGER.getNameCodec()));

    public static final Holder<ScriptType> ABILITY_TAG_SET = AKYTHERA_REGISTRAR.register("ability_tag_set",
        () -> new ScriptType(AbilityTagSet.class, AbilityTagSet.CODEC));

    public static final Holder<ScriptType> ABILITY_SLOT = AKYTHERA_REGISTRAR.register("ability_slot",
        () -> new ScriptType(AbilitySlot.class, AbilitySlot.ENUM_MANAGER.getNameCodec()));

    // Math

    public static final Holder<ScriptType> QUATERNIOND = AKYTHERA_REGISTRAR.register("quaterniond", () -> new ScriptType(Quaterniond.class, QuaternionUtils.CODEC));
    public static final Holder<ScriptType> TRANSFORM = AKYTHERA_REGISTRAR.register("transform", () -> new ScriptType(Transform.class, Transform.CODEC));

    // Animation

    public static final Holder<ScriptType> LAYER_SETUP = AKYTHERA_REGISTRAR.register("layer_setup",
        () -> new ScriptType(LayeredBlendPerBoneDriver.LayerSetup.class, LayeredBlendPerBoneDriver.LayerSetup.CODEC));

    // Widget

    public static final Holder<ScriptType> WIDGET_BLUEPRINT = AKYTHERA_REGISTRAR.register("widget_blueprint",
        () -> ScriptType.registryEntry(WidgetBlueprint.class, AkytheraRegistries.DataPack.WIDGET_KEY));

    public static final Holder<ScriptType> LOCALIZED_MESSAGE = AKYTHERA_REGISTRAR.register("localized_message",
        () -> new ScriptType(LocalizedMessage.class, LocalizedMessage.CODEC));

    public static final Holder<ScriptType> COLOR = AKYTHERA_REGISTRAR.register("rgba",
        () -> new ScriptType(RGBA.class, RGBA.INT_LIST_CODEC));

    public static final Holder<ScriptType> WIDGET_IMAGE = AKYTHERA_REGISTRAR.register("widget_image",
        () -> new ScriptType(WidgetImage.class, WidgetImage.CODEC));

    // Particle

    public static final Holder<ScriptType> PARTICLE_SYSTEM_BLUEPRINT = AKYTHERA_REGISTRAR.register("particle_system_blueprint",
        () -> ScriptType.registryEntry(ParticleSystemBlueprint.class, AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY));

    public static final Holder<ScriptType> PARTICLE_BLUEPRINT = AKYTHERA_REGISTRAR.register("particle_blueprint",
        () -> ScriptType.registryEntry(ParticleBlueprint.class, AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY));

    public static final Holder<ScriptType> MOUSE_BUTTON_EVENT = registerGuiType("mouse_button_event", "net.minecraft.client.input.MouseButtonEvent");
    public static final Holder<ScriptType> KEY_EVENT = registerGuiType("key_event", "net.minecraft.client.input.KeyEvent");
    public static final Holder<ScriptType> CHARACTER_EVENT = registerGuiType("character_event", "net.minecraft.client.input.CharacterEvent");
    public static final Holder<ScriptType> PREEDIT_EVENT = registerGuiType("preedit_event", "net.minecraft.client.input.PreeditEvent");

    /// Registers [AkytheraClientScriptTypes#guiType] of `className` under `name` on a physical client. A dedicated
    /// server, which lacks the client classes, registers a placeholder under the same id.
    private static Holder<ScriptType> registerGuiType(final String name, final String className) {
        return AKYTHERA_REGISTRAR.register(name, () -> ModPlatformProvider.get().getPhysicalSide().isClient()
            ? AkytheraClientScriptTypes.guiType(className)
            : new ScriptType(Object.class));
    }
}
