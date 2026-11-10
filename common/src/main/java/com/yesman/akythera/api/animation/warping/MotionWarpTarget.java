package com.yesman.akythera.api.animation.warping;

import com.yesman.akythera.api.animation.notify.impl.MotionWarpingNotifyState;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Finds where a [MotionWarpingNotifyState] warps the root motion to. A built-in one, a Java class registered as a
/// script type with a public no-argument constructor, or a data pack entry run as a [DataDrivenMotionWarpTarget].
@ScriptType
public interface MotionWarpTarget {
    /// The destination of `owner`, in world space, or null to warp nothing.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    @Nullable Vec3 find(final EntityPatch<?> owner);
}
