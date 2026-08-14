package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/// Everything one skinning dispatch needs that is not baked into the mesh.
///
/// @param poses            fully bound bone transforms, `mat4[]`, in the per-frame storage buffer
/// @param visibilityFlags  one bit per mesh part, set when the part is hidden, `int[]`
/// @param modelView        the entity's `PoseStack.Pose#pose`, baked into the output positions
/// @param normal           the entity's `PoseStack.Pose#normal`
/// @param color            vertex tint, ARGB
/// @param outlineColor     team outline tint, ARGB, 0 when the entity has no outline
/// @param packedLight      lightmap coords, packed as vanilla's `VertexConsumer#setLight` expects
/// @param packedOverlay    overlay coords, packed as vanilla's `VertexConsumer#setOverlay` expects
public record SkinnedDrawContext(
    GpuBufferSlice poses,
    GpuBufferSlice visibilityFlags,
    Matrix4f modelView,
    Matrix3f normal,
    int color,
    int outlineColor,
    int packedLight,
    int packedOverlay
) {
}
