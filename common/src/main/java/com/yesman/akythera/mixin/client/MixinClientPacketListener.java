package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Ignores the positions the server sends for an entity a client rag doll places, see
/// [ArmatureComponent#startRagdoll]. The delta base is still kept, so [ArmatureComponent#stopRagdoll] can put the
/// entity back where the server holds it.
@Mixin(value = ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Shadow
    private ClientLevel level;

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        ),
        method = "handleMoveEntity(Lnet/minecraft/network/protocol/game/ClientboundMoveEntityPacket;)V",
        cancellable = true
    )
    private void akythera$ignoreMoveEntity(final ClientboundMoveEntityPacket packet, final CallbackInfo callbackInfo) {
        Entity entity = packet.getEntity(level);

        if (!akythera$isPlacedByRagdoll(entity)) return;

        if (packet.hasPosition()) {
            VecDeltaCodec positionCodec = entity.getPositionCodec();
            positionCodec.setBase(positionCodec.decode(packet.getXa(), packet.getYa(), packet.getZa()));
        }

        callbackInfo.cancel();
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        ),
        method = "handleEntityPositionSync(Lnet/minecraft/network/protocol/game/ClientboundEntityPositionSyncPacket;)V",
        cancellable = true
    )
    private void akythera$ignorePositionSync(final ClientboundEntityPositionSyncPacket packet, final CallbackInfo callbackInfo) {
        Entity entity = level.getEntity(packet.id());

        if (!akythera$isPlacedByRagdoll(entity)) return;

        entity.getPositionCodec().setBase(packet.values().position());
        callbackInfo.cancel();
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        ),
        method = "handleTeleportEntity(Lnet/minecraft/network/protocol/game/ClientboundTeleportEntityPacket;)V",
        cancellable = true
    )
    private void akythera$ignoreTeleport(final ClientboundTeleportEntityPacket packet, final CallbackInfo callbackInfo) {
        if (akythera$isPlacedByRagdoll(level.getEntity(packet.id()))) callbackInfo.cancel();
    }

    @Unique
    private static boolean akythera$isPlacedByRagdoll(final @Nullable Entity entity) {
        if (entity == null || entity.isLocalInstanceAuthoritative()) return false;

        ArmatureComponent armatureComponent = AkytheraPatches.getEntityPatchOptional(entity).map(EntityPatch::getArmatureComponent).orElse(null);

        return armatureComponent != null && armatureComponent.isRagdollRunning();
    }
}
