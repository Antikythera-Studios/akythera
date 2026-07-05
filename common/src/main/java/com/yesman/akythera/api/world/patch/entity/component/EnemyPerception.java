package com.yesman.akythera.api.world.patch.entity.component;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Targeting;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Abstraction of all entity patches that can have attack target
///
/// Modeled after [Targeting] interface but [EntityPatch] version.
public final class EnemyPerception implements PatchComponent<EntityPatch<LivingEntity>> {
    private final EntityPatch<LivingEntity> owner;
    private LivingEntity targeting;

    public EnemyPerception(EntityPatch<LivingEntity> owner) {
        this.owner = owner;
    }

    /// Returns a current attack target
    public @Nullable LivingEntity getTarget() {
        return targeting;
    }

    /// Sets a current target. Give null if you want to remove target
    ///
    /// Targets are kept until it's valid, and alive.
    public void setTarget(@Nullable LivingEntity target) {
        targeting = target;
    }

    /// Returns whether the target can be referenced in valid process
    public static boolean isInvalidTarget(@NonNull LivingEntity target) {
        return target.isRemoved();
    }

    @Override
    public EntityPatch<LivingEntity> getOwner() {
        return owner;
    }
}
