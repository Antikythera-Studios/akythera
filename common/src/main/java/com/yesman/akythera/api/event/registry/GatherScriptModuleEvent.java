package com.yesman.akythera.api.event.registry;

import com.yesman.akythera.core.event.Event;
import com.yesman.akythera.core.reflection.ScriptField;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;

/// An event triggered on gathering script modules, like field
/// getters and setters annotated by [ScriptField] or methods
/// annotated by [ScriptFunction].
///
/// Use [ScriptModularizeHelper#scanScriptModulesAndRegister]
/// to register class with those annotations, or
/// [ScriptModularizeHelper#registerCustomScript] to register
/// custom logics.
public final class GatherScriptModuleEvent extends Event {
}
