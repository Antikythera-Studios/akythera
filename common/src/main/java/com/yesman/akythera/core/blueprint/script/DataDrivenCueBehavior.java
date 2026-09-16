package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCueInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/// Runs a data pack cue's `on_execute` graph.
///
/// One behavior is shared by every play of its entry, so the graph binds the play it is running for
/// as a local rather than holding one.
public final class DataDrivenCueBehavior implements AbilityCue.Behavior {
    /// The locals every cue graph reads.
    public static final String OWNER_LOCAL = "component";

    public static final String CUE_LOCAL = "cue";

    public static final String STATE_LOCAL = "state";

    private final Holder.Reference<AbilityCueBlueprint> blueprint;

    /// Set once the graph threw. It is not run again.
    private boolean failed;

    public DataDrivenCueBehavior(final Holder.Reference<AbilityCueBlueprint> blueprint) {
        this.blueprint = blueprint;
    }

    @Override
    public void onExecute(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityCueInstance cueInstance,
        final @Nullable AbilityState abilityState
    ) {
        if (failed) return;

        // A fresh set per play: one behavior serves every play of this entry, and they overlap.
        ScriptVariables variables = new ScriptVariables(blueprint.value().variables());

        variables.setValue(OWNER_LOCAL, abilitySystemComponent);
        variables.setValue(CUE_LOCAL, cueInstance);
        variables.setValue(STATE_LOCAL, abilityState);

        try {
            blueprint.value().onExecute().execute(null, new ScriptNode.ExecutionContext(variables));
        } catch (Throwable throwable) {
            failed = true;

            Akythera.LOGGER.error(
                "Cue graph of {} threw and is now disabled", blueprint.key().identifier(), throwable
            );
        }
    }
}
