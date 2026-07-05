package com.yesman.akythera.api.component;

/// A component that should be reloaded instead of shallow-copying.
public interface ReloadablePatchComponent<T> extends PatchComponent<T> {
    /// Discards the old components and creates renewed components.
    ///
    /// You can keep the old data in renewed component, or completely remove the
    /// old data and fill with new one on each respawn, dimension changes, and even
    /// datapack reload in AkytheraLabs editor.
    ReloadablePatchComponent<T> getRenewed(final T newOwner);
}
