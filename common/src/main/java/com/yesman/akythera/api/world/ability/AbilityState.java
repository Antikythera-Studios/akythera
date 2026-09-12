package com.yesman.akythera.api.world.ability;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface AbilityState {
    StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec();

    default StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> clientBoundCodec() {
        return streamCodec();
    }

    default StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> serverBoundCodec() {
        return streamCodec();
    }
}
