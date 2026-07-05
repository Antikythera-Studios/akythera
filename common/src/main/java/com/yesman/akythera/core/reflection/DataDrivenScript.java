package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import net.minecraft.core.Holder;

/// A scriptable object where developers can build logics
/// via datapack. That means the logics are serializable
/// in Minecraft's data loading cycle.
///
/// Data script must support these; [ScriptNode] based
/// logic flow and [ScriptVariables] support, but *what to
/// implement* are determined by driven classes.
///
/// @param <T> Script owner. All unconnected pure node's owner.
public interface DataDrivenScript<T, B extends Blueprint<? extends DataDrivenScript<?, ?>, T>> {
    /// Returns script owner
    T getScriptOwner();

    /// Returns [Blueprint] of this instance
    Holder.Reference<B> getBlueprint();

    /// Returns runtime variables managed as hashmap since
    /// code injection is impossible in data pack load time.
    ScriptVariables getScriptVariable();
}
