package com.yesman.akythera.core.blueprint.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.blueprint.Blueprint;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Map;

public final class DataDrivenAnimatorScript
    implements AnimatorScript, DataDrivenScript<Animator, DataDrivenAnimatorScript.AnimatorScriptBlueprint>
{
    /// [ScriptCaller] sites of the three entry points. The events' are their method names.
    public static final String CONSTRUCT = "construct";
    public static final String UPDATE = "update";
    public static final String GET_FINAL_POSE = "getFinalPose";

    /// Script blueprint
    private final Holder.Reference<AnimatorScriptBlueprint> blueprint;

    /// Script owner
    private final Animator owner;

    private final BlueprintExecutor executor;

    public DataDrivenAnimatorScript(final Holder.Reference<AnimatorScriptBlueprint> blueprint, final Animator owner) {
        this.blueprint = blueprint;
        this.owner = owner;
        this.executor = new BlueprintExecutor(
            this, ScriptModularizeHelper.scanBlueprintLayout(AnimatorScript.class), blueprint.value().body());
    }

    @Override
    public void construct(final Map<AnimationLayerSignature, AnimationLayer> animLayerMap, final Map<String, AnimationDriver> poseCaches, final BlendingAnimator animator) {
        executor.invokeMethod(CONSTRUCT, null, animLayerMap, poseCaches, animator);
    }

    @Override
    public void update(final float frameTime, final float partialTick) {
        executor.invokeMethod(UPDATE, null, frameTime, partialTick);
    }

    @Override
    public Pose getFinalPose(final Map<AnimationLayerSignature, AnimationLayer> layers) {
        Object pose = executor.run(
            GET_FINAL_POSE, blueprint.value().poseGetter(), List.of("layers"), new Object[] {layers}, null);

        return pose instanceof Pose finalPose ? finalPose : Pose.EMPTY_POSE;
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
        return executor.variables();
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }

    /// @param poseGetter the Anim Graph, answering the final pose from the `layers` local
    public record AnimatorScriptBlueprint(BlueprintBody body, FlowNode poseGetter)
        implements Blueprint<DataDrivenAnimatorScript, Animator>
    {
        public static final MapCodec<AnimatorScriptBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                BlueprintBody.MAP_CODEC.forGetter(AnimatorScriptBlueprint::body),
                FlowNode.CODEC.fieldOf("pose").forGetter(AnimatorScriptBlueprint::poseGetter)
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
}
