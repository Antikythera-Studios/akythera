package com.yesman.akythera.api.event.entity.animation;

import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import net.minecraft.world.entity.Entity;

/// Called on initializing [BlendingAnimator]
public class InitBlendingAnimatorEvent extends EntityPatchEvent<EntityPatch<Entity>> {
    private final AnimatorScript animatorScript;
    private final BlendingAnimator animator;

    public InitBlendingAnimatorEvent(final EntityPatch<Entity> entityPatch, final AnimatorScript animatorScript, final BlendingAnimator animator) {
        super(entityPatch);
        this.animatorScript = animatorScript;
        this.animator = animator;
    }

    public AnimatorScript getAnimatorScript() {
        return animatorScript;
    }

    public BlendingAnimator getAnimator() {
        return this.animator;
    }
}