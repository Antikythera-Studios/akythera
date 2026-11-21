package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/// Events around a living entity's jump from the ground, on the side that moves it.
public abstract class LivingJumpEvent extends LivingEntityPatchEvent {
    public LivingJumpEvent(final EntityPatch<LivingEntity> entityPatch) {
        super(entityPatch);
    }

    /// Posted in [LivingEntity#aiStep] before it reads the jump input. Listeners may hold the jump back.
    public static final class Input extends LivingJumpEvent {
        private boolean jumping;

        public Input(final EntityPatch<LivingEntity> entityPatch, final boolean jumping) {
            super(entityPatch);
            this.jumping = jumping;
        }

        public boolean isJumping() {
            return jumping;
        }

        public void setJumping(final boolean jumping) {
            this.jumping = jumping;
        }
    }

    /// Posted after [LivingEntity#jumpFromGround]. Listeners may change the sprint boost it added.
    public static final class Jumped extends LivingJumpEvent {
        /// The horizontal speed a sprinting jump adds, in blocks per tick. Zero for a jump without sprint.
        private Vec3 sprintBoost;

        public Jumped(final EntityPatch<LivingEntity> entityPatch, final Vec3 sprintBoost) {
            super(entityPatch);
            this.sprintBoost = sprintBoost;
        }

        public Vec3 getSprintBoost() {
            return sprintBoost;
        }

        public void setSprintBoost(final Vec3 sprintBoost) {
            this.sprintBoost = sprintBoost;
        }
    }
}
