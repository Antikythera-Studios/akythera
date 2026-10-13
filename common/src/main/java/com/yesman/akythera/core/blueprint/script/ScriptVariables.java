package com.yesman.akythera.core.blueprint.script;

import com.google.common.base.Suppliers;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.protocol.PacketFlow;

import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/// A runtime fields belong to [DataDrivenScript]. Scripts
/// that are built by datapack is already in runtime so
/// variable generations should also be done in runtime,
/// not compile time.
///
/// NOTE: This doesn't serialize the values itself, but rather
/// serializes what values are belong to script.
public final class ScriptVariables {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, VariableInfo> memberVariables = new HashMap<>();

    // Without member variables
    public ScriptVariables() {
        this(null);
    }

    // Use `ScriptVariables()` with no member variables
    public ScriptVariables(final List<VariableInfo> memberVariables) {
        if (memberVariables != null) {
            memberVariables.forEach(v -> {
                this.variables.put(v.name(), v.type().mutableCopy(v.defaultVal()));
                this.memberVariables.put(v.name(), v);
            });
        }
    }

    public boolean hasVariable(final String name) {
        return variables.containsKey(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(final String name) {
        if (!variables.containsKey(name)) throw new IllegalArgumentException("No script variable named " + name);
        return (T) variables.get(name);
    }

    public <T> T setValue(final String name, final T value) {
        variables.put(name, value);
        return value;
    }

    /// A copy of the values this instance holds right now.
    ///
    /// A callback sub-flow runs long after the entry point that built it returned, and [#reset] has
    /// wiped the locals by then, so the callback reads its locals from this. Member variables still
    /// resolve through the live script.
    public ScriptVariables snapshot() {
        ScriptVariables copy = new ScriptVariables();

        copy.variables.putAll(variables);
        copy.memberVariables.putAll(memberVariables);

        return copy;
    }

    public void reset() {
        // Remove local variables
        variables.entrySet().removeIf(entry -> !memberVariables.containsKey(entry.getKey()));
    }

    /// The locals this instance holds right now, for [#restoreLocals] to put back.
    public Map<String, Object> captureLocals() {
        Map<String, Object> locals = new HashMap<>();

        variables.forEach((name, value) -> {
            if (!memberVariables.containsKey(name)) locals.put(name, value);
        });

        return locals;
    }

    /// Replaces the locals with `locals`, keeping the member variables.
    public void restoreLocals(final Map<String, Object> locals) {
        reset();
        variables.putAll(locals);
    }

    @Override
    public String toString() {
        return "Script Variables: " + variables + "\nmember variables: " + memberVariables;
    }

    /// Which way a variable travels, and whether it travels at all.
    public enum SyncDirection {
        NONE,

        /// The server owns it and mirrors it to whoever instantiates the ability.
        SERVER_TO_CLIENT,

        /// The owner's client writes it and sends it with its activation request.
        CLIENT_TO_SERVER;

        public static final Codec<SyncDirection> CODEC = Codec.STRING.xmap(
            name -> ParseUtils.enumValueOfOrNull(SyncDirection.class, name) == null
                ? NONE
                : ParseUtils.enumValueOfOrNull(SyncDirection.class, name),
            value -> value.name().toLowerCase(Locale.ROOT)
        );
    }

    /// @param defaultValue the value the variable starts at, resolving to null when it starts unset
    /// @param sync         forced to [SyncDirection#NONE] when the type carries no stream codec
    public record VariableInfo(
        String name, ScriptType type, Supplier<@Nullable Object> defaultValue, SyncDirection sync
    ) {
        public VariableInfo(final String name, final ScriptType type, final @Nullable Object defaultVal) {
            this(name, type, defaultVal, SyncDirection.NONE);
        }

        public VariableInfo(final String name, final ScriptType type, final @Nullable Object defaultVal, final SyncDirection sync) {
            this(name, type, () -> defaultVal, sync);
        }

        public VariableInfo {
            if (!type.isSyncable()) sync = SyncDirection.NONE;
        }

        public static final Codec<VariableInfo> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(VariableInfo::name),
                    ScriptType.CODEC.fieldOf("type").forGetter(VariableInfo::type),
                    Codec.PASSTHROUGH.optionalFieldOf("default_value").forGetter(ParseUtils.provideOptional(VariableInfo::valueAsDynamic)),
                    SyncDirection.CODEC.optionalFieldOf("sync", SyncDirection.NONE).forGetter(VariableInfo::sync)
                )
                .apply(instance, (name, type, defaultValue, sync) ->
                    new VariableInfo(name, type, Suppliers.memoize(() -> decodeDefaultValue(type, defaultValue)), sync))
        );

        /// The value the variable starts at. Null when it starts unset.
        public @Nullable Object defaultVal() {
            return defaultValue.get();
        }

        /// Whether this variable crosses the wire in `flow`'s direction.
        public boolean travels(final PacketFlow flow) {
            return switch (sync) {
                case NONE -> false;
                case SERVER_TO_CLIENT -> flow == PacketFlow.CLIENTBOUND;
                case CLIENT_TO_SERVER -> flow == PacketFlow.SERVERBOUND;
            };
        }

        // For codec. A null default is left out of the json.
        private @Nullable Dynamic<?> valueAsDynamic() {
            return type.codec() == null || defaultVal() == null ? null : new Dynamic<> (JsonOps.INSTANCE, encode().getOrThrow());
        }

        /// Decodes the serialized `default_value` into the typed value via the [ScriptType]'s codec.
        ///
        /// Runs on the first [#defaultVal] call. A registry entry default is looked up through
        /// [StaticRegistryAccess], which holds nothing while the data pack registries carrying this variable load.
        @SuppressWarnings("unchecked")
        private static <A> @Nullable Object decodeDefaultValue(final ScriptType type, final Optional<Dynamic<?>> defaultValue) {
            if (defaultValue.isEmpty() || type.codec() == null) return null;
            return ((Codec<A>) type.codec()).parse(defaultValue.get()).getOrThrow();
        }

        @SuppressWarnings("unchecked")
        private <A> DataResult<JsonElement> encode() {
            assert type.codec() != null;
            return ((Codec<A>)type.codec()).encodeStart(JsonOps.INSTANCE, (A)defaultVal());
        }
    }
}
