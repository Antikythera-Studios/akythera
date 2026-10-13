package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.function.UnaryOperator;

/// A particle drawn as a 3D model, scaled and rotated in three axes, by [AkytheraMeshParticleGroup].
@ScriptType
public interface AkytheraMeshParticle extends AkytheraParticle {
    @ScriptFunction(pure = true, category = "Particle/Mesh")
    Vec3 getScale();

    @ScriptFunction(category = "Particle/Mesh")
    void setScale(Vec3 scale);

    Quaternionf getRotation();

    void setRotation(Quaternionf rotation);

    /// Submits this frame's model, placed relative to `cameraPosition`.
    void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Vec3 cameraPosition, float partialTick);

    /// Draws this frame's model on the spot, for a renderer outside the particle engine: placed in `poseStack`'s space,
    /// full-bright, its color multiplied by `tint` (ARGB), and drawn with the texture `textures` turns its own into.
    /// Translucent faces are drawn farthest from `eye`, the camera in `poseStack`'s space, first.
    void drawImmediate(PoseStack poseStack, ByteBufferBuilder byteBuffer, float partialTick, int tint, UnaryOperator<Identifier> textures, Vector3fc eye);
}
