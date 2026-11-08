package com.yesman.akythera.api.world.behavior;

/// A test attached to a [TreeNode]. The placement fails to start while any of its conditions answers false.
///
/// A condition reads state and never changes it.
public interface BehaviorCondition extends BehaviorNode {
    boolean test(final BrainComponent brain);
}
