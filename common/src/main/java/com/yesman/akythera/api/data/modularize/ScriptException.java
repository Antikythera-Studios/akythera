package com.yesman.akythera.api.data.modularize;

public final class ScriptException extends RuntimeException {
    public ScriptException(final String scriptId, final String className, final String methodName, final Throwable cause) {
        super(String.format("Error while executing script %s(%s#%s)", scriptId, className, methodName), cause);
    }
}
