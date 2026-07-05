package com.yesman.akythera.core.math.model;

import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

/// A "State" definition used by [FiniteStateMachine],
/// working as a signature that represents "one single state".
///
/// This extensible enum class doesn't have any implementation
/// like [EnumerableDataBlockManager] to avoid nesting usage of
/// different state enums among different state machines.
public interface FSMState extends EnumerableDataBlock {
}
