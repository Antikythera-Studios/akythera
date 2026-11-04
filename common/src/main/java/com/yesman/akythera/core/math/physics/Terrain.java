package com.yesman.akythera.core.math.physics;

import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.OptionalDouble;
import java.util.function.Consumer;

/// The colliders a body stands on or falls over, and the fluids it floats in.
@FunctionalInterface
public interface Terrain {
    /// Half the width of the column [#groundHeight] gathers colliders in, in blocks.
    double PROBE_RADIUS = 1.0E-3D;

    /// Hands every collider box inside the world-space `area` to `sink`, in world space. False when part of `area`
    /// is not loaded.
    boolean collect(AABB area, Consumer<AABB> sink);

    /// Hands every fluid inside the world-space `area` to `sink`, in world space. None by default.
    default void collectFluids(final AABB area, final Consumer<FluidVolume> sink) {
    }

    /// The top of the highest collider a straight-down probe from `top` to `bottom` at (`x`, `z`) meets, in world space.
    /// Empty when it meets none or the column is not loaded. A collider the probe starts inside is skipped.
    default OptionalDouble groundHeight(final double x, final double z, final double top, final double bottom) {
        double[] highest = { Double.NEGATIVE_INFINITY };
        AABB column = new AABB(x - PROBE_RADIUS, bottom, z - PROBE_RADIUS, x + PROBE_RADIUS, top, z + PROBE_RADIUS);

        boolean loaded = collect(column, box -> {
            if (box.minX <= x && x <= box.maxX && box.minZ <= z && z <= box.maxZ && box.maxY <= top && box.maxY >= bottom) {
                highest[0] = Math.max(highest[0], box.maxY);
            }
        });

        return loaded && highest[0] != Double.NEGATIVE_INFINITY ? OptionalDouble.of(highest[0]) : OptionalDouble.empty();
    }

    /// The block colliders and the water and lava of `level`.
    static Terrain of(final Level level) {
        return new Terrain() {
            @Override
            public boolean collect(final AABB area, final Consumer<AABB> sink) {
                if (!level.hasChunksAt(Mth.floor(area.minX), Mth.floor(area.minZ), Mth.floor(area.maxX), Mth.floor(area.maxZ))) {
                    return false;
                }

                for (VoxelShape shape : level.getBlockCollisions(null, area)) {
                    shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> sink.accept(new AABB(x0, y0, z0, x1, y1, z1)));
                }

                return true;
            }

            @Override
            public void collectFluids(final AABB area, final Consumer<FluidVolume> sink) {
                for (BlockPos pos : BlockPos.betweenClosed(area)) {
                    FluidState fluid = level.getFluidState(pos);
                    FluidKind kind = fluid.is(FluidTags.WATER) ? FluidKind.WATER : fluid.is(FluidTags.LAVA) ? FluidKind.LAVA : null;

                    if (kind == null) continue;

                    sink.accept(new FluidVolume(
                        new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + fluid.getHeight(level, pos), pos.getZ() + 1), kind
                    ));
                }
            }
        };
    }

    /// An endless floor whose top is at `height`.
    static Terrain floor(final double height) {
        return (area, sink) -> {
            if (area.minY < height) sink.accept(new AABB(area.minX, area.minY - 1.0D, area.minZ, area.maxX, height, area.maxZ));

            return true;
        };
    }

    /// Endless water whose surface is at `surface`, `depth` blocks deep over an endless floor.
    static Terrain pool(final double surface, final double depth) {
        Terrain floor = floor(surface - depth);

        return new Terrain() {
            @Override
            public boolean collect(final AABB area, final Consumer<AABB> sink) {
                return floor.collect(area, sink);
            }

            @Override
            public void collectFluids(final AABB area, final Consumer<FluidVolume> sink) {
                if (area.minY < surface && area.maxY > surface - depth) {
                    sink.accept(new FluidVolume(new AABB(area.minX, surface - depth, area.minZ, area.maxX, surface, area.maxZ), FluidKind.WATER));
                }
            }
        };
    }

    /// The fluids a body floats in, each set up by its [ArmaturePhysicsData.FluidParameters].
    enum FluidKind {
        WATER,
        LAVA
    }

    /// A box filled with a fluid.
    record FluidVolume(AABB box, FluidKind kind) {}
}
