package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

/// Opens the [WidgetUsage#SCREEN] or shows the [WidgetUsage#HUD] a [WidgetBlueprint] describes.
@NullMarked
public record ClientBoundOpenWidget(ResourceKey<WidgetBlueprint> widget) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundOpenWidget> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "open_widget"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundOpenWidget> STREAM_CODEC =
        StreamCodec.composite(
            ResourceKey.streamCodec(AkytheraRegistries.DataPack.WIDGET_KEY),
            ClientBoundOpenWidget::widget,
            ClientBoundOpenWidget::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
