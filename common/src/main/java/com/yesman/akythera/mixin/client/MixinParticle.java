package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.ParticleHost;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(Particle.class)
public abstract class MixinParticle {
    @Shadow
    @Final
    protected ClientLevel level;

    /// An [AkytheraParticle] of a system with no level collides through the system's [ParticleHost#collide].
    @Redirect(
        method = "move",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collideBoundingBox(Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 akythera$collide(final CollisionContext context, final Vec3 motion, final AABB box, final Level collisionLevel, final List<VoxelShape> extraShapes) {
        if (level == null && (Object)this instanceof AkytheraParticle particle) {
            return particle.getEmitter().getSystem().getHost().collide(box, motion);
        }

        return Entity.collideBoundingBox(context, motion, box, collisionLevel, extraShapes);
    }
}
