package com.yesman.akythera.api.event.entity.animation;

import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.Entity;

/// Fired when [AnimationMontage] starts to play
public class PlayAnimMontageEvent extends EntityPatchEvent<EntityPatch<Entity>> {
	private final AnimationMontage animMontage;
	
	public PlayAnimMontageEvent(EntityPatch<Entity> playerPatch, AnimationMontage animMontage) {
		super(playerPatch);
		this.animMontage = animMontage;
	}

	public AnimationMontage getAnimMontage() {
		return this.animMontage;
	}
}
