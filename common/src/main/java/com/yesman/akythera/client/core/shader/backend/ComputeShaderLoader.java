package com.yesman.akythera.client.core.shader.backend;

import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeShaderModule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ComputeShaderLoader {
    /// Compiles and links the compute program at `identifier`, ordered against `barrierFlags` at
    /// the end of every dispatch.
    public static ComputeProgram loadComputeShaderProgram(
        final ResourceProvider resourceManager,
        final Identifier identifier,
        final BarrierFlags... barrierFlags
    ) throws IllegalStateException {
        Optional<Resource> resource = resourceManager.getResource(identifier);

        if (resource.isEmpty()) {
            throw new IllegalStateException("Shader script not found: \"" + identifier + "\"");
        }

        String shaderSource;

        try (InputStream stream = resource.get().open()) {
            shaderSource = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ComputeBackend backend = ComputeBackends.get();

        try (ComputeShaderModule module = backend.compile(shaderSource)) {
            if (!module.isCompiled()) {
                throw new IllegalStateException(
                    "Shader \"" + identifier + "\" failed to compile because of the following errors: " + module.getInfoLog()
                );
            }

            ComputeProgram program = backend.link(module, barrierFlags);

            if (!program.isLinked()) {
                throw new IllegalStateException(
                    "Program \"" + identifier + "\" failed to link because of the following errors: " + program.getInfoLog()
                );
            }

            return program;
        }
    }

    private ComputeShaderLoader() {
    }
}
