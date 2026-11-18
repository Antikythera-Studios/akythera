package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.world.BlockFractures;
import com.yesman.akythera.mixinaccessor.client.ClientLevelAccessor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Meshes the positions [BlockFractures] hides as air.
///
/// The neighbors of a hidden block then draw the faces it covered, unculled and without its ambient occlusion. Those
/// faces are lit from the hidden position, which the light engine keeps dark as the inside of a solid block, so a
/// hidden position takes the light of the first shown position above it instead: the hole is lit as if open to the
/// sky it faces.
@Mixin(RenderSectionRegion.class)
public abstract class MixinRenderSectionRegion {
    @Shadow
    @Final
    private LevelLightEngine lightEngine;

    /// The hidden positions in this region, by [BlockPos#asLong]. Taken on the main thread when the region is built,
    /// since the region is meshed off it.
    @Unique
    private LongSet akythera$hiddenPositions;

    @Inject(at = @At("TAIL"), method = "<init>")
    private void akythera$takeHiddenPositions(
        final ClientLevel level, final int minSectionX, final int minSectionY, final int minSectionZ, final SectionCopy[] sections, final CallbackInfo callbackInfo
    ) {
        akythera$hiddenPositions = ((ClientLevelAccessor)level).akythera$getBlockFractures().hiddenPositions(minSectionX, minSectionY, minSectionZ);
    }

    @Inject(at = @At("HEAD"), method = "getBlockState", cancellable = true)
    private void akythera$hideFracturedBlock(final BlockPos pos, final CallbackInfoReturnable<BlockState> callbackInfo) {
        if (akythera$hiddenPositions.contains(pos.asLong())) callbackInfo.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    public int getBrightness(final LightLayer layer, final BlockPos pos) {
        BlockPos.MutableBlockPos litPos = pos.mutable();

        while (akythera$hiddenPositions.contains(litPos.asLong())) litPos.move(Direction.UP);

        return lightEngine.getLayerListener(layer).getLightValue(litPos);
    }
}
