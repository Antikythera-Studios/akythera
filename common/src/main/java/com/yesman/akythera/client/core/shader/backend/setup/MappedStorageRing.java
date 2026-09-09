package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import org.jspecify.annotations.Nullable;

/// [net.minecraft.client.renderer.MappableRingBuffer] over storage buffers.
///
/// Same shape and the same fence-per-slot rotation; only the allocation differs, because a ring
/// buffer of `GpuDevice#createBuffer` buffers can never be bound as a storage buffer.
public final class MappedStorageRing implements AutoCloseable {
    private static final int SLOT_COUNT = 3;

    private final GpuBuffer[] buffers = new GpuBuffer[SLOT_COUNT];
    private final @Nullable GpuFence[] fences = new GpuFence[SLOT_COUNT];

    private int current;

    /// Whether [#currentBuffer] handed out the current slot since the last [#rotate].
    private boolean written;

    public MappedStorageRing(final String label, final int size) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int slot = i;
            buffers[i] = ComputeBackends.get().createMappedStorageBuffer(() -> label + " #" + slot, size);
        }
    }

    /// The slot to write, once the frames that were still reading it have finished.
    public GpuBuffer currentBuffer() {
        GpuFence fence = fences[current];

        if (fence != null) {
            fence.awaitCompletion(Long.MAX_VALUE);
            fence.close();
            fences[current] = null;
        }

        written = true;

        return buffers[current];
    }

    /// Fences the slot just written and moves onto the next. A slot nothing wrote stays current,
    /// having nothing for a fence to guard.
    public void rotate() {
        if (!written) return;

        if (fences[current] != null) {
            fences[current].close();
        }

        fences[current] = RenderSystem.getDevice().createCommandEncoder().createFence();
        current = (current + 1) % SLOT_COUNT;
        written = false;
    }

    @Override
    public void close() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            buffers[i].close();

            if (fences[i] != null) {
                fences[i].close();
            }
        }
    }
}
