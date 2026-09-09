package com.yesman.akythera.core.networking.payload.bidirectional;

import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public record BiDirectionalReplaceAnimation(List<ReplaceAnimations.ReplaceEntry> entries, boolean reset) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BiDirectionalReplaceAnimation> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "replace_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BiDirectionalReplaceAnimation> STREAM_CODEC =
        StreamCodec.composite(
            ReplaceAnimations.ReplaceEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            BiDirectionalReplaceAnimation::entries,
            ByteBufCodecs.BOOL,
            BiDirectionalReplaceAnimation::reset,
            BiDirectionalReplaceAnimation::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}