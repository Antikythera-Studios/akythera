package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.reflection.ScriptType;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/// The variables one [Ability] reads and writes, one per [AbilityInstance], built by
/// [Ability#createState].
///
/// A null codec in a direction means the state is not a sync target that way. Fields a codec
/// leaves out keep their current value on the receiving side.
@ScriptType
public interface AbilityState {
    /// The state an ability that declares no variables receives: nothing to sync, never dirty.
    AbilityState EMPTY = new AbilityState() {
        @Override
        public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec() {
            return null;
        }

        @Override
        public boolean checkDirtyToSend(final AbilityState previous) {
            return false;
        }

        @Override
        public void copyState(final AbilityState from) {

        }

        @Override
        public void decodeInto(
            final PacketFlow flow,
            final RegistryFriendlyByteBuf buffer,
            final Level level
        ) {

        }
    };

    @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec();

    default @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> serverBoundCodec() {
        return streamCodec();
    }

    default @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> clientBoundCodec() {
        return streamCodec();
    }

    /// Whether any field the client bound codec carries differs from `previous`, the last state
    /// that was sent.
    boolean checkDirtyToSend(final AbilityState previous);

    /// Copies `from`'s values into this state in place.
    void copyState(final AbilityState from);

    /// Reads what the `flow` codec wrote into this state in place.
    ///
    /// Every field that codec leaves out keeps its current value.
    ///
    /// @param level resolves entity ids, which a codec alone cannot
    void decodeInto(PacketFlow flow, RegistryFriendlyByteBuf buffer, Level level);

    /// Encodes `state` through its `flow` codec on the calling thread. Empty when that direction has
    /// no codec.
    ///
    /// A connection encodes its payloads later on its own event loop, where a live state would be
    /// read while its owner keeps writing it.
    @SuppressWarnings("unchecked")
    static byte[] encode(
        final AbilityState state,
        final PacketFlow flow,
        final RegistryAccess registryAccess
    ) {
        StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> codec =
            flow == PacketFlow.CLIENTBOUND ? state.clientBoundCodec() : state.serverBoundCodec();

        if (codec == null) return new byte[0];

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);

        try {
            // A state declares its codecs for its own class, and this codec came from this state.
            ((StreamCodec<? super RegistryFriendlyByteBuf, AbilityState>) codec).encode(buffer, state);

            return ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    /// Reads `bytes` into `state` in place.
    ///
    /// @return whether the bytes were exactly one encoding. Fields read before a fault stay written
    static boolean decode(
        final AbilityState state,
        final PacketFlow flow,
        final byte[] bytes,
        final Level level
    ) {
        RegistryFriendlyByteBuf buffer =
            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), level.registryAccess());

        try {
            state.decodeInto(flow, buffer, level);

            return buffer.readableBytes() == 0;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            buffer.release();
        }
    }
}
