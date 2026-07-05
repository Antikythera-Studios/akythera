package com.yesman.akythera.api.event.entity.animation;

import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.Entity;

/// Fired when [AnimationMontage] is stopped by time expiration, or interrupted by another montage
public class EndAnimMontageEvent extends EntityPatchEvent<EntityPatch<Entity>> {
	private final AnimationMontage animMontage;
	private final boolean isFinished;
	
	public EndAnimMontageEvent(EntityPatch<Entity> playerPatch, AnimationMontage animMontage, boolean isFinished) {
		super(playerPatch);
		
		this.animMontage = animMontage;
		this.isFinished = isFinished;
	}

	public AnimationMontage getAnimMontage() {
		return this.animMontage;
	}

    /// Returns whether the montage is played to the end
	public boolean isFinished() {
		return this.isFinished;
	}
}
