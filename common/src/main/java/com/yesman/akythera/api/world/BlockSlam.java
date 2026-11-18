package com.yesman.akythera.api.world;

import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundBlockSlam;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.mixinaccessor.client.ClientLevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/// Fractures the ground around a point: the blocks jump and settle back on the clients. The level is not changed.
public final class BlockSlam {
    private BlockSlam() {}

    public static final double MIN_RADIUS = 0.5D;

    @ScriptFunction(label = "Block Slam", category = "World", desc = """
        Fractures the ground around a position, or the block under it. On the server, plays for every player tracking \
        the chunk; on a client, for the local player only. False when no block there carries a shockwave
        """)
    public static boolean slam(final Level level, final Vec3 position, final double radius) {
        if (level == null || position == null) return false;

        Vec3 corner = new Vec3(Math.round(position.x), Math.floor(position.y), Math.round(position.z));
        Vec3 middle = new Vec3(Math.floor(position.x) + 0.5D, Math.floor(position.y), Math.floor(position.z) + 0.5D);
        Vec3 center = corner.distanceToSqr(position) < middle.distanceToSqr(position) ? corner : middle;
        BlockPos ground = BlockPos.containing(center);

        if (!canTransferShockwave(level, ground)) {
            ground = ground.below();
            center = center.subtract(0.0D, 1.0D, 0.0D);

            if (!canTransferShockwave(level, ground)) return false;
        }

        double clampedRadius = Math.max(MIN_RADIUS, radius);

        if (level instanceof ServerLevel serverLevel) {
            ClientBoundBlockSlam payload = new ClientBoundBlockSlam(center, clampedRadius);
            serverLevel.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(ground), false)
                .forEach(player -> NetworkingHelper.getInstance().sendToClient(player, payload));
        } else if (level.isClientSide()) {
            ((ClientLevelAccessor)level).akythera$getBlockFractures().slam(center, clampedRadius);
        }

        return true;
    }

    /// Whether the block at `pos` is ground a shockwave runs over: its collision covers the whole bottom face.
    public static boolean canTransferShockwave(final BlockGetter level, final BlockPos pos) {
        return Block.isFaceFull(level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty()), Direction.DOWN);
    }
}
