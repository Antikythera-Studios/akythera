package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/// @param sockets the [BoneSocket]s attached under the armature, keyed by the name the
///                [ArmatureComponent] exposes them under ([ComponentAttachable#getComponent])
public record ArmatureComponentBlueprint(
    AssetAccessor<? extends Armature> armature,
    AnimatorScriptFactory scriptFactory,
    boolean useRootMotion,
    Map<String, BoneSocket> sockets
) implements ComponentBlueprint<ArmatureComponent, EntityPatch<Entity>>
{
    public static final MapCodec<ArmatureComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ArmatureManager.CODEC.fieldOf("armature").forGetter(ArmatureComponentBlueprint::armature),
                AnimatorScriptFactory.CODEC.fieldOf("script").forGetter(ArmatureComponentBlueprint::scriptFactory),
                Codec.BOOL.fieldOf("use_root_motion").forGetter(ArmatureComponentBlueprint::useRootMotion),
                // Sockets are the armature's attached child components; a blueprint written before
                // sockets existed (or one that simply has none) decodes to an empty map.
                Codec.unboundedMap(Codec.STRING, BoneSocket.DIRECT_CODEC)
                    .optionalFieldOf("sockets", Map.of()).forGetter(ArmatureComponentBlueprint::sockets)
            )
            .apply(instance, ArmatureComponentBlueprint::new)
    );

    /// Socket-less armature — the shape every call site used before sockets were attachable.
    public ArmatureComponentBlueprint(
        final AssetAccessor<? extends Armature> armature,
        final AnimatorScriptFactory scriptFactory,
        final boolean useRootMotion
    ) {
        this(armature, scriptFactory, useRootMotion, Map.of());
    }

    @Override
    public ArmatureComponent instantiate(final EntityPatch<Entity> armatureOwner) {
        return new ArmatureComponent(
            armatureOwner,
            armature,
            ComponentsFactory.getImpl().getAnimator(armatureOwner, scriptFactory::instantiate),
            useRootMotion,
            sockets
        );
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<ArmatureComponent, EntityPatch<Entity>>> mapCodec() {
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
            if (AnimatorScript.class.isAssignableFrom(animatorType.type())) {
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