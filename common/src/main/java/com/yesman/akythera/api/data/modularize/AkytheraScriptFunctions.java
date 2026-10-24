package com.yesman.akythera.api.data.modularize;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilityTask;
import com.yesman.akythera.api.world.ability.impl.PlayMontageTask;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.api.world.ability.AbilityEffectInstance;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.api.world.ability.AbilityTaskHandle;
import com.yesman.akythera.api.world.ability.DataDrivenAbility;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.client.api.data.modularize.AkytheraClientScriptFunctions;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AdditiveBlendDriver;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.EmptyAnimationDriver;
import com.yesman.akythera.core.animation.driver.LayeredBlendPerBoneDriver;
import com.yesman.akythera.core.animation.driver.ModifyBoneDriver;
import com.yesman.akythera.core.animation.driver.PlayRateDriver;
import com.yesman.akythera.core.animation.driver.PoseBlendDriver;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.math.Transform;
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

import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings({ "rawtypes", "unchecked" })
public final class AkytheraScriptFunctions {
    private AkytheraScriptFunctions() {}

    public static final ModPlatform.Registrar<ScriptFunction> MINECRAFT_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_FUNCTION_KEY, "minecraft");

    public static final ModPlatform.Registrar<ScriptFunction> AKYTHERA_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.SCRIPT_FUNCTION_KEY, Akythera.MODID);

    /// Element and entry slots of the collection functions, bound by the collection they are read from.
    private static final TypeVariable<?> LIST_ELEMENT = List.class.getTypeParameters()[0];
    private static final TypeVariable<?> SET_ELEMENT = Set.class.getTypeParameters()[0];
    private static final TypeVariable<?> MAP_KEY = Map.class.getTypeParameters()[0];
    private static final TypeVariable<?> MAP_VALUE = Map.class.getTypeParameters()[1];

    static {
        try {
            // Utils
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, MathUtils.class, "Math", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, Matrix4fUtils.class, "Math/Matrix", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, ParseUtils.class, "Parse", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, QuaternionUtils.class, "Math/Quaternion", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, VectorUtils.class, "Math/Vector", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, Transform.class, "Math/Transform", ScriptFunction.Usage.SCRIPT);

            // Animation
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AnimationLayer.class, "Animation/Animation Layer", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, Animator.class, "Animation/Animator", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AnimationDriver.class, "Animation/Animation Driver", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, Pose.class, "Animation/Pose", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, ArmatureComponent.class, "Animation/Armature", ScriptFunction.Usage.SCRIPT);

            // Entity & Entity patches
            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, EntityPatch.class, "World/Entity", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, LivingEntityPatch.class, "World/Entity", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, PlayerPatch.class, "World/Entity", ScriptFunction.Usage.SCRIPT);

            ScriptModularizeHelper.scanScriptModulesAndRegister(MINECRAFT_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, ItemPatch.class, "World/Item", ScriptFunction.Usage.SCRIPT);

            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilitySystemComponent.class, "Ability", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, Ability.class, "Ability", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityState.class, "Ability", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityTask.class, "Ability/Task", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityInstance.class, "Ability", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityEffectInstance.class, "Ability/Effect", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityTaskHandle.class, "Ability/Task", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityCue.class, "Ability/Cue", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, AbilityTagSet.class, "Ability/Tag", ScriptFunction.Usage.SCRIPT);

            // Widgets, client-only
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.AkytheraButton", "Widget/Button", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.CheckBox", "Widget/Check Box", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.ComboBox", "Widget/Combo Box", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.AkytheraEditBox", "Widget/Edit Box", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.DragValueBox", "Widget/Drag Value Box", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.TextArea", "Widget/Text Area", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.Image", "Widget/Image", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.ProgressionBar", "Widget/Progression Bar", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.ColorPickerWidget", "Widget/Color Picker", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.AkytheraScreen", "Widget", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.AkytheraWidget", "Widget", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.AkytheraHUD", "Widget", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.Grid", "Widget/Grid", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.HorizontalWidgetContainer", "Widget/Container", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.gui.VerticalWidgetContainer", "Widget/Container", ScriptFunction.Usage.SCRIPT);

            // Particles, client-only
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraParticleSystem", "Particle", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraParticle", "Particle", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraParticles", "Particle", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraSpriteParticle", "Particle/Sprite", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraMeshParticle", "Particle/Mesh", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraStaticMeshParticle", "Particle/Mesh", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.AkytheraSkeletalMeshParticle", "Particle/Mesh", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.module.ParticleInitializerModule", "Particle", ScriptFunction.Usage.SCRIPT);
            ScriptModularizeHelper.scanClientScriptModulesAndRegister(AKYTHERA_REGISTRAR, AkytheraScriptTypes.AKYTHERA_REGISTRAR, "com.yesman.akythera.client.api.particle.module.ParticleUpdateModule", "Particle", ScriptFunction.Usage.SCRIPT);
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

    /// A [SystematicAbility] written in a data pack. It is built as the [DataDrivenAbility] that runs the
    /// blueprint's graphs, and laid out in the editor as the [SystematicAbility] it is.
    public static final Holder<ScriptFunction> SYSTEMATIC_ABILITY_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("systematic_ability_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Holder.Reference, SystematicAbility>)DataDrivenAbility::new,
            FunctionSignature.create(SystematicAbility.class, List.of("blueprint"), Holder.Reference.class),
            "Systematic Ability",
            "Ability",
            "Activation type, cost, cooldown, tags and triggers, and the graphs, filled in by the editor",
            true,
            ScriptFunction.Usage.ABILITY_CTOR
        )
    );

    // ***********************************************
    // Ability task
    // ***********************************************

    public static final Holder<ScriptFunction> PLAY_MONTAGE_TASK_CONSTRUCTOR = AKYTHERA_REGISTRAR.register("play_montage_task_ctor", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<ResourceKey, String, PlayMontageTask>) PlayMontageTask::new,
            FunctionSignature.create(
                PlayMontageTask.class, List.of("montageId", "section"),
                ParseUtils.parameterized(ResourceKey.class, AnimationMontage.class), String.class
            ),
            "Play Montage Task",
            "Ability",
            "A task that accompanies the lifecycle with a played montage",
            true,
            ScriptFunction.Usage.ABILITY_TASK
        )
    );

    // ***********************************************
    // Ability cue
    // ***********************************************

    public static final Holder<ScriptFunction> PLAY_ABILITY_CUE = AKYTHERA_REGISTRAR.register("play_ability_cue", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<AbilitySystemComponent, String, AbilityInstance, Boolean, Boolean, AbilityCue>)
                (component, cueId, source, withState, endsWithAbility) -> component.playCue(
                    ResourceKey.create(AkytheraRegistries.DataPack.ABILITY_CUE_KEY, Identifier.parse(cueId)),
                    source,
                    Boolean.TRUE.equals(withState),
                    Boolean.TRUE.equals(endsWithAbility)
                ),
            FunctionSignature.create(
                AbilityCue.class, List.of("component", "cueId", "source", "withState", "endsWithAbility"),
                AbilitySystemComponent.class, String.class, AbilityInstance.class, Boolean.class, Boolean.class
            ),
            "Play Ability Cue",
            "Ability/Cue",
            "Plays an ability cue by id",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> PLAY_ABILITY_CUE_SCRIPT = AKYTHERA_REGISTRAR.register("play_ability_cue_script", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<AbilitySystemComponent, String, AbilityInstance, Boolean, Boolean, AbilityCue>)
                (component, scriptId, source, withState, endsWithAbility) -> component.playCue(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.getOptional(Identifier.parse(scriptId))
                        .orElseThrow(() -> new IllegalArgumentException("No script type " + scriptId))
                        .type()
                        .asSubclass(AbilityCueScript.class),
                    source,
                    Boolean.TRUE.equals(withState),
                    Boolean.TRUE.equals(endsWithAbility)
                ),
            FunctionSignature.create(
                AbilityCue.class, List.of("component", "scriptId", "source", "withState", "endsWithAbility"),
                AbilitySystemComponent.class, String.class, AbilityInstance.class, Boolean.class, Boolean.class
            ),
            "Play Ability Cue Script",
            "Ability/Cue",
            "Plays a Java ability cue by its script type id",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Player skin
    // ***********************************************

    public static final Holder<ScriptFunction> PLAYER_SKIN_MODEL_TYPE = MINECRAFT_REGISTRAR.register("player_skin_model_type", () ->
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

    public static final Holder<ScriptFunction> PLAYER_SKIN_HAS_CAPE = MINECRAFT_REGISTRAR.register("player_skin_has_cape", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<PlayerSkin, Boolean>) skin -> skin != null && skin.cape() != null,
            FunctionSignature.create(Boolean.class, List.of("playerSkin"), PlayerSkin.class),
            "Has Cape",
            "Online",
            "Whether the skin carries a cape. A skin that has not resolved yet has none",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> PLAYER_SKIN_CAPE_TEXTURE = MINECRAFT_REGISTRAR.register("player_skin_cape_texture", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<PlayerSkin, Identifier>) skin -> skin == null || skin.cape() == null ? null : skin.cape().texturePath(),
            FunctionSignature.create(Identifier.class, List.of("playerSkin"), PlayerSkin.class),
            "Cape Texture",
            "Online",
            "The texture id of the skin's cape, or null when it has none",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Components
    // ***********************************************

    public static final Holder<ScriptFunction> GET_COMPONENT = AKYTHERA_REGISTRAR.register("get_component", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<ComponentAttachable, String, PatchComponent>) (host, path) ->
                host == null || path == null ? null : host.getComponentAt(path),
            FunctionSignature.create(PatchComponent.class, List.of("host", "path"), ComponentAttachable.class, String.class),
            "Get Component",
            "Component",
            "The component at a path of names joined by '/', switched on or off. Null when none sits there",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> IS_COMPONENT_ENABLED = AKYTHERA_REGISTRAR.register("is_component_enabled", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<PatchComponent, Boolean>) component -> component != null && component.isEnabled(),
            FunctionSignature.create(Boolean.class, List.of("component"), PatchComponent.class),
            "Is Component Enabled",
            "Component",
            "Whether the component is switched on. False for a null component",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SET_COMPONENT_ENABLED = AKYTHERA_REGISTRAR.register("set_component_enabled", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<PatchComponent, Boolean>) (component, enabled) -> {
                if (component != null) component.setEnabled(Boolean.TRUE.equals(enabled));
            },
            FunctionSignature.create(Void.class, List.of("component", "enabled"), PatchComponent.class, Boolean.class),
            "Set Component Enabled",
            "Component",
            "Switches the component on or off. Does nothing for a null component",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Animation driver
    // ***********************************************

    public static final Holder<ScriptFunction> IS_PLAYER_ANIMATION_LIBRARY_LOADED = AKYTHERA_REGISTRAR.register("is_player_animation_library_loaded", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Supplier<Boolean>) () -> ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId()),
            FunctionSignature.create(Boolean.class, List.of()),
            "Is Player Animation Library Loaded",
            "Mod Loader",
            "Whether Player Animation Library is installed on this side",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    /// The client registers [AkytheraClientScriptFunctions#prefersPlayerAnimationLibrary]. A dedicated
    /// server, which has no client config, registers a placeholder under the same id that answers false.
    public static final Holder<ScriptFunction> PREFERS_PLAYER_ANIMATION_LIBRARY = AKYTHERA_REGISTRAR.register("prefers_player_animation_library", () ->
        ModPlatformProvider.get().getPhysicalSide().isClient()
            ? AkytheraClientScriptFunctions.prefersPlayerAnimationLibrary()
            : ScriptModularizeHelper.makeCustomScript(
                (Supplier<Boolean>) () -> false,
                FunctionSignature.create(Boolean.class, List.of()),
                "Prefers Player Animation Library",
                "Configs",
                "Whether the client config picks Player Animation Library over the skinned mesh",
                true,
                ScriptFunction.Usage.SCRIPT
            )
    );

    public static final Holder<ScriptFunction> STOP_MONTAGE = AKYTHERA_REGISTRAR.register("stop_montage", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Animator, ResourceKey, Boolean>) Animator::stopPlayingMontage,
            FunctionSignature.create(
                Boolean.class, List.of("animator", "montageId"),
                Animator.class, ParseUtils.parameterized(ResourceKey.class, AnimationMontage.class)
            ),
            "Stop Montage",
            "Animation/Animator",
            "Fades out the montage if it is playing, and returns whether it was",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> ANIM_MONTAGE_KEY = AKYTHERA_REGISTRAR.register("anim_montage_key", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<AnimationMontage, ResourceKey>) montage ->
                ScriptType.entryKey(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, montage).orElse(null),
            FunctionSignature.create(
                ParseUtils.parameterized(ResourceKey.class, AnimationMontage.class), List.of("montage"), AnimationMontage.class
            ),
            "Montage Key",
            "Animation/Animator",
            "The id the montage is registered under",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // Render
    // ***********************************************

    /// The client registers [AkytheraClientScriptFunctions#entityRenderType]. A dedicated server, which has
    /// no render types, registers a placeholder under the same id and parameters that answers null, so a
    /// blueprint naming the function still decodes there.
    public static final Holder<ScriptFunction> ENTITY_RENDER_TYPE = AKYTHERA_REGISTRAR.register("entity_render_type", () ->
        ModPlatformProvider.get().getPhysicalSide().isClient()
            ? AkytheraClientScriptFunctions.entityRenderType()
            : ScriptModularizeHelper.makeCustomScript(
                (BiFunction<String, Boolean, Object>) (texture, translucent) -> null,
                FunctionSignature.create(Object.class, List.of("texture", "translucent"), String.class, Boolean.class),
                "Make Entity Render Type",
                "Render",
                "The entity render type drawing a texture",
                true,
                ScriptFunction.Usage.SCRIPT
            )
    );

    /// The client registers [AkytheraClientScriptFunctions#selfRenderType]. A dedicated server, which has no
    /// render types, registers a placeholder under the same id that answers null.
    public static final Holder<ScriptFunction> SELF_RENDER_TYPE = AKYTHERA_REGISTRAR.register("self_render_type", () ->
        ModPlatformProvider.get().getPhysicalSide().isClient()
            ? AkytheraClientScriptFunctions.selfRenderType()
            : ScriptModularizeHelper.makeCustomScript(
                (Supplier<Object>) () -> null,
                FunctionSignature.create(Object.class, List.of()),
                "Get Entity Render Type",
                "Render",
                "The render type the entity's own model draws with",
                true,
                ScriptFunction.Usage.SCRIPT
            )
    );

    // ***********************************************
    // Widget
    // ***********************************************

    public static final Holder<ScriptFunction> SCREEN_CONSTRUCTOR = registerClientOnly(
        "screen_ctor", AkytheraClientScriptFunctions::screenConstructor, List.of("blueprint", "owner"), ScriptFunction.Usage.WIDGET_CTOR);

    public static final Holder<ScriptFunction> WIDGET_CONSTRUCTOR = registerClientOnly(
        "widget_ctor", AkytheraClientScriptFunctions::widgetConstructor, List.of("blueprint", "owner"), ScriptFunction.Usage.WIDGET_CTOR);

    public static final Holder<ScriptFunction> HUD_CONSTRUCTOR = registerClientOnly(
        "hud_ctor", AkytheraClientScriptFunctions::hudConstructor, List.of("blueprint", "owner"), ScriptFunction.Usage.WIDGET_CTOR);

    public static final Holder<ScriptFunction> OPEN_SCREEN = registerClientOnly(
        "open_screen", AkytheraClientScriptFunctions::openScreen, List.of("blueprint"), ScriptFunction.Usage.SCRIPT);

    public static final Holder<ScriptFunction> ADD_HUD = registerClientOnly(
        "add_hud", AkytheraClientScriptFunctions::addHud, List.of("blueprint"), ScriptFunction.Usage.SCRIPT);

    public static final Holder<ScriptFunction> REMOVE_HUD = registerClientOnly(
        "remove_hud", AkytheraClientScriptFunctions::removeHud, List.of("hud"), ScriptFunction.Usage.SCRIPT);

    public static final Holder<ScriptFunction> SCREEN_TITLE = registerClientOnly(
        "screen_title", AkytheraClientScriptFunctions::screenTitle, List.of("screen"), ScriptFunction.Usage.SCRIPT);

    public static final Holder<ScriptFunction> WIDGET_OWNER = registerClientOnly(
        "widget_owner", AkytheraClientScriptFunctions::widgetOwner, List.of("host"), ScriptFunction.Usage.SCRIPT);

    /// Registers `client` on a physical client. A dedicated server, which lacks the client classes `client` names,
    /// registers a [ScriptModularizeHelper#placeholder] taking `parameterNames` under the same id.
    private static Holder<ScriptFunction> registerClientOnly(
        final String name, final Supplier<ScriptFunction> client, final List<String> parameterNames, final ScriptFunction.Usage usage
    ) {
        return AKYTHERA_REGISTRAR.register(name, () -> ModPlatformProvider.get().getPhysicalSide().isClient()
            ? client.get()
            : ScriptModularizeHelper.placeholder(parameterNames, false, false, name, usage));
    }

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

    public static final Holder<ScriptFunction> IS_NULL = AKYTHERA_REGISTRAR.register("is_null", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Object, Boolean>) value -> value == null,
            FunctionSignature.create(Boolean.class, List.of("value"), Object.class),
            "Is Null",
            "Logic",
            "Whether the value is null",
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
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationDriver, AnimationDriver, LayeredBlendPerBoneDriver.LayerSetup, Supplier, AnimationDriver>) (animator, base, layered, setup, blendWeight) ->
                new LayeredBlendPerBoneDriver(animator, base, layered, setup, (Supplier<Float>)blendWeight),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "base", "layered", "setup", "blendWeight"), BlendingAnimator.class, AnimationDriver.class, AnimationDriver.class, LayeredBlendPerBoneDriver.LayerSetup.class, Supplier.class),
            "Layered Blend per Bone",
            "Animation/Pose",
            "Hands branches of the skeleton over to a second pose, by a weight per bone",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> PLAY_RATE = AKYTHERA_REGISTRAR.register("play_rate", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<AnimationDriver, Supplier, AnimationDriver>) (base, rate) -> new PlayRateDriver(base, (Supplier<Float>)rate),
            FunctionSignature.create(AnimationDriver.class, List.of("base", "rate"), AnimationDriver.class, Supplier.class),
            "Play Rate",
            "Animation/Pose",
            "Plays a pose at a rate, 1 being its own speed",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> MODIFY_BONE_LOCAL = AKYTHERA_REGISTRAR.register("modify_bone_local", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationDriver, String, Supplier, Supplier, AnimationDriver>) (animator, base, boneName, rotation, replace) ->
                new ModifyBoneDriver(animator, base, boneName, ModifyBoneDriver.Space.LOCAL, (Supplier<Vec3>)rotation, (Supplier<Float>)replace),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "base", "boneName", "rotation", "replace"), BlendingAnimator.class, AnimationDriver.class, String.class, Supplier.class, Supplier.class),
            "Modify Bone (Local)",
            "Animation/Pose",
            "Turns a bone by a pitch, yaw and roll in degrees about its own axes, replacing a share of its animated rotation",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> MODIFY_BONE_PARENT = AKYTHERA_REGISTRAR.register("modify_bone_parent", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationDriver, String, Supplier, Supplier, AnimationDriver>) (animator, base, boneName, rotation, replace) ->
                new ModifyBoneDriver(animator, base, boneName, ModifyBoneDriver.Space.PARENT, (Supplier<Vec3>)rotation, (Supplier<Float>)replace),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "base", "boneName", "rotation", "replace"), BlendingAnimator.class, AnimationDriver.class, String.class, Supplier.class, Supplier.class),
            "Modify Bone (Parent)",
            "Animation/Pose",
            "Turns a bone by a pitch, yaw and roll in degrees about the parent bone's axes, replacing a share of its animated rotation",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> MODIFY_BONE_MODEL = AKYTHERA_REGISTRAR.register("modify_bone_model", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationDriver, String, Supplier, Supplier, AnimationDriver>) (animator, base, boneName, rotation, replace) ->
                new ModifyBoneDriver(animator, base, boneName, ModifyBoneDriver.Space.MODEL, (Supplier<Vec3>)rotation, (Supplier<Float>)replace),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "base", "boneName", "rotation", "replace"), BlendingAnimator.class, AnimationDriver.class, String.class, Supplier.class, Supplier.class),
            "Modify Bone (Model)",
            "Animation/Pose",
            "Turns a bone by a pitch, yaw and roll in degrees about the model's axes, replacing a share of its animated and inherited rotation",
            true,
            ScriptFunction.Usage.ANIM_GRAPH
        )
    );

    public static final Holder<ScriptFunction> ORIENTATION_WARP = AKYTHERA_REGISTRAR.register("orientation_warp", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.QuintFunction<BlendingAnimator, AnimationDriver, String, String, Supplier, AnimationDriver>) (animator, base, hipBoneName, spineBoneName, angle) ->
                ModifyBoneDriver.orientationWarp(animator, base, hipBoneName, spineBoneName, (Supplier<Float>)angle),
            FunctionSignature.create(AnimationDriver.class, List.of("animator", "base", "hipBoneName", "spineBoneName", "angle"), BlendingAnimator.class, AnimationDriver.class, String.class, String.class, Supplier.class),
            "Orientation Warp",
            "Animation/Pose",
            "Turns the hips by an angle in degrees and keeps the spine facing forward",
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
            FunctionSignature.create(Double.class, List.of("a", "b"), Vec3.class, Vec3.class),
            "Vector Dot",
            "Math/Vector",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // java.util.List
    // ***********************************************

    public static final Holder<ScriptFunction> LIST_GET = MINECRAFT_REGISTRAR.register("list_get", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<List, Integer, Object>) List::get,
            FunctionSignature.create(LIST_ELEMENT, List.of("list", "index"), ParseUtils.parameterized(List.class, LIST_ELEMENT), Integer.class),
            "Get",
            "List",
            "The element at an index",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_SET = MINECRAFT_REGISTRAR.register("list_set", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriConsumer<List, Integer, Object>) List::set,
            FunctionSignature.create(Void.class, List.of("list", "index", "element"), ParseUtils.parameterized(List.class, LIST_ELEMENT), Integer.class, LIST_ELEMENT),
            "Set",
            "List",
            "Replaces the element at an index",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_ADD = MINECRAFT_REGISTRAR.register("list_add", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<List, Object>) List::add,
            FunctionSignature.create(Void.class, List.of("list", "element"), ParseUtils.parameterized(List.class, LIST_ELEMENT), LIST_ELEMENT),
            "Add",
            "List",
            "Appends an element to the end",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_REMOVE_AT = MINECRAFT_REGISTRAR.register("list_remove_at", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<List, Integer>) (list, index) -> list.remove((int) index),
            FunctionSignature.create(Void.class, List.of("list", "index"), ParseUtils.parameterized(List.class, LIST_ELEMENT), Integer.class),
            "Remove at Index",
            "List",
            "Removes the element at an index, shifting the later ones down",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_CONTAINS = MINECRAFT_REGISTRAR.register("list_contains", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<List, Object, Boolean>) List::contains,
            FunctionSignature.create(Boolean.class, List.of("list", "element"), ParseUtils.parameterized(List.class, LIST_ELEMENT), LIST_ELEMENT),
            "Contains",
            "List",
            "Whether an equal element is in the list",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_INDEX_OF = MINECRAFT_REGISTRAR.register("list_index_of", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<List, Object, Integer>) List::indexOf,
            FunctionSignature.create(Integer.class, List.of("list", "element"), ParseUtils.parameterized(List.class, LIST_ELEMENT), LIST_ELEMENT),
            "Index Of",
            "List",
            "The index of the first equal element, or -1 when none",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_SIZE = MINECRAFT_REGISTRAR.register("list_size", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<List, Integer>) List::size,
            FunctionSignature.create(Integer.class, List.of("list"), ParseUtils.parameterized(List.class, LIST_ELEMENT)),
            "Size",
            "List",
            "The number of elements",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_IS_EMPTY = MINECRAFT_REGISTRAR.register("list_is_empty", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<List, Boolean>) List::isEmpty,
            FunctionSignature.create(Boolean.class, List.of("list"), ParseUtils.parameterized(List.class, LIST_ELEMENT)),
            "Is Empty",
            "List",
            "Whether the list has no elements",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> LIST_CLEAR = MINECRAFT_REGISTRAR.register("list_clear", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Consumer<List>) List::clear,
            FunctionSignature.create(Void.class, List.of("list"), ParseUtils.parameterized(List.class, LIST_ELEMENT)),
            "Clear",
            "List",
            "Removes every element",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // java.util.Set
    // ***********************************************

    public static final Holder<ScriptFunction> SET_ADD = MINECRAFT_REGISTRAR.register("set_add", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<Set, Object>) Set::add,
            FunctionSignature.create(Void.class, List.of("set", "element"), ParseUtils.parameterized(Set.class, SET_ELEMENT), SET_ELEMENT),
            "Add",
            "Set",
            "Adds an element unless an equal one is already in",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SET_REMOVE = MINECRAFT_REGISTRAR.register("set_remove", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<Set, Object>) Set::remove,
            FunctionSignature.create(Void.class, List.of("set", "element"), ParseUtils.parameterized(Set.class, SET_ELEMENT), SET_ELEMENT),
            "Remove",
            "Set",
            "Removes the equal element",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SET_CONTAINS = MINECRAFT_REGISTRAR.register("set_contains", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Set, Object, Boolean>) Set::contains,
            FunctionSignature.create(Boolean.class, List.of("set", "element"), ParseUtils.parameterized(Set.class, SET_ELEMENT), SET_ELEMENT),
            "Contains",
            "Set",
            "Whether an equal element is in the set",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SET_SIZE = MINECRAFT_REGISTRAR.register("set_size", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Set, Integer>) Set::size,
            FunctionSignature.create(Integer.class, List.of("set"), ParseUtils.parameterized(Set.class, SET_ELEMENT)),
            "Size",
            "Set",
            "The number of elements",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SET_IS_EMPTY = MINECRAFT_REGISTRAR.register("set_is_empty", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Set, Boolean>) Set::isEmpty,
            FunctionSignature.create(Boolean.class, List.of("set"), ParseUtils.parameterized(Set.class, SET_ELEMENT)),
            "Is Empty",
            "Set",
            "Whether the set has no elements",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> SET_CLEAR = MINECRAFT_REGISTRAR.register("set_clear", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Consumer<Set>) Set::clear,
            FunctionSignature.create(Void.class, List.of("set"), ParseUtils.parameterized(Set.class, SET_ELEMENT)),
            "Clear",
            "Set",
            "Removes every element",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // java.util.Map
    // ***********************************************

    public static final Holder<ScriptFunction> MAP_GET = MINECRAFT_REGISTRAR.register("map_get", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Map, Object, Object>) Map::get,
            FunctionSignature.create(MAP_VALUE, List.of("map", "key"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE), MAP_KEY),
            "Get",
            "Map",
            "The value under a key, or null when none",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_GET_OR_DEFAULT = MINECRAFT_REGISTRAR.register("map_get_or_default", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<Map, Object, Object, Object>) Map::getOrDefault,
            FunctionSignature.create(MAP_VALUE, List.of("map", "key", "defaultValue"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE), MAP_KEY, MAP_VALUE),
            "Get or Default",
            "Map",
            "The value under a key, or the default when none",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_PUT = MINECRAFT_REGISTRAR.register("map_put", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriConsumer<Map, Object, Object>) Map::put,
            FunctionSignature.create(Void.class, List.of("map", "key", "value"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE), MAP_KEY, MAP_VALUE),
            "Put",
            "Map",
            "Stores a value under a key, replacing the old one",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_REMOVE = MINECRAFT_REGISTRAR.register("map_remove", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiConsumer<Map, Object>) Map::remove,
            FunctionSignature.create(Void.class, List.of("map", "key"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE), MAP_KEY),
            "Remove",
            "Map",
            "Removes the entry under a key",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_CONTAINS = MINECRAFT_REGISTRAR.register("map_contains", () ->
        ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Map, Object, Boolean>) Map::containsKey,
            FunctionSignature.create(Boolean.class, List.of("map", "key"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE), MAP_KEY),
            "Contains",
            "Map",
            "Whether the map has an entry under a key",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_SIZE = MINECRAFT_REGISTRAR.register("map_size", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Map, Integer>) Map::size,
            FunctionSignature.create(Integer.class, List.of("map"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE)),
            "Size",
            "Map",
            "The number of entries",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_IS_EMPTY = MINECRAFT_REGISTRAR.register("map_is_empty", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Map, Boolean>) Map::isEmpty,
            FunctionSignature.create(Boolean.class, List.of("map"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE)),
            "Is Empty",
            "Map",
            "Whether the map has no entries",
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> MAP_CLEAR = MINECRAFT_REGISTRAR.register("map_clear", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Consumer<Map>) Map::clear,
            FunctionSignature.create(Void.class, List.of("map"), ParseUtils.parameterized(Map.class, MAP_KEY, MAP_VALUE)),
            "Clear",
            "Map",
            "Removes every entry",
            false,
            ScriptFunction.Usage.SCRIPT
        )
    );

    // ***********************************************
    // java.lang.String
    // ***********************************************

    public static final Holder<ScriptFunction> SUBSTR1 = MINECRAFT_REGISTRAR.register("substring1", () ->
        ScriptModularizeHelper.makeCustomScript(
            (ScriptFunction.TriFunction<String, Integer, Integer, String>)String::substring,
            FunctionSignature.create(String.class, List.of("string", "beginIndex", "endIndex"), String.class, int.class, int.class),
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
            FunctionSignature.create(String.class, List.of("string", "beginIndex"), String.class, int.class),
            "Substring",
            "String",
            null,
            true,
            ScriptFunction.Usage.SCRIPT
        )
    );

    public static final Holder<ScriptFunction> TO_STRING = MINECRAFT_REGISTRAR.register("to_string", () ->
        ScriptModularizeHelper.makeCustomScript(
            (Function<Object, String>) String::valueOf,
            FunctionSignature.create(String.class, List.of("object"), Object.class),
            "To String",
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
