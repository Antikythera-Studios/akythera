package com.yesman.akythera.api.component;

/// A component that only one can exist per type in [ComponentAttachable].
///
/// Game crashes if it detects over one component in assembling patch
/// components, this rule is protected by Akythera Labs, but not for
/// manual data file manipulations—so runtime validation is required.
public interface UniqueComponent extends PatchComponent {
}
