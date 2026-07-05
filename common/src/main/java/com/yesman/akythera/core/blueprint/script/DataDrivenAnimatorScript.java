package com.yesman.akythera.core.blueprint.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.data.modularize.ScriptException;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.blueprint.Blueprint;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DataDrivenAnimatorScript
    implements AnimatorScript, DataDrivenScript<Animator, DataDrivenAnimatorScript.AnimatorScriptBlueprint>
{
    /// Script blueprint
    private final Holder.Reference<AnimatorScriptBlueprint> blueprint;

    private boolean constructThrownError;
    private boolean updateThrownError;
    private boolean getPoseThrownError;

    /// Script variables
    private final ScriptVariables variables;

    /// Script owner
    private final Animator owner;

    public DataDrivenAnimatorScript(final Holder.Reference<AnimatorScriptBlueprint> blueprint, final Animator owner) {
        this.blueprint = blueprint;
        this.variables = new ScriptVariables(blueprint.value().variables());
        this.owner = owner;
    }

    @Override
    public void construct(final Map<AnimationLayerSignature, AnimationLayer> layers, final Map<String, AnimationDriver> cachedPoseDrivers, final BlendingAnimator animator) {
        if (constructThrownError) return;

        variables.reset();
        variables.setValue("animLayerMap", layers);
        variables.setValue("animator", animator);
        variables.setValue("poseCaches", cachedPoseDrivers);

        try {
            getBlueprint().value().construct().execute(this, new ScriptNode.ExecutionContext(variables, false));
        } catch (Throwable throwable) {
            constructThrownError = true;

            Akythera.LOGGER.error(
                new ScriptException(
                    getBlueprint().getRegisteredName(),
                    AnimatorScript.class.getSimpleName(),
                    "construct",
                    throwable
                ),
                throwable
            );
        }
    }

    @Override
    public void update(final float frameTime, final float partialTick) {
        if (updateThrownError) return;

        variables.reset();
        variables.setValue("frameTime", frameTime);
        variables.setValue("partialTick", partialTick);

        try {
            getBlueprint().value().update().execute(this, new ScriptNode.ExecutionContext(variables, false));
        } catch (Throwable throwable) {
            updateThrownError = true;

            Akythera.LOGGER.error(
                new ScriptException(
                    getBlueprint().getRegisteredName(),
                    AnimatorScript.class.getSimpleName(),
                    "update",
                    throwable
                ),
                throwable
            );
        }
    }

    @Override
    public Pose getFinalPose(final Map<AnimationLayerSignature, AnimationLayer> layers) {
        if (getPoseThrownError) return Pose.EMPTY_POSE;

        variables.reset();
        variables.setValue("layers", layers);

        try {
            return getBlueprint().value().poseGetter().execute(this, new ScriptNode.ExecutionContext(variables, false));
        } catch (Throwable throwable) {
            getPoseThrownError = true;

            Akythera.LOGGER.error(
                new ScriptException(
                    getBlueprint().getRegisteredName(),
                    AnimatorScript.class.getSimpleName(),
                    "getFinalPose",
                    throwable
                ),
                throwable
            );

            return Pose.EMPTY_POSE;
        }
    }

    @Override
    public Animator getScriptOwner() {
        return owner;
    }

    @Override
    public Holder.Reference<AnimatorScriptBlueprint> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return variables;
    }

    public static Builder blueprintBuilder() {
        return new DataDrivenAnimatorScript.Builder();
    }

    public record AnimatorScriptBlueprint(
        FlowNode construct,
        FlowNode update,
        FlowNode poseGetter,
        List<ScriptVariables.VariableInfo> variables
    ) implements Blueprint<DataDrivenAnimatorScript, Animator>
    {
        public static final MapCodec<AnimatorScriptBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                FlowNode.CODEC.fieldOf("construct").forGetter(AnimatorScriptBlueprint::construct),
                FlowNode.CODEC.fieldOf("update").forGetter(AnimatorScriptBlueprint::update),
                FlowNode.CODEC.fieldOf("pose").forGetter(AnimatorScriptBlueprint::poseGetter),
                ScriptVariables.VariableInfo.CODEC.listOf().fieldOf("variables").forGetter(AnimatorScriptBlueprint::variables)
            ).apply(instance, AnimatorScriptBlueprint::new)
        );

        public static final Codec<AnimatorScriptBlueprint> CODEC = MAP_CODEC.codec();

        /// Use [DataDrivenAnimatorScript] constructor to get holder in more reliable way
        @Override @Deprecated
        public DataDrivenAnimatorScript instantiate(final Animator blueprintOwner) {
            RegistryAccess registryAccess = StaticRegistryAccess.getRegistryAccess();

            // Dereference holder by object.
            ResourceKey<DataDrivenAnimatorScript.AnimatorScriptBlueprint> resourceKey =
                registryAccess.lookupOrThrow(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY).getResourceKey(this).orElseThrow();

            return new DataDrivenAnimatorScript(registryAccess.getOrThrow(resourceKey), blueprintOwner);
        }

        @Override
        public MapCodec<? extends Blueprint<DataDrivenAnimatorScript, Animator>> mapCodec() {
            return MAP_CODEC;
        }
    }

    public static final class Builder {
        FlowNode construct;
        FlowNode update;
        FlowNode poseGetter;
        final List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

        public Builder construct(final FlowNode construct) {
            this.construct = construct;
            return this;
        }

        public Builder update(final FlowNode update) {
            this.update = update;
            return this;
        }

        public Builder poseGetter(final FlowNode poseGetter) {
            this.poseGetter = poseGetter;
            return this;
        }

        public Builder memberVariable(final String variableName, final ScriptType type, final Object defaultVal) {
            variables.add(new ScriptVariables.VariableInfo(variableName, type, defaultVal));
            return this;
        }

        public AnimatorScriptBlueprint build() {
            if (construct == null) {
                throw new IllegalStateException("Can't build Animator Blueprint: construct logic not specified!");
            }

            if (update == null) {
                throw new IllegalStateException("Can't build Animator Blueprint: update logic not specified!");
            }

            if (poseGetter == null) {
                throw new IllegalStateException("Can't build Animator Blueprint: pose getter logic not specified!");
            }

            return new AnimatorScriptBlueprint(construct, update, poseGetter, variables);
        }
    }
}
