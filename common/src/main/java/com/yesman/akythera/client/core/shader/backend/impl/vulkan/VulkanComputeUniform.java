package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.yesman.akythera.client.core.shader.backend.program.ComputeUniform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;

/// One member of the push constant block, written into the staging block the dispatch pushes.
///
/// A name the block does not carry resolves to [#DROPPED], whose writes go nowhere, mirroring what
/// an unresolved OpenGL uniform location does.
record VulkanComputeUniform(ByteBuffer block, int offset, int size) implements ComputeUniform {
    static final VulkanComputeUniform DROPPED = new VulkanComputeUniform(null, 0, 0);

    @Override
    public void putMatrix3f(final Matrix3f value) {
        if (block == null) return;

        // std430 pads each column of a mat3 out to a vec4, which is what the 48-byte range spirv-cross
        // reports covers. JOML packs the columns tight, so they go in one at a time.
        for (int column = 0; column < 3; column++) {
            int base = offset + column * 16;

            block.putFloat(base, value.get(column, 0));
            block.putFloat(base + 4, value.get(column, 1));
            block.putFloat(base + 8, value.get(column, 2));
            block.putFloat(base + 12, 0.0F);
        }
    }

    @Override
    public void putMatrix4f(final Matrix4f value) {
        if (block == null) return;

        value.get(offset, block);
    }

    @Override
    public void putUnsignedInt(final int value) {
        if (block == null) return;

        block.putInt(offset, value);
    }

    @Override
    public void putVector3f(final Vector3fc value) {
        if (block == null) return;

        block.putFloat(offset, value.x());
        block.putFloat(offset + 4, value.y());
        block.putFloat(offset + 8, value.z());
    }
}
