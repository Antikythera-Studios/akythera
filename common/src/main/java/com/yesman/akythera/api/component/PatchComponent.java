package com.yesman.akythera.api.component;

/// A static, non-instantiated per owners component. Shared throughout blueprint
/// instances.
///
/// Behavior is composed from small, self-contained modules attached to a host
/// rather than accreted through deep inheritance. A host keeps its components in a
/// map keyed by their runtime [Class] and looks them up by type
public interface PatchComponent {
}
