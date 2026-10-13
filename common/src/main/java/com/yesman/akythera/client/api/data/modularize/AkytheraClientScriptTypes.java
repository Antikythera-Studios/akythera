package com.yesman.akythera.client.api.data.modularize;

import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptType;
import net.minecraft.client.renderer.rendertype.RenderType;

/// Script types that name client-only classes. Used by [AkytheraScriptTypes]
public final class AkytheraClientScriptTypes {
    private AkytheraClientScriptTypes() {}

    /// A [RenderType] value: carried between nodes, never serialized or held in a variable.
    public static ScriptType renderType() {
        return new ScriptType(RenderType.class);
    }

    /// The client-only class named `className`, found by reflection.
    ///
    /// @throws IllegalArgumentException when no class has that name
    public static ScriptType guiType(final String className) {
        try {
            return new ScriptType(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("No class named " + className, e);
        }
    }
}
