package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// One equipped ability's variables and graphs. Built per [AbilityInstance] through
/// [Ability#createState], because one [Ability] is shared by every instance that equips it.
public final class DataDrivenAbilityState
    implements AbilityState, DataDrivenScript<Ability<?>, AbilityBlueprint>
{
    private final Holder.Reference<AbilityBlueprint> blueprint;

    private final Ability<?> blueprintOwner;

    private final BlueprintExecutor executor;

    public DataDrivenAbilityState(
        final Holder.Reference<AbilityBlueprint> blueprint,
        final Ability<?> blueprintOwner
    ) {
        this.blueprint = blueprint;
        this.blueprintOwner = blueprintOwner;
        this.executor = new BlueprintExecutor(
            this, ScriptModularizeHelper.scanBlueprintLayout(blueprintOwner.getClass()), blueprint.value().body());
    }

    /// The blueprint's variables in declaration order, which is the order both sides read them in.
    private List<ScriptVariables.VariableInfo> syncedVariables(final PacketFlow flow) {
        return blueprint.value().body().variables().stream().filter(variable -> variable.travels(flow)).toList();
    }

    @Override
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec() {
        return null;
    }

    @Override
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> clientBoundCodec() {
        return directionCodec(PacketFlow.CLIENTBOUND);
    }

    @Override
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> serverBoundCodec() {
        return directionCodec(PacketFlow.SERVERBOUND);
    }

    /// A codec that writes every variable travelling `flow`, or null when none does.
    ///
    /// Only the write half is real. Reading happens in [#decodeInto], which a payload handler calls
    /// with the [Level] an entity-typed variable needs to resolve its id.
    private @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> directionCodec(
        final PacketFlow flow
    ) {
        List<ScriptVariables.VariableInfo> synced = syncedVariables(flow);

        if (synced.isEmpty()) return null;

        return StreamCodec.of(
            (buffer, state) -> {
                for (ScriptVariables.VariableInfo variable : synced) {
                    variable.type().writeSynced(buffer, executor.variables().getValue(variable.name()));
                }
            },
            buffer -> this
        );
    }

    @Override
    public boolean checkDirtyToSend(final AbilityState previous) {
        if (!(previous instanceof DataDrivenAbilityState sent)) return true;

        for (ScriptVariables.VariableInfo variable : syncedVariables(PacketFlow.CLIENTBOUND)) {
            if (!Objects.equals(
                executor.variables().getValue(variable.name()), sent.executor.variables().getValue(variable.name())
            )) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void copyState(final AbilityState from) {
        if (!(from instanceof DataDrivenAbilityState source)) return;

        for (ScriptVariables.VariableInfo variable : blueprint.value().body().variables()) {
            executor.variables().setValue(variable.name(), source.executor.variables().getValue(variable.name()));
        }
    }

    @Override
    public void decodeInto(
        final PacketFlow flow,
        final RegistryFriendlyByteBuf buffer,
        final Level level
    ) {
        for (ScriptVariables.VariableInfo variable : syncedVariables(flow)) {
            executor.variables().setValue(variable.name(), variable.type().readSynced(buffer, level));
        }
    }

    @Override
    public Ability<?> getScriptOwner() {
        return blueprintOwner;
    }

    @Override
    public Holder.Reference<AbilityBlueprint> getBlueprint() {
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
