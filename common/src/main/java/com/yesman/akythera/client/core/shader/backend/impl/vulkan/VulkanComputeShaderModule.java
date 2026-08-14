package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.yesman.akythera.client.core.shader.backend.program.ComputeShaderModule;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcBufferRange;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/// One compute stage compiled to SPIR-V, alongside the reflection a pipeline layout needs: which
/// descriptor bindings the storage buffers sit at, and where each push constant lives inside the
/// block.
///
/// A failed compile keeps the error message and produces no SPIR-V; [#isCompiled] reports it and
/// linking such a module is never attempted.
final class VulkanComputeShaderModule implements ComputeShaderModule {
    private final @Nullable ByteBuffer spirv;
    private final String infoLog;
    private final IntList storageBindings;
    private final Map<String, PushConstantRange> pushConstants;
    private final int pushConstantSize;

    VulkanComputeShaderModule(final String source) {
        Compiled compiled = compile(source);

        spirv = compiled.spirv();
        infoLog = compiled.infoLog();

        if (spirv == null) {
            storageBindings = IntList.of();
            pushConstants = Map.of();
            pushConstantSize = 0;

            return;
        }

        IntList bindings = new IntArrayList();
        Map<String, PushConstantRange> ranges = new HashMap<>();

        pushConstantSize = reflect(spirv, bindings, ranges);
        storageBindings = bindings;
        pushConstants = ranges;
    }

    private static Compiled compile(final String source) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();

        // Vulkan 1.2, matching the VK12 entry points the backend is written against. Optimization is
        // left off so a driver crash reports line numbers that mean something, which is what
        // `GlslCompiler` does for the graphics stages.
        Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
        Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_zero);
        Shaderc.shaderc_compile_options_set_generate_debug_info(options);

        ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
        ByteBuffer nameBuffer = MemoryUtil.memUTF8("akythera-compute");
        ByteBuffer entryPointBuffer = MemoryUtil.memUTF8("main");
        long result = Shaderc.shaderc_compile_into_spv(
            compiler, sourceBuffer, Shaderc.shaderc_compute_shader, nameBuffer, entryPointBuffer, options
        );

        try {
            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                return new Compiled(null, Shaderc.shaderc_result_get_error_message(result));
            }

            ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
            ByteBuffer spirv = MemoryUtil.memCalloc(bytes.remaining());
            MemoryUtil.memCopy(bytes, spirv);

            return new Compiled(spirv, "");
        } finally {
            Shaderc.shaderc_result_release(result);
            MemoryUtil.memFree(entryPointBuffer);
            MemoryUtil.memFree(nameBuffer);
            MemoryUtil.memFree(sourceBuffer);
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    @Override
    public boolean isCompiled() {
        return spirv != null;
    }

    @Override
    public String getInfoLog() {
        return infoLog;
    }

    @Override
    public void close() {
        if (spirv != null) {
            MemoryUtil.memFree(spirv);
        }
    }

    ByteBuffer spirv() {
        if (spirv == null) {
            throw new IllegalStateException("Attempt to use a compute stage that failed to compile: " + infoLog);
        }

        return spirv;
    }

    /// Descriptor bindings the `layout(std430) buffer` blocks were decorated with, ascending.
    IntList storageBindings() {
        return storageBindings;
    }

    Map<String, PushConstantRange> pushConstants() {
        return pushConstants;
    }

    int pushConstantSize() {
        return pushConstantSize;
    }

    /// Fills `bindings` and `ranges` off the SPIR-V and returns the size of the push constant block.
    ///
    /// `spvc_compiler_get_active_buffer_ranges` reports only the members the stage actually reads,
    /// so a member the shader declares but never uses resolves to no range and its writes are
    /// dropped — which is the same thing an unused OpenGL uniform does, its location coming back
    /// as -1.
    private static int reflect(final ByteBuffer spirv, final IntList bindings, final Map<String, PushConstantRange> ranges) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.callocPointer(1);
            PointerBuffer countPointer = stack.callocPointer(1);

            throwIfError(Spvc.spvc_context_create(pointer), "create a spirv-cross context");
            long context = pointer.get(0);

            try {
                IntBuffer words = spirv.asIntBuffer();

                throwIfError(Spvc.spvc_context_parse_spirv(context, words, words.remaining(), pointer), "parse the compiled SPIR-V");
                long ir = pointer.get(0);

                throwIfError(
                    Spvc.spvc_context_create_compiler(context, Spvc.SPVC_BACKEND_NONE, ir, Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pointer),
                    "create a spirv-cross compiler"
                );
                long compiler = pointer.get(0);

                throwIfError(Spvc.spvc_compiler_create_shader_resources(compiler, pointer), "list the shader's resources");
                long resources = pointer.get(0);

                throwIfError(
                    Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, pointer, countPointer),
                    "list the storage buffers"
                );

                SpvcReflectedResource.Buffer storageBuffers = SpvcReflectedResource.create(pointer.get(0), (int) countPointer.get(0));

                for (int i = 0; i < storageBuffers.capacity(); i++) {
                    bindings.add(Spvc.spvc_compiler_get_decoration(compiler, storageBuffers.get(i).id(), Spv.SpvDecorationBinding));
                }

                bindings.sort(null);

                throwIfError(
                    Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_PUSH_CONSTANT, pointer, countPointer),
                    "list the push constants"
                );

                SpvcReflectedResource.Buffer pushConstantBlocks = SpvcReflectedResource.create(pointer.get(0), (int) countPointer.get(0));

                if (pushConstantBlocks.capacity() == 0) {
                    return 0;
                }

                SpvcReflectedResource block = pushConstantBlocks.get(0);

                throwIfError(
                    Spvc.spvc_compiler_get_active_buffer_ranges(compiler, block.id(), pointer, countPointer),
                    "read the push constant block's layout"
                );

                SpvcBufferRange.Buffer members = SpvcBufferRange.create(pointer.get(0), (int) countPointer.get(0));
                int size = 0;

                for (int i = 0; i < members.capacity(); i++) {
                    SpvcBufferRange member = members.get(i);
                    String name = Spvc.spvc_compiler_get_member_name(compiler, block.base_type_id(), member.index());

                    ranges.put(name, new PushConstantRange((int) member.offset(), (int) member.range()));
                    size = Math.max(size, (int) (member.offset() + member.range()));
                }

                return size;
            } finally {
                Spvc.spvc_context_destroy(context);
            }
        }
    }

    private static void throwIfError(final int result, final String what) {
        if (result != Spvc.SPVC_SUCCESS) {
            throw new IllegalStateException("spirv-cross failed to " + what + ": " + result);
        }
    }

    /// Where one push constant sits inside the block, in bytes.
    record PushConstantRange(int offset, int size) {
    }

    private record Compiled(@Nullable ByteBuffer spirv, String infoLog) {
    }
}
