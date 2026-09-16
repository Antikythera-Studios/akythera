package com.yesman.akythera.api.data.modularize;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.ability.AbilityEffectInstance;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTaskHandle;
import com.yesman.akythera.api.world.ability.DataDrivenAbility;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AdditiveBlendDriver;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.EmptyAnimationDriver;
import com.yesman.akythera.core.animation.driver.LayeredBlendPerBoneDriver;
import com.yesman.akythera.core.animation.driver.PoseBlendDriver;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.*;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings({ "rawtypes", "unchecked" })
public final class AkytheraScriptFunctions {
    private AkytheraScriptFunctions() {}

    public static final ModPlatform.Registrar<ScriptFunction> MINECRAFT_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_FUNCTION_KEY, "minecraft");

    public static final ModPlatform.Registrar<ScriptFunction> AKYTHERA_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_FUNCTION_KEY, Akythera.MODID);

    static {
        try {
            // Utils
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, MathUtils.class, "Math", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, Matrix4fUtils.class, "Math/Matrix", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, ParseUtils.class, "Parse", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, QuaternionUtils.class, "Math/Quaternion", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, VectorUtils.class, "Math/Vector", ScriptFunction.Usage.SCRIPT);

            // Animation
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AnimationLayer.class, "Animation/Animation Layer", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, Animator.class, "Animation/Animator", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AnimationDriver.class, "Animation/Animation Driver", ScriptFunction.Usage.SCRIPT);

            // Entity & Entity patches
            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, EntityPatch.class, "World/Entity", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, LivingEntityPatch.class, "World/Entity", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, PlayerPatch.class, "World/Entity", ScriptFunction.Usage.SCRIPT);

            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AbilitySystemComponent.class, "Ability", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AbilityInstance.class, "Ability", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AbilityEffectInstance.class, "Ability/Effect", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AbilityTaskHandle.class, "Ability/Task", ScriptFunction.Usage.SCRIPT);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed at scanning and registering modular function", e);
        }
    }

    // ***********************************************
    // Entity Patch Constructors (Code only)
    // ***********************************************

    public static final Holder<ScriptFunction> ENTITY_PATCH_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("entity_patch_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Holder.Reference, Entity, EntityPatch>)EntityPatch::new,
            FunctionSignature.create(EntityPatch.class, List.of("blueprint", "original"), Holder.Reference.class, Entity.class),
            "Entity Patch",
            "Entity Patch",
            "Components, flows and variables, for any entity",
            true,
            ScriptFunction.Usage.ENTITY_PATCH_CTOR
        )
    );

    public static final Holder<ScriptFunction> LIVING_ENTITY_PATCH_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("living_entity_patch_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Holder.Reference, LivingEntity, LivingEntityPatch>)LivingEntityPatch::new,
            FunctionSignature.create(LivingEntityPatch.class, List.of("blueprint", "original"), Holder.Reference.class, LivingEntity.class),
            "Living Entity Patch",
            "Entity Patch",
            "Adds the living-entity surface",
            true,
            ScriptFunction.Usage.ENTITY_PATCH_CTOR
        )
    );

    public static final Holder<ScriptFunction> PLAYER_PATCH_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("player_patch_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Holder.Reference, Player, PlayerPatch>)PlayerPatch::new,
            FunctionSignature.create(PlayerPatch.class, List.of("blueprint", "original"), Holder.Reference.class, Player.class),
            "Player Patch",
            "Entity Patch",
            "Adds the player surface",
            true,
            ScriptFunction.Usage.ENTITY_PATCH_CTOR
        )
    );

    // ***********************************************
    // Ability constructors
    // ***********************************************

    public static final Holder<ScriptFunction> SYSTEMATIC_ABILITY_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("systematic_ability_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Holder.Reference, SystematicAbility>)SystematicAbility::new,
            FunctionSignature.create(SystematicAbility.class, List.of("blueprint"), Holder.Reference.class),
            "Systematic Ability",
            "Ability",
            "Activation type, cost, cooldown, tags and triggers, filled in by the editor",
            true,
            ScriptFunction.Usage.ABILITY_CTOR
        )
    );

    public static final Holder<ScriptFunction> DATA_DRIVEN_ABILITY_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("data_driven_ability_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Holder.Reference, DataDrivenAbility>)DataDrivenAbility::new,
            FunctionSignature.create(DataDrivenAbility.class, List.of("blueprint"), Holder.Reference.class),
            "Data Driven Ability",
            "Ability",
            "Runs the blueprint's graphs instead of Java",
            true,
            ScriptFunction.Usage.ABILITY_CTOR
        )
    );

    // ***********************************************
    // Player skin
    // ***********************************************

    public static final Holder<ScriptFunction> PLAYER_SKIN_MODEL = MINECRAFT_REGISTRAR.register("player_skin_model", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<PlayerSkin, PlayerModelType>) skin -> skin == null ? PlayerModelType.WIDE : skin.model(),
            FunctionSignature.create(PlayerModelType.class, List.of("playerSkin"), PlayerSkin.class),
            "Model Type",
            "Online",
            "The skin's model type. A skin that has not resolved yet reads as WIDE",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> IS_SLIM_PLAYER_MODEL = MINECRAFT_REGISTRAR.register("is_slim_player_model", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<PlayerModelType, Boolean>) model -> model == PlayerModelType.SLIM,
            FunctionSignature.create(Boolean.class, List.of("modelType"), PlayerModelType.class),
            "Is Slim Model",
            "Online",
            "Whether the model type is the slim (3px arm) variant",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Selection
    // ***********************************************

    /// Untyped on both branches: the engine only picks a reference, and the editor's
    /// [ScriptFunction.Usage#SCRIPT] node keeps its wildcard pins in step so a graph never wires two
    /// different types into it.
    public static final Holder<ScriptFunction> SELECT = AKYTHERA_REGISTRAR.register("select", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Boolean, Object, Object, Object>) (condition, whenTrue, whenFalse) ->
                Boolean.TRUE.equals(condition) ? whenTrue : whenFalse,
            FunctionSignature.create(Object.class, List.of("condition", "whenTrue", "whenFalse"), Boolean.class, Object.class, Object.class),
            "Select",
            "Logic",
            "Yields one of two values by a boolean",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Anim Graph Nodes
    // ***********************************************

    public static final Holder<ScriptFunction> STORE_ANIM_GRAPH_LAYER = AKYTHERA_REGISTRAR.register("store_anim_graph_layer", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<Map, AnimationDriver>) (layerMap, driver) -> layerMap.put("anim_graph", new AnimationLayer(driver)),
            FunctionSignature.create(Void.class, List.of("layerMap", "driver"), Map.class, AnimationDriver.class),
            "Store Anim Graph Layer",
            "Animation/Pose",
            "Stores the compiled root driver as the anim-graph layer",
            false,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> ANIM_GRAPH_POSE = AKYTHERA_REGISTRAR.register("anim_graph_pose", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Map, Pose>) layerMap -> {
                AnimationLayer layer = (AnimationLayer) layerMap.get("anim_graph");
                return layer == null ? Pose.EMPTY_POSE : layer.getPose();
            },
            FunctionSignature.create(Pose.class, List.of("layerMap"), Map.class),
            "Anim Graph Pose",
            "Animation/Pose",
            "Reads the anim-graph layer's pose",
            false,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> PLAY_ANIM_SEQUENCE = AKYTHERA_REGISTRAR.register("play_anim_sequence", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<String, AnimationDriver>) id ->
                AnimatorScript.instantiateAnimSequence(ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, Identifier.parse(id))),
            FunctionSignature.create(AnimationDriver.class, List.of("sequenceId"), String.class),
            "Sequence Player",
            "Animation/Pose",
            "Plays an animation sequence by id",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> PLAY_BLEND_SPACE_2D = AKYTHERA_REGISTRAR.register("play_blend_space_2d", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<String, Supplier, Supplier, AnimationDriver>) (id, x, y) ->
                AnimatorScript.instantiateBlendSpace2D(
                    ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, Identifier.parse(id)),
                    (Supplier<Float>) x, (Supplier<Float>) y
                ),
            FunctionSignature.create(AnimationDriver.class, List.of("blendSpaceId", "x", "y"), String.class, Supplier.class, Supplier.class),
            "Blendspace Player",
            "Animation/Pose",
            "Plays a 2D blendspace by id",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> EMPTY_DRIVER = AKYTHERA_REGISTRAR.register("empty_driver", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Supplier<AnimationDriver>) () -> EmptyAnimationDriver.INSTANCE,
            FunctionSignature.create(AnimationDriver.class, List.of()),
            "Empty Pose",
            "Animation/Pose",
            "An empty animation driver (no pose)",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> PLAY_ANIM_SEQUENCE_INTERFACE = AKYTHERA_REGISTRAR.register("play_anim_sequence_interface", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<BlendingAnimator, AnimationInterfaceKey, String, AnimationDriver>) (animator, interfaceKey, id) ->
                AnimatorScript.instantiateAnimSequenceInterface(
                    animator, interfaceKey,
                    ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, Identifier.parse(id))
                ),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "interfaceKey", "sequenceId"), BlendingAnimator.class, AnimationInterfaceKey.class, String.class),
            "Anim Sequence Animation Interface",
            "Animation/Pose",
            "Plays a replaceable animation sequence registered under an animation interface key",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> PLAY_BLEND_SPACE_2D_INTERFACE = AKYTHERA_REGISTRAR.register("play_blend_space_2d_interface", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationInterfaceKey, String, Supplier, Supplier, AnimationDriver>) (animator, interfaceKey, id, x, y) ->
                AnimatorScript.instantiateBlendSpace2DInterface(
                    animator, interfaceKey,
                    ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, Identifier.parse(id)),
                    (Supplier<Float>) x, (Supplier<Float>) y
                ),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "interfaceKey", "blendSpaceId", "x", "y"), BlendingAnimator.class, AnimationInterfaceKey.class, String.class, Supplier.class, Supplier.class),
            "Blend Space 2D Animation Interface",
            "Animation/Pose",
            "Plays a replaceable 2D blendspace registered under an animation interface key",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> BLEND_TWO_POSES = AKYTHERA_REGISTRAR.register("blend_two_poses", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<AnimationDriver, AnimationDriver, Supplier, AnimationDriver>) (a, b, alpha) ->
                new PoseBlendDriver(a, b, (Supplier<Float>)alpha),
            FunctionSignature.create(AnimationDriver.class, List.of("a", "b", "alpha"), AnimationDriver.class, AnimationDriver.class, Supplier.class),
            "Blend",
            "Animation/Pose",
            "Interpolates between two poses, alpha 0 being A and 1 being B",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> APPLY_ADDITIVE = AKYTHERA_REGISTRAR.register("apply_additive", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<AnimationDriver, AnimationDriver, Supplier, AnimationDriver>) (base, additive, alpha) ->
                new AdditiveBlendDriver(base, additive, (Supplier<Float>)alpha),
            FunctionSignature.create(AnimationDriver.class, List.of("base", "additive", "alpha"), AnimationDriver.class, AnimationDriver.class, Supplier.class),
            "Apply Additive",
            "Animation/Pose",
            "Lays an additive pose over a base pose by an alpha",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> LAYERED_BLEND_PER_BONE = AKYTHERA_REGISTRAR.register("layered_blend_per_bone", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationDriver, AnimationDriver, String, Integer, AnimationDriver>)
                LayeredBlendPerBoneDriver::new,
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "base", "layered", "boneName", "depth"), BlendingAnimator.class, AnimationDriver.class, AnimationDriver.class, String.class, Integer.class),
            "Layered Blend per Bone",
            "Animation/Pose",
            "Hands the skeleton branch below a bone over to a second pose",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    // ***********************************************
    // java.lang.Math / net.minecraft.util.Mth
    // ***********************************************

    public static final Holder<ScriptFunction> ROT_LERP = MINECRAFT_REGISTRAR.register("rot_lerp", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Float, Float, Float, Float>) Mth::rotLerp,
            FunctionSignature.create(Float.class, List.of("delta", "start", "end"), Float.class, Float.class, Float.class),
            "Lerp Rotation",
            "Math/Rotation",
            "Linearly interpolates the rotation between -180 and 180",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> CLAMP_INT = MINECRAFT_REGISTRAR.register("clampi", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Integer, Integer, Integer, Integer>) Mth::clamp,
            FunctionSignature.create(Integer.class, List.of("value", "min", "max"), Integer.class, Integer.class, Integer.class),
            "Clamp(integer)",
            "Math/Clamp",
            "Clamps the integer value between a minimum and a maximum",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> CLAMP_LONG = MINECRAFT_REGISTRAR.register("clampl", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Long, Long, Long, Long>) Mth::clamp,
            FunctionSignature.create(Long.class, List.of("value", "min", "max"), Long.class, Long.class, Long.class),
            "Clamp(long)",
            "Math/Clamp",
            "Clamps the long value between a minimum and a maximum",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> CLAMP_FLOAT = MINECRAFT_REGISTRAR.register("clampf", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Float, Float, Float, Float>) Mth::clamp,
            FunctionSignature.create(Float.class, List.of("value", "min", "max"), Float.class, Float.class, Float.class),
            "Clamp(float)",
            "Math/Clamp",
            "Clamps the float value between a minimum and a maximum",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> CLAMP_DOUBLE = MINECRAFT_REGISTRAR.register("clampd", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Double, Double, Double, Double>) Mth::clamp,
            FunctionSignature.create(Double.class, List.of("value", "min", "max"), Double.class, Double.class, Double.class),
            "Clamp(double)",
            "Math/Clamp",
            "Clamps the double value between a minimum and a maximum",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // net.minecraft.world.phys.Vec3
    // ***********************************************

    public static final Holder<ScriptFunction> VEC3_LENGTH = MINECRAFT_REGISTRAR.register("vec3_length", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Vec3, Double>) Vec3::length,
            FunctionSignature.create(Double.class, List.of("vec3"), Vec3.class),
            "Length of Vector",
            "Math/Vector",
            "Returns the length of the vector",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> VEC3_LENGTH_SQR = MINECRAFT_REGISTRAR.register("vec3_length_sqr", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Vec3, Double>) Vec3::lengthSqr,
            FunctionSignature.create(Double.class, List.of("vec3"), Vec3.class),
            "Squared Length of Vector",
            "Math/Vector",
            "Returns the squared length of the vector",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> VEC3_ADD = MINECRAFT_REGISTRAR.register("vec3_add", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Vec3, Vec3, Vec3>) Vec3::add,
            FunctionSignature.create(Vec3.class, List.of("a", "b"), Vec3.class, Vec3.class),
            "Vector Add (+)",
            "Math/Vector",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> VEC3_SUBTRACT = MINECRAFT_REGISTRAR.register("vec3_subtract", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Vec3, Vec3, Vec3>) Vec3::subtract,
            FunctionSignature.create(Vec3.class, List.of("a", "b"), Vec3.class, Vec3.class),
            "Vector Subtract (-)",
            "Math/Vector",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> VEC3_DOT = MINECRAFT_REGISTRAR.register("vec3_dot", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Vec3, Vec3, Double>) Vec3::dot,
            FunctionSignature.create(Vec3.class, List.of("a", "b"), Vec3.class, double.class),
            "Vector Dot",
            "Math/Vector",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // java.lang.Map
    // ***********************************************

    public static final Holder<ScriptFunction> MAP_GET = MINECRAFT_REGISTRAR.register("map_get", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Map, Object, Object>) Map::get,
            FunctionSignature.create(Object.class, List.of("map", "key"), Map.class, Object.class),
            "Get",
            "Map",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_PUT = MINECRAFT_REGISTRAR.register("map_put", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriConsumer<Map, Object, Object>) Map::put,
            FunctionSignature.create(Void.class, List.of("map", "key", "value"), Map.class, Object.class, Object.class),
            "Put",
            "Map",
            null,
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_CONTAINS = MINECRAFT_REGISTRAR.register("map_contains", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Map, Object, Boolean>) Map::containsKey,
            FunctionSignature.create(Boolean.class, List.of("map", "key"), Map.class, Object.class),
            "Contains",
            "Map",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // java.lang.String
    // ***********************************************

    public static final Holder<ScriptFunction> SUBSTR1 = MINECRAFT_REGISTRAR.register("substring1", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<String, Integer, Integer, String>)String::substring,
            FunctionSignature.create(String.class, List.of("beginIndex", "endIndex", "string"), int.class, int.class, String.class),
            "Substring",
            "String",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SUBSTR2 = MINECRAFT_REGISTRAR.register("substring2", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<String, Integer, String>)String::substring,
            FunctionSignature.create(String.class, List.of("beginIndex", "string"), int.class, String.class),
            "Substring",
            "String",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Position providers
    //
    // Method signature: (EntityPatch), return Vec3
    //
    // Used by particle generation, motion warping
    // TODO: Remove, move to Epic Fight.
    // ***********************************************

    public static final Holder<ScriptFunction> RANDOM_LOCATION_IN_BOUNDING_BOX = AKYTHERA_REGISTRAR.register("random_in_bb", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<EntityPatch<?>, Vec3>) entityPatch -> {
                Entity entity = entityPatch.getOriginal();
                RandomSource random = entity.getRandom();
                EntityDimensions dimensions = entity.getDimensions(entity.getPose());

                return entity.position().add(
                    -dimensions.width() * 0.5F + random.nextFloat() * dimensions.width() * 1.2F,
                    -dimensions.height() * 0.5F + random.nextFloat() * dimensions.height() * 1.2F + dimensions.height() * 0.2F, // prevents particles generating underground
                    -dimensions.width() * 0.5F + random.nextFloat() * dimensions.width() * 1.2F
                );
            },
            FunctionSignature.create(Vec3.class, List.of("entityPatch"), EntityPatch.class),
            "Random Location in Bounding Box",
            "World/Entity",
            "Returns a random location inside the entity's bounding box",
            true,
            ScriptFunction.Usage.PARTICLE_GENERATION
        )
    );

    public static final Holder<ScriptFunction> BEST_FIT_MELEE_DESTINATION = AKYTHERA_REGISTRAR.register("best_fit_melee_destination", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<EntityPatch<?>, @Nullable Vec3>) entityPatch -> {
                /*
                if (entityPatch instanceof EnemyPerception enemyPerception && enemyPerception.getTarget() != null) {
                    Vec3 fromAttackerToTarget = enemyPerception.getTarget().position().subtract(entityPatch.getOriginal().position());
                    float distance = entityPatch.getOriginal().distanceTo(enemyPerception.getTarget());

                    if (entityPatch instanceof EquipmentHolder equipmentHolder) {
                        ItemPatch mainhandItemPatch = equipmentHolder.getMainHandItemPatch();

                        if (mainhandItemPatch != null) {
                            // Expand the distance by weapon reach
                            distance += mainhandItemPatch.getReach();
                        }
                    }

                    // TODO: return optimized position
                }
                */
                return null;
            },
            FunctionSignature.create(Vec3.class, List.of("entityPatch"), EntityPatch.class),
            "Melee Destination",
            "World/Entity",
            "Finds an optimized location for melee attacks",
            true,
            ScriptFunction.Usage.MOTION_WARPING
        )
    );
}
