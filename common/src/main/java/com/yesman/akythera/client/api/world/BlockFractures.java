package com.yesman.akythera.client.api.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.world.BlockSlam;
import com.yesman.akythera.client.config.AkytheraClientConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// The ground fractures of a [ClientLevel]. A fractured block is drawn jumping by this, while the chunk meshes read
/// its position as air (`MixinRenderSectionRegion`), until it settles back.
public final class BlockFractures {
    private static final float RETURN_TICKS = 5.0F; // settling back to the original pose
    private static final int RESTORE_TICKS = 3; // shown in the chunk mesh again before the end, so the mesh is rebuilt before the drawn block is gone
    private static final int CRUMBLE_TICKS = 10; // the last ticks that drop block particles

    private final ClientLevel level;

    /// The fractured blocks by [BlockPos#asLong].
    private final Long2ObjectMap<FracturedBlock> blocks = new Long2ObjectOpenHashMap<>();

    public BlockFractures(final ClientLevel level) {
        this.level = level;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /// Spreads a shockwave from `center`, on the ground block's level, over `radius` blocks. It runs along rays half a
    /// block apart at the edge. A ray climbs one block and drops one block; a wall two blocks high, a cliff two blocks
    /// deep, or a fluid on or over the ground stops it. Nothing happens while [AkytheraClientConfig#isBlockSlamsEnabled] is off.
    public void slam(final Vec3 center, final double radius) {
        if (!AkytheraClientConfig.isBlockSlamsEnabled()) return;

        int rays = Mth.ceil(radius * Mth.TWO_PI * 2.0D);
        double bounceScale = Math.min(1.0D / (radius * radius), 0.1D);
        int lifetimeRange = Math.max(1, (int)(radius * 80.0D));
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int ray = 0; ray < rays; ray++) {
            double angle = Mth.TWO_PI * ray / rays;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int x = Mth.floor(center.x);
            int y = Mth.floor(center.y);
            int z = Mth.floor(center.z);
            int stepX = dx > 0.0D ? 1 : -1;
            int stepZ = dz > 0.0D ? 1 : -1;
            double deltaX = Math.abs(1.0D / dx);
            double deltaZ = Math.abs(1.0D / dz);
            double nextX = dx == 0.0D ? Double.POSITIVE_INFINITY : (dx > 0.0D ? x + 1 - center.x : center.x - x) * deltaX;
            double nextZ = dz == 0.0D ? Double.POSITIVE_INFINITY : (dz > 0.0D ? z + 1 - center.z : center.z - z) * deltaZ;

            // Walks the cells the ray crosses, nearest first
            for (double travel = 0.0D; travel <= radius; ) {
                if (BlockSlam.canTransferShockwave(level, probe.set(x, y + 1, z))) {
                    if (BlockSlam.canTransferShockwave(level, probe.set(x, y + 2, z))) break; // wall

                    y++;
                } else if (!BlockSlam.canTransferShockwave(level, probe.set(x, y, z))) {
                    if (!BlockSlam.canTransferShockwave(level, probe.set(x, y - 1, z))) break; // cliff

                    y--;
                }

                BlockPos pos = new BlockPos(x, y, z);
                BlockPos above = pos.above();

                if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(above).isEmpty()) break;

                double toBlockX = x + 0.5D - center.x;
                double toBlockZ = z + 0.5D - center.z;
                double distance = Math.sqrt(toBlockX * toBlockX + toBlockZ * toBlockZ);
                BlockState state = level.getBlockState(pos);
                BlockState aboveState = level.getBlockState(above);
                boolean carriesAbove = !aboveState.isAir();
                boolean fracturable = distance <= radius
                    && !blocks.containsKey(pos.asLong())
                    && !state.hasBlockEntity()
                    && state.getRenderShape() == RenderShape.MODEL
                    && (!carriesAbove || aboveState.getCollisionShape(level, above).isEmpty()
                        && !aboveState.hasBlockEntity()
                        && aboveState.getRenderShape() == RenderShape.MODEL
                        && !aboveState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF));

                if (fracturable) {
                    double ratio = distance / radius;
                    // The impact direction (down) crossed with the direction to the block
                    float axisLength = (float)distance;
                    float axisX = axisLength < 1.0E-4F ? 1.0F : (float)-toBlockZ / axisLength;
                    float axisZ = axisLength < 1.0E-4F ? 0.0F : (float)toBlockX / axisLength;
                    Quaternionf tilt = new Quaternionf()
                        .rotationAxis(Mth.DEG_TO_RAD * ((float)ratio * 15.0F + level.getRandom().nextFloat() * 10.0F - 5.0F), axisX, 0.0F, axisZ)
                        .rotateAxis(Mth.DEG_TO_RAD * (level.getRandom().nextFloat() * 15.0F - 7.5F), 1.0F, 0.0F, 0.0F)
                        .rotateAxis(Mth.DEG_TO_RAD * (level.getRandom().nextFloat() * 40.0F - 20.0F), 0.0F, 1.0F, 0.0F)
                        .rotateAxis(Mth.DEG_TO_RAD * (level.getRandom().nextFloat() * 15.0F - 7.5F), 0.0F, 0.0F, 1.0F);
                    FracturedBlock block = new FracturedBlock(
                        level, pos, state, carriesAbove ? aboveState : null,
                        (float)Math.max(0.0D, ratio - 0.5D) * 0.5F, tilt,
                        (float)(distance * distance * bounceScale), 30 + level.getRandom().nextInt(lifetimeRange)
                    );

                    blocks.put(pos.asLong(), block);
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);

                    for (int corner = 0; corner < 4; corner += level.getRandom().nextInt(4)) {
                        level.addParticle(
                            new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x + (corner % 2), y + 1.0D, z + 1 - (corner % 2), 0.0D, 0.0D, 0.0D
                        );
                    }
                }

