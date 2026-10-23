package com.yesman.akythera.core.animation.driver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.PoseOperators;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/// Hands branches of the skeleton over to a second pose (UE's "Layered blend per bone"): each bone mixes
/// [AbstractBlendDriver#blend] onto [AbstractBlendDriver#base] by the weight [#setup] gives it, scaled by
/// [#blendWeight].
///
/// The armature is read from [#animator] on every sample, so the mix follows the owner's current
/// armature rather than the one it was constructed against.
public final class LayeredBlendPerBoneDriver extends AbstractBlendDriver {
    private final Animator animator;
    private final LayerSetup setup;

    /// Scales every bone's weight, from 0.0 to 1.0.
    private final Supplier<Float> blendWeight;

    /// [#setup]'s weight per bone id of [#maskArmature].
    private float @Nullable [] mask;

    /// The armature [#mask] was built for.
    private @Nullable Armature maskArmature;

    public LayeredBlendPerBoneDriver(
        final Animator animator, final AnimationDriver base, final AnimationDriver layered,
        final LayerSetup setup, final Supplier<Float> blendWeight
    ) {
        super(base, layered);

        this.animator = animator;
        this.setup = setup;
        this.blendWeight = blendWeight;
    }

    @Override
    public Pose getPose() {
        float weight = Math.clamp(blendWeight.get(), 0.0F, 1.0F);

        if (weight <= 0.0F) return base.getPose();

        Armature armature = animator.getArmature().get();

        if (mask == null || armature != maskArmature) {
            mask = setup.boneWeights(armature);
            maskArmature = armature;
        }

        return PoseOperators.layeredBlendPerBone(
            base.getPose(), blend.getPose(), armature, mask, weight, setup.meshSpaceRotationBlend());
    }

    @Override
    protected AnimationDriver dominant() {
        return blend;
    }

    /// Which bones take the layered pose, and how much.
    ///
    /// A bone's weight is the largest one its [BranchFilter]s give it, unless a [WeightStep] on it or an ancestor
    /// says otherwise: the nearest step wins over the filters. A bone under an excluding filter stays on the base
    /// pose whatever else applies.
    ///
    /// @param meshSpaceRotationBlend whether rotations mix in model space, so a layered branch keeps the
    ///                               orientation it has in the layered pose wherever the base pose moves its
    ///                               parents
    @ScriptType(autoRegister = false)
    public record LayerSetup(List<BranchFilter> branchFilters, List<WeightStep> weightSteps, boolean meshSpaceRotationBlend) {
        public static final Codec<LayerSetup> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    BranchFilter.CODEC.listOf().optionalFieldOf("branch_filters", List.of()).forGetter(LayerSetup::branchFilters),
                    WeightStep.CODEC.listOf().optionalFieldOf("weight_steps", List.of()).forGetter(LayerSetup::weightSteps),
                    Codec.BOOL.optionalFieldOf("mesh_space_rotation_blend", false).forGetter(LayerSetup::meshSpaceRotationBlend)
                ).apply(instance, LayerSetup::new)
        );

        public static final LayerSetup EMPTY = new LayerSetup(List.of(), List.of(), false);

        public LayerSetup {
            branchFilters = List.copyOf(branchFilters);
            weightSteps = List.copyOf(weightSteps);
        }

        /// The weight of every bone of `armature`, by bone id.
        public float[] boneWeights(final Armature armature) {
            float[] weights = new float[armature.getBoneCount()];
            int[] depths = new int[branchFilters.size()];
            Arrays.fill(depths, -1);

            weighRecursively(armature.getRootBone(), weights, depths, false, Float.NaN);

            return weights;
        }

        /// @param parentDepths per filter, how many bones below its own bone the parent sits, or -1 outside it
        /// @param stepWeight   the nearest step's weight above, or NaN for none
        private void weighRecursively(
            final Bone bone, final float[] weights, final int[] parentDepths, boolean excluded, float stepWeight
        ) {
            int[] depths = new int[parentDepths.length];
            float filterWeight = 0.0F;

            for (int i = 0; i < depths.length; i++) {
                BranchFilter filter = branchFilters.get(i);
                depths[i] = parentDepths[i] >= 0 ? parentDepths[i] + 1 : filter.boneName().equals(bone.getName()) ? 0 : -1;

                if (depths[i] < 0) continue;

                if (filter.blendDepth() < 0) {
                    excluded = true;
                } else {
                    filterWeight = Math.max(filterWeight, filter.blendDepth() == 0 ? 1.0F : Math.min(1.0F, (depths[i] + 1) / (float)filter.blendDepth()));
                }
            }

            for (WeightStep step : weightSteps) {
                if (step.boneName().equals(bone.getName())) stepWeight = Math.clamp(step.weight(), 0.0F, 1.0F);
            }

            weights[bone.getId()] = excluded ? 0.0F : Float.isNaN(stepWeight) ? filterWeight : stepWeight;

            for (Bone child : bone.getChildBones()) {
                weighRecursively(child, weights, depths, excluded, stepWeight);
            }
        }
    }

    /// The branch from `boneName` down. The bone takes `1 / blendDepth` of the layered pose and each bone below
    /// takes that much more, so the weight reaches 1.0 `blendDepth` bones in. 0 hands the whole branch over at
    /// once; a negative depth keeps the branch on the base pose.
    public record BranchFilter(String boneName, int blendDepth) {
        public static final Codec<BranchFilter> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("bone").forGetter(BranchFilter::boneName),
                    Codec.INT.optionalFieldOf("blend_depth", 0).forGetter(BranchFilter::blendDepth)
                ).apply(instance, BranchFilter::new)
        );
    }

    /// The weight, from 0.0 to 1.0, `boneName` and the bones below it take, until a step further down.
    public record WeightStep(String boneName, float weight) {
        public static final Codec<WeightStep> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("bone").forGetter(WeightStep::boneName),
                    Codec.FLOAT.fieldOf("weight").forGetter(WeightStep::weight)
                ).apply(instance, WeightStep::new)
        );
    }
}
