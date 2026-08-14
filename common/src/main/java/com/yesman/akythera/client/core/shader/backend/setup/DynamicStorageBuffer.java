package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import net.minecraft.util.Mth;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A frame-scoped bump allocator over a [MappedStorageRing], handing out the `layout(std430)`
/// payloads a dispatch reads.
///
/// Mirrors [net.minecraft.client.renderer.DynamicUniformStorage], which does the same for uniform
/// blocks but only in one fixed block size. Every slice this hands out stays valid until
/// [#endFrame] — a buffer outgrown mid-frame is retired, not closed.
public final class DynamicStorageBuffer implements AutoCloseable {
    private static final int INITIAL_CAPACITY = 65536;

    private final List<MappedStorageRing> retiredBuffers = new ArrayList<>();
    private final String label;
    private final int alignment;

    private MappedStorageRing ringBuffer;
    private int capacity;
    private int nextOffset;

    public DynamicStorageBuffer(final String label) {
        this.label = label;

        alignment = Math.max(ComputeBackends.get().storageBufferOffsetAlignment(), 4);
        capacity = INITIAL_CAPACITY;
        ringBuffer = new MappedStorageRing(label, capacity);
    }

    /// Reserves `byteLength` bytes at a binding-legal offset and lets `writer` fill them.
    public GpuBufferSlice write(final int byteLength, final Consumer<ByteBuffer> writer) {
        int offset = Mth.roundToward(nextOffset, alignment);

        if (offset + byteLength > capacity) {
            grow(Math.max(capacity * 2, Mth.roundToward(byteLength, alignment)));
            offset = 0;
        }

        GpuBufferSlice slice = ringBuffer.currentBuffer().slice(offset, byteLength);

        try (GpuBufferSlice.MappedView view = slice.map(false, true)) {
            writer.accept(view.data());
        }

        nextOffset = offset + byteLength;

        return slice;
    }

    /// Rewinds the allocator and rotates onto the next ring slot, whose fence guards writes against
    /// the frames still reading it.
    public void endFrame() {
        nextOffset = 0;
        ringBuffer.rotate();

        if (!retiredBuffers.isEmpty()) {
            retiredBuffers.forEach(MappedStorageRing::close);
            retiredBuffers.clear();
        }
    }

    @Override
    public void close() {
        retiredBuffers.forEach(MappedStorageRing::close);
        retiredBuffers.clear();
        ringBuffer.close();
    }

    private void grow(final int newCapacity) {
        retiredBuffers.add(ringBuffer);
        capacity = newCapacity;
        nextOffset = 0;
        ringBuffer = new MappedStorageRing(label + " x" + newCapacity, newCapacity);
    }
}