                if (nextX < nextZ) {
                    travel = nextX;
                    nextX += deltaX;
                    x += stepX;
                } else {
                    travel = nextZ;
                    nextZ += deltaZ;
                    z += stepZ;
                }
            }
        }
    }

    /// Ages every fractured block. One is shown in the chunk mesh again [#RESTORE_TICKS] before its end, or at once
    /// when the level no longer holds its block there.
    public void tick() {
        ObjectIterator<FracturedBlock> iterator = blocks.values().iterator();

        while (iterator.hasNext()) {
            FracturedBlock block = iterator.next();
            boolean replaced = level.getBlockState(block.pos) != block.state;

            block.age++;

            if (!block.restored && (replaced || block.age >= block.lifetime - RESTORE_TICKS)) {
                block.restored = true;
                level.sendBlockUpdated(block.pos, block.state, block.state, Block.UPDATE_IMMEDIATE);
            }

            if (replaced || block.age >= block.lifetime) {
                iterator.remove();
                continue;
            }

            if (block.lifetime - block.age < CRUMBLE_TICKS) {
                level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, block.state),
                    block.pos.getX() + level.getRandom().nextDouble(), block.pos.getY() + 1.0D, block.pos.getZ() + level.getRandom().nextDouble(),
                    0.0D, 0.0D, 0.0D
                );
            }
        }
    }

    /// The positions the chunk meshes of the 3x3x3 sections from the given minimum section read as air.
    public LongSet hiddenPositions(final int minSectionX, final int minSectionY, final int minSectionZ) {
        if (blocks.isEmpty()) return LongSets.EMPTY_SET;

        int minX = SectionPos.sectionToBlockCoord(minSectionX);
        int minY = SectionPos.sectionToBlockCoord(minSectionY);
        int minZ = SectionPos.sectionToBlockCoord(minSectionZ);
        LongSet hidden = new LongOpenHashSet();

        for (FracturedBlock block : blocks.values()) {
            BlockPos pos = block.pos;

            if (block.restored || pos.getX() < minX || pos.getX() >= minX + 48 || pos.getZ() < minZ || pos.getZ() >= minZ + 48) continue;
            if (pos.getY() >= minY && pos.getY() < minY + 48) hidden.add(pos.asLong());
            if (block.above != null && pos.getY() + 1 >= minY && pos.getY() + 1 < minY + 48) hidden.add(BlockPos.asLong(pos.getX(), pos.getY() + 1, pos.getZ()));
        }

        return hidden.isEmpty() ? LongSets.EMPTY_SET : hidden;
    }

    /// The fractured blocks in `frustum`, posed at `partialTick`.
    public ParticleGroupRenderState extractRenderState(final Frustum frustum, final float partialTick) {
        List<Pose> poses = new ArrayList<>();

        for (FracturedBlock block : blocks.values()) {
            if (!frustum.isVisible(new AABB(block.pos).inflate(1.0D))) continue;

            float time = block.age + partialTick;
            float returning = Mth.clamp((time - (block.lifetime - RETURN_TICKS)) / RETURN_TICKS, 0.0F, 1.0F);
            // A parabola from the ground up to the bounce height and back, longer for a higher one
            float bounceTicks = Math.max(block.bounceHeight * 8.0F, 8.0F);
            float curvature = 4.0F / (bounceTicks * bounceTicks);
            float peakTime = Mth.sqrt(block.bounceHeight / curvature);
            float bounce = Math.max(block.bounceHeight - curvature * (time - peakTime) * (time - peakTime), 0.0F);

            poses.add(new Pose(block, block.lift * (1.0F - returning) + bounce, new Quaternionf(block.tilt).slerp(new Quaternionf(), returning)));
        }

        return new State(poses);
    }

    /// A block a shockwave lifted, drawn at its position with `above`, the block it carries.
    private static final class FracturedBlock {
        private final BlockPos pos;
        private final BlockState state;
        private final @Nullable BlockState above;

        /// Height the block is lifted by while it lasts, in blocks. Higher toward the edge.
        private final float lift;

        /// Rotation about the block's center, tilting away from the slam's center.
        private final Quaternionf tilt;

        /// Height of the jump at the start, in blocks.
        private final float bounceHeight;

        private final int lifetime; // ticks
        private int age;

        /// Whether the chunk mesh shows the block again.
        private boolean restored;

        /// Draw states of [#state] and [#above], lit from the position over [#pos] since the level holds `state` at `pos`.
        private final MovingBlockRenderState blockRenderState;
        private final @Nullable MovingBlockRenderState aboveRenderState;

        private FracturedBlock(
            final ClientLevel level, final BlockPos pos, final BlockState state, final @Nullable BlockState above, final float lift, final Quaternionf tilt,
            final float bounceHeight, final int lifetime
        ) {
            this.pos = pos;
            this.state = state;
            this.above = above;
            this.lift = lift;
            this.tilt = tilt;
            this.bounceHeight = bounceHeight;
            this.lifetime = lifetime;
            this.blockRenderState = renderState(level, pos.above(), state);
            this.aboveRenderState = above == null ? null : renderState(level, pos.above(), above);
        }

        private static MovingBlockRenderState renderState(final ClientLevel level, final BlockPos lightPos, final BlockState blockState) {
            MovingBlockRenderState renderState = new MovingBlockRenderState();

            renderState.randomSeedPos = lightPos;
            renderState.blockPos = lightPos;
            renderState.blockState = blockState;
            renderState.biome = level.getBiome(lightPos);
            renderState.cardinalLighting = level.cardinalLighting();
            renderState.lightEngine = level.getLightEngine();

            return renderState;
        }
    }

    /// One frame's pose of `block`: its lift plus its bounce, and its tilt.
    private record Pose(FracturedBlock block, float offsetY, Quaternionf tilt) {
    }

    private record State(List<Pose> poses) implements ParticleGroupRenderState {
        @Override
        public void submit(final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
            PoseStack poseStack = new PoseStack();

            for (Pose pose : poses) {
                FracturedBlock block = pose.block;

                poseStack.pushPose();
                poseStack.translate(block.pos.getX() - camera.pos.x, block.pos.getY() - camera.pos.y, block.pos.getZ() - camera.pos.z);
                poseStack.translate(0.5F, 0.5F, 0.5F);
                poseStack.mulPose(pose.tilt);
                poseStack.translate(-0.5F, pose.offsetY - 0.5F, -0.5F);
                submitNodeCollector.submitMovingBlock(poseStack, block.blockRenderState, 0);

                if (block.aboveRenderState != null) {
                    poseStack.translate(0.0F, 1.0F, 0.0F);
                    submitNodeCollector.submitMovingBlock(poseStack, block.aboveRenderState, 0);
                }

                poseStack.popPose();
            }
        }
    }
}
