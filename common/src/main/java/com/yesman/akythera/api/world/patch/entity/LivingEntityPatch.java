package com.yesman.akythera.api.world.patch.entity;

import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class LivingEntityPatch<T extends LivingEntity> extends EntityPatch<T> {
    public LivingEntityPatch(@NonNull T original) {
        super(original);
    }

    public LivingEntityPatch(final Holder.@Nullable Reference<EntityPatchBlueprint> blueprint, @NonNull final T original) {
        super(blueprint, original);
    }

    public LivingEntityPatch(final EntityPatchBlueprint blueprint, @NonNull final T original) {
        super(blueprint, original);
    }

    public LivingEntityPatch(final LivingEntityPatch<T> copyTarget) {
        super(copyTarget);
    }
}
