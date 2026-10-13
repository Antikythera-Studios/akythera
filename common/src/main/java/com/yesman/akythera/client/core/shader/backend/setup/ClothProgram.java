package com.yesman.akythera.client.core.shader.backend.setup;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.core.shader.backend.ComputeShaderLoader;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.core.Akythera;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// The compute programs a cloth runs, each compiled on first use and dropped by [#invalidateAll].
public enum ClothProgram {
    FRAME("cloth_frame", BarrierFlags.SHADER_STORAGE),
    SOLVER("cloth_solver", BarrierFlags.SHADER_STORAGE),
    SPATIAL_HASH("cloth_spatial_hash", BarrierFlags.SHADER_STORAGE),
    VANILLA_TRANSFORMER("vanilla_cloth_transformer", BarrierFlags.SHADER_STORAGE, BarrierFlags.VERTEX_ATTRIBUTE);

    private final Identifier id;
    private final BarrierFlags[] barriers;
    private @Nullable ComputeProgram program;

    ClothProgram(final String name, final BarrierFlags... barriers) {
        this.id = Akythera.identifier("shaders/compute/" + name + ".comp");
        this.barriers = barriers;
    }

    public ComputeProgram get() {
        if (program == null) {
            program = ComputeShaderLoader.loadComputeShaderProgram(ComponentsFactory.getImpl().getResourceManager(), id, barriers);
        }

        return program;
    }

    /// Drops every compiled program so the next dispatch recompiles it from the reloaded pack.
    public static void invalidateAll() {
        for (ClothProgram clothProgram : values()) {
            if (clothProgram.program != null) {
                clothProgram.program.close();
                clothProgram.program = null;
            }
        }
    }
}
