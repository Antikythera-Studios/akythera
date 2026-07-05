package com.yesman.akythera.core.asset.loader;

import com.yesman.akythera.api.asset.AssetAccessor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/// Read asset files in the resource pool, returns asset objects
/// that are actually used in game.
///
/// Avoid manual asset loads. Use [AssetAccessor] which forward
/// declares an assets and load the asset object in the first
/// reference.
public interface AssetLoader {
    /// Coord system converter as Blender has Z-Up system, and Minecraft has Y-Up basis.
    Matrix4f BLENDER_TO_MINECRAFT_COORD_SYSTEM = new Matrix4f().rotate((float)Math.toRadians(-90.0F), 1.0F, 0.0F, 0.0F);

    /// A resource path identifier
    Identifier resourcePath();
}
