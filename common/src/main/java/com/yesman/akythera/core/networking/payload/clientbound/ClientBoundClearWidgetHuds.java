package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.client.api.gui.HudManager;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Removes every HUD the [HudManager] shows.
@NullMarked
public record ClientBoundClearWidgetHuds() implements CustomPacketPayload {
    public static final ClientBoundClearWidgetHuds INSTANCE = new ClientBoundClearWidgetHuds();

    public static final CustomPacketPayload.Type<ClientBoundClearWidgetHuds> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "clear_widget_huds"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundClearWidgetHuds> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
