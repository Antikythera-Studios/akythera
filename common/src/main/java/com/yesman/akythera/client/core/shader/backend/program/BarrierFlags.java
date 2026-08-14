package com.yesman.akythera.client.core.shader.backend.program;

/// Memory hazards a compute dispatch is ordered against once it finishes. Backend-neutral: the
/// implementation maps these onto its own barrier bits.
public enum BarrierFlags {
    /// Writes through `layout(std430) buffer` become visible to later shader reads.
    SHADER_STORAGE,

    /// Writes become visible to vertex attribute fetch.
    VERTEX_ATTRIBUTE,

    /// Writes become visible to index fetch.
    ELEMENT_ARRAY,

    /// Writes become visible to atomic counter reads.
    ATOMIC_COUNTER,

    /// Writes become visible to indirect draw and dispatch command reads.
    COMMAND
}
