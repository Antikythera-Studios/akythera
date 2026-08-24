package com.yesman.akythera.core.asset.loader;

import net.minecraft.resources.Identifier;

/// Handles all resource loading exceptions in Epic Fight.
/// both for json and binary file extensions.
public class ResourceLoadingFailureException extends RuntimeException {
    public ResourceLoadingFailureException(Identifier resourcePath, Exception e) {
        super("Failed at loading resource: " + resourcePath + ": " + e.getMessage(), e);
    }

    public ResourceLoadingFailureException(Identifier resourcePath, String message) {
        super("Failed at loading resource: " + resourcePath + " " + message);
    }
}
