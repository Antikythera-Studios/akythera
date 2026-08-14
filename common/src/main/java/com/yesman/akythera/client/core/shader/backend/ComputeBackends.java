package com.yesman.akythera.client.core.shader.backend;

import org.jspecify.annotations.Nullable;

/// Holds the [ComputeBackend] the client installed once the GPU device was created.
public final class ComputeBackends {
    private static @Nullable ComputeBackend installed;

    private ComputeBackends() {
    }

    public static void install(final ComputeBackend backend) {
        installed = backend;
    }

    public static boolean isInstalled() {
        return installed != null;
    }

    public static ComputeBackend get() throws IllegalStateException {
        if (installed == null) {
            throw new IllegalStateException("No compute backend has been installed");
        }

        return installed;
    }
}
