package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// What runs a node tree: the object the run belongs to, the blueprint the tree came from, and the site
/// inside that blueprint — an entry point (`update`), a component slot (`armature/cape#renderTypeGetter`),
/// or the editor canvas a deferred sub-tree was compiled from.
///
/// @param owner     what the run belongs to, e.g. the animator or the entity patch. Null for a component
///                  hanging off no patch.
/// @param blueprint the registry key of the blueprint, or null for a tree that came from none
public record ScriptCaller(@Nullable Object owner, @Nullable ResourceKey<?> blueprint, String site) {
    /// Joins a component's path and its slot in a component site.
    public static final String SLOT_SEPARATOR = "#";

    /// A run of `script` at `site`.
    public static ScriptCaller of(final DataDrivenScript<?, ?> script, final String site) {
        return new ScriptCaller(script.getScriptOwner(), script.getBlueprint().key(), site);
    }

    /// A run of `component`'s `slot`, sited at the component's path under `owner` and keyed by the
    /// blueprint `owner` was built from.
    public static ScriptCaller ofComponent(final @Nullable EntityPatch<?> owner, final PatchComponent component, final String slot) {
        if (owner == null) {
            return new ScriptCaller(null, null, slot);
        }

        String path = owner.pathOf(component);
        ResourceKey<?> blueprint = owner.getScript() instanceof DataDrivenScript<?, ?> script
            ? script.getBlueprint().key()
            : null;

        return new ScriptCaller(owner, blueprint, (path == null ? "" : path) + SLOT_SEPARATOR + slot);
    }

    /// The same owner and blueprint at another site.
    public ScriptCaller at(final String site) {
        return new ScriptCaller(owner, blueprint, site);
    }
}
