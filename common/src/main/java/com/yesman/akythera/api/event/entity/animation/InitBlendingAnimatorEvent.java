package com.yesman.akythera.api.event.entity.animation;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.event.Event;

/// Called on initializing [BlendingAnimator]
public class InitBlendingAnimatorEvent extends Event {
    private final ComponentAttachable owner;
    private final AnimatorScript animatorScript;
    private final BlendingAnimator animator;

    public InitBlendingAnimatorEvent(final ComponentAttachable owner, final AnimatorScript animatorScript, final BlendingAnimator animator) {
        this.owner = owner;
        this.animatorScript = animatorScript;
        this.animator = animator;
    }

    /// The object the animator plays on
    public ComponentAttachable getOwner() {
        return owner;
    }

    public AnimatorScript getAnimatorScript() {
        return animatorScript;
    }

    public BlendingAnimator getAnimator() {
        return this.animator;
    }
}
