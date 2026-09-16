package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/// One equipped ability's variables and graphs. Built per [AbilityInstance] through
/// [Ability#createState], because one [Ability] is shared by every instance that equips it.
public final class DataDrivenAbilityState
    implements AbilityState, DataDrivenScript<Ability<?>, AbilityBlueprint>
{
    /// Which graph an event runs.
    public enum Event {
        ON_EQUIPPED, ON_UNEQUIPPED, ON_ACTIVATED, ON_RECAST, ON_CANCELED, ON_FINISHED
    }

    private final Holder.Reference<AbilityBlueprint> blueprint;

    private final Ability<?> blueprintOwner;

    private final ScriptVariables variables;

    /// Events that threw once. A failed graph is not run again on this instance.
    private final Map<Event, Boolean> failed = new EnumMap<>(Event.class);

    /// Depth of the graph currently running, so a graph that re-enters this state does not reset
    /// the variables out from under the one that called it.
    private int running;

    /// Whether the `can_activate` condition threw. It refuses every activation afterwards.
    private boolean canActivateFailed;

    public DataDrivenAbilityState(
        final Holder.Reference<AbilityBlueprint> blueprint,
        final Ability<?> blueprintOwner
    ) {
        this.blueprint = blueprint;
        this.blueprintOwner = blueprintOwner;
        this.variables = new ScriptVariables(blueprint.value().variables());
    }

    /// The locals every ability graph reads. A blueprint may not declare a variable of either
    /// name, or each run would overwrite it.
    public static final String OWNER_LOCAL = "component";

    public static final String INSTANCE_LOCAL = "instance";

    /// Whether `event`'s graph threw and was latched off.
    public boolean hasFailed(final Event event) {
        return Boolean.TRUE.equals(failed.get(event));
    }

    /// Evaluates the blueprint's condition, from `can_activate_function` when it declares one and
    /// from the `can_activate` pure node otherwise.
    ///
    /// Fails closed: a condition that throws, or answers with anything but true, refuses the
    /// activation.
    public boolean evaluateCanActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance
    ) {
        PureNode condition = blueprint.value().canActivate().orElse(null);
        FlowNode function = blueprint.value().canActivateFunction().orElse(null);

        if (condition == null && function == null) return true;
        if (canActivateFailed) return false;

        if (running == 0) variables.reset();

        variables.setValue(OWNER_LOCAL, abilitySystemComponent);
        variables.setValue(INSTANCE_LOCAL, abilityInstance);
        running++;

        try {
            ScriptNode.ExecutionContext context = new ScriptNode.ExecutionContext(variables);

            if (function != null) {
                function.execute(this, context);

                // A graph that never reached its return node answers nothing, and nothing is a no.
                return Boolean.TRUE.equals(context.takeReturnedValue());
            }

            return Boolean.TRUE.equals(condition.execute(this, context));
        } catch (Throwable throwable) {
            canActivateFailed = true;

            Akythera.LOGGER.error(
                "Ability condition of {} threw and now refuses every activation",
                blueprint.key().identifier(), throwable
            );

            return false;
        } finally {
            running--;
        }
    }

    /// Runs `event`'s graph with the two objects every ability graph reads bound as locals.
    public void run(
        final Event event,
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance
    ) {
        if (hasFailed(event)) return;

        FlowNode graph = switch (event) {
            case ON_EQUIPPED -> blueprint.value().onEquipped();
            case ON_UNEQUIPPED -> blueprint.value().onUnequipped();
            case ON_ACTIVATED -> blueprint.value().onActivated();
            case ON_RECAST -> blueprint.value().onRecast();
            case ON_CANCELED -> blueprint.value().onCanceled();
            case ON_FINISHED -> blueprint.value().onFinished();
        };

        if (running == 0) variables.reset();

        variables.setValue(OWNER_LOCAL, abilitySystemComponent);
        variables.setValue(INSTANCE_LOCAL, abilityInstance);
        running++;

        try {
            graph.execute(this, new ScriptNode.ExecutionContext(variables));
        } catch (Throwable throwable) {
            failed.put(event, true);

            Akythera.LOGGER.error(
                "Ability graph {} of {} threw and is now disabled on this instance",
                event, blueprint.key().identifier(), throwable
            );
        } finally {
            running--;
        }
    }

    /// The blueprint's variables in declaration order, which is the order both sides read them in.
    private List<ScriptVariables.VariableInfo> syncedVariables(final PacketFlow flow) {
        return blueprint.value().variables().stream().filter(variable -> variable.travels(flow)).toList();
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
                    write(buffer, variable);
                }
            },
            buffer -> this
        );
    }

    @SuppressWarnings("unchecked")
    private void write(final RegistryFriendlyByteBuf buffer, final ScriptVariables.VariableInfo variable) {
        StreamCodec<? super RegistryFriendlyByteBuf, Object> codec =
            (StreamCodec<? super RegistryFriendlyByteBuf, Object>) variable.type().streamCodec();
        Object value = variables.getValue(variable.name());

        // An entity cannot travel by value, and a bare stream codec holds no level to resolve it.
        // Keyed on the declared type, which is what the read side decides by.
        if (Entity.class.isAssignableFrom(variable.type().type())) {
            buffer.writeVarInt(value instanceof Entity entity ? entity.getId() : -1);

            return;
        }

        codec.encode(buffer, value);
    }

    @Override
    public boolean checkDirtyToSend(final AbilityState previous) {
        if (!(previous instanceof DataDrivenAbilityState sent)) return true;

        for (ScriptVariables.VariableInfo variable : syncedVariables(PacketFlow.CLIENTBOUND)) {
            if (!Objects.equals(variables.getValue(variable.name()), sent.variables.getValue(variable.name()))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void copyState(final AbilityState from) {
        if (!(from instanceof DataDrivenAbilityState source)) return;

        for (ScriptVariables.VariableInfo variable : blueprint.value().variables()) {
            variables.setValue(variable.name(), source.variables.getValue(variable.name()));
        }
    }

    @Override
    public void decodeInto(
        final PacketFlow flow,
        final RegistryFriendlyByteBuf buffer,
        final Level level
    ) {
        for (ScriptVariables.VariableInfo variable : syncedVariables(flow)) {
            variables.setValue(variable.name(), read(buffer, variable, level));
        }
    }

    private @Nullable Object read(
        final RegistryFriendlyByteBuf buffer,
        final ScriptVariables.VariableInfo variable,
        final Level level
    ) {
        if (Entity.class.isAssignableFrom(variable.type().type())) {
            int entityId = buffer.readVarInt();

            return entityId < 0 ? null : level.getEntity(entityId);
        }

        return variable.type().streamCodec().decode(buffer);
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
        return variables;
    }
}
