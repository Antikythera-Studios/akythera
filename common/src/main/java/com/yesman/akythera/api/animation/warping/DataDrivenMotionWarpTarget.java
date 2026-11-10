package com.yesman.akythera.api.animation.warping;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetScriptBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A [MotionWarpTarget] written in a data pack, built for one entity and one warping window. A graph that throws, or
/// answers no [Vec3], warps nothing.
public final class DataDrivenMotionWarpTarget implements MotionWarpTarget, DataDrivenScript<EntityPatch<?>, MotionWarpTargetScriptBlueprint> {
    private final Holder.Reference<MotionWarpTargetScriptBlueprint> blueprint;

    private final EntityPatch<?> owner;

    private final BlueprintExecutor executor;

    public DataDrivenMotionWarpTarget(
        final Holder.Reference<MotionWarpTargetScriptBlueprint> blueprint, final BlueprintBody body, final EntityPatch<?> owner
    ) {
        this.blueprint = blueprint;
        this.owner = owner;
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(MotionWarpTarget.class), body);
    }

    // ***************************************************************
    // MotionWarpTarget
    // ***************************************************************

    @Override
    public @Nullable Vec3 find(final EntityPatch<?> owner) {
        return executor.invokeMethod("find", null, owner) instanceof Vec3 target ? target : null;
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public EntityPatch<?> getScriptOwner() {
        return owner;
    }

    @Override
    public Holder.Reference<MotionWarpTargetScriptBlueprint> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return executor.variables();
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }
}
