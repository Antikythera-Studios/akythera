package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/// @param transform              where the skeleton sits in its owner's space
/// @param transformInheritPolicy the elements of its owner's space the skeleton takes on
/// @param childBlueprints        what the armature builds its children from, keyed by the name the
///                               [ArmatureComponent] exposes them under
///                               ([ComponentAttachable#getComponent])
/// @param enabled                whether the built [ArmatureComponent] starts switched on
public record ArmatureComponentBlueprint(
    AssetAccessor<? extends Armature> armature,
    AnimatorScriptFactory scriptFactory,
    boolean useRootMotion,
    Transform transform,
    TransformInheritPolicy transformInheritPolicy,
    Map<String, ComponentBlueprint<?, ?>> childBlueprints,
    boolean enabled
) implements ComponentBlueprint<ArmatureComponent, ComponentAttachable>
{
    public static final MapCodec<ArmatureComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ArmatureManager.CODEC.fieldOf("armature").forGetter(ArmatureComponentBlueprint::armature),
                AnimatorScriptFactory.CODEC.fieldOf("script").forGetter(ArmatureComponentBlueprint::scriptFactory),
                Codec.BOOL.fieldOf("use_root_motion").forGetter(ArmatureComponentBlueprint::useRootMotion),
                TRANSFORM_CODEC.forGetter(ArmatureComponentBlueprint::transform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(ArmatureComponentBlueprint::transformInheritPolicy),
                // The same field [TransformSocket] declares for its own children, dispatching
                // through the same registry. A childless armature decodes to an empty map.
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES)
                    .optionalFieldOf("components", Map.of())
                    .forGetter(ArmatureComponentBlueprint::childBlueprints),
                ENABLED_CODEC.forGetter(ArmatureComponentBlueprint::enabled)
            )
            .apply(instance, ArmatureComponentBlueprint::new)
    );

    /// Childless, switched-on armature—the shape every call site used before components were
    /// attachable.
    public ArmatureComponentBlueprint(
        final AssetAccessor<? extends Armature> armature,
        final AnimatorScriptFactory scriptFactory,
        final boolean useRootMotion
    ) {
        this(armature, scriptFactory, useRootMotion, Transform.IDENTITY, TransformInheritPolicy.LOC_ROT_SCA, Map.of(), true);
    }

    @Override
    public ArmatureComponent instantiate(final ComponentAttachable armatureOwner) {
        ArmatureComponent component = new ArmatureComponent(
            armatureOwner,
            armature,
            ComponentsFactory.getImpl().getAnimator(armatureOwner, scriptFactory::instantiate),
            useRootMotion,
            transform,
            transformInheritPolicy,
            childBlueprints
        );

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<ArmatureComponent, ComponentAttachable>> mapCodec() {
        return CODEC;
    }

    public interface AnimatorScriptFactory {
        Codec<AnimatorScriptFactory> CODEC = Codec.STRING.dispatch(
            provider ->
                switch (provider) {
                    case ByClass _ -> "class";
                    case ByDataPack _ -> "datapack";
                    default -> throw new IllegalArgumentException("Unsupported script provider type " + provider.getClass().getCanonicalName());
                },
            literal ->
                switch (literal) {
                    case "class" -> ByClass.CODEC;
                    case "datapack" -> ByDataPack.CODEC;
                    default -> throw new IllegalArgumentException("Unsupported script provider type " + literal);
                }
        );

        AnimatorScript instantiate(final Animator blueprintOwner);
    }

    public record ByClass(ScriptType animatorType) implements AnimatorScriptFactory {
        static final MapCodec<ByClass> CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("animator_type").forGetter(ByClass::animatorType)
                )
                .apply(instance, ByClass::new)
        );

        @Override
        public AnimatorScript instantiate(final Animator blueprintOwner) {
            if (!AnimatorScript.class.isAssignableFrom(animatorType.type())) {
                throw new IllegalArgumentException(animatorType.type().getSimpleName() +
                    " is not a subclass of " + AnimatorScript.class.getSimpleName());
            }

            try {
                return (AnimatorScript)animatorType.type().getConstructor().newInstance();
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Failed at binding animator and script", e);
            }
        }
    }

    public record ByDataPack(ResourceKey<DataDrivenAnimatorScript.AnimatorScriptBlueprint> resourceKey) implements AnimatorScriptFactory {
        static final MapCodec<ByDataPack> CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ResourceKey.codec(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY)
                        .fieldOf("animator_type").forGetter(ByDataPack::resourceKey)
                )
            .apply(instance, ByDataPack::new)
        );

        @Override
        public AnimatorScript instantiate(final Animator blueprintOwner) {
            Holder.Reference<DataDrivenAnimatorScript.AnimatorScriptBlueprint> scriptHolder =
                StaticRegistryAccess.getRegistryAccess().getOrThrow(resourceKey);

            return new DataDrivenAnimatorScript(scriptHolder, blueprintOwner);
        }
    }
}