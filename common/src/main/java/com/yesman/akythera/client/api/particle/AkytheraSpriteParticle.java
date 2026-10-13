package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.core.blueprint.particle.SpriteParticleBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/// A camera-facing quad a [SpriteParticleBlueprint] describes.
@ScriptType
public class AkytheraSpriteParticle extends SingleQuadParticle implements AkytheraParticle {
    private static final Function<Identifier, RenderType> RENDER_TYPES = Util.memoize(
        texture -> RenderType.create(
            "akythera_sprite_particle",
            RenderSetup.builder(Layer.TRANSLUCENT.pipeline())
                .withTexture("Sampler0", texture, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .useLightmap()
                .createRenderSetup()
        )
    );

    private final ParticleState state;
    private final SpriteSet sprites;

    /// The quad's half extent, in blocks.
    private float size;

    /// The quad's rotation about the view axis, in degrees.
    private float rollDegrees;

    public AkytheraSpriteParticle(final AkytheraParticleEmitter emitter, final SpriteParticleBlueprint blueprint) {
        this(emitter, blueprint, blueprint.sprites().map(AkytheraSpriteSet::new, AkytheraSpriteSet::vanilla), emitter.getWorldTransform().loc());
    }

    private AkytheraSpriteParticle(
        final AkytheraParticleEmitter emitter,
        final SpriteParticleBlueprint blueprint,
        final SpriteSet sprites,
        final Vec3 spawnPoint
    ) {
        super(emitter.getSystem().getLevel(), spawnPoint.x, spawnPoint.y, spawnPoint.z, sprites.first());

        this.state = new ParticleState(emitter, spawnPoint, blueprint.properties(), blueprint.color());
        this.sprites = sprites;
        this.hasPhysics = blueprint.properties().hasPhysics();
        this.setSize(blueprint.properties().bbWidth(), blueprint.properties().bbHeight());
        this.lifetime = blueprint.properties().lifetime();
        this.size = blueprint.size();
        this.rollDegrees = blueprint.roll();

        this.writeRenderState();
        this.oRoll = this.roll;
    }

    @Override
    public void emitterInit(final AkytheraParticleEmitter emitter) {
        state.runInitializers(this);

        xo = x - emitter.getTickMotion().x;
        yo = y - emitter.getTickMotion().y;
        zo = z - emitter.getTickMotion().z;
        writeRenderState();
        oRoll = roll;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;

        if (age++ >= lifetime) {
            remove();

            return;
        }

        if (!state.update(this)) return;

        // Particle#move reads the motion from, and zeroes a blocked horizontal axis on, xd / yd / zd
        Vec3 motion = state.getMotion();
        setParticleSpeed(motion.x, motion.y, motion.z);
        move(xd, yd, zd);
        state.moved(new Vec3(xd, yd, zd), onGround);

        setSpriteFromAge(sprites);
        writeRenderState();
    }

    /// Copies this particle's own state into the fields vanilla's quad extraction reads.
    private void writeRenderState() {
        quadSize = size;
        rCol = state.getColor().r();
        gCol = state.getColor().g();
        bCol = state.getColor().b();
        alpha = state.getColor().a();
        roll = rollDegrees * Mth.DEG_TO_RAD;
    }

    /// What draws a [Frame] from `texture` outside the particle engine: vanilla's translucent particle pipeline, with
    /// the vertices in [DefaultVertexFormat#PARTICLE].
    public static RenderType renderType(final Identifier texture) {
        return RENDER_TYPES.apply(texture);
    }

    /// What a frame between the last tick (`partialTick` 0) and this one (1) draws, for a renderer of its own.
    public Frame frame(final float partialTick) {
        return new Frame(
            new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y), Mth.lerp(partialTick, zo, z)),
            quadSize,
            Mth.lerp(partialTick, oRoll, roll),
            state.getColor(),
            sprite,
            sprites instanceof AkytheraSpriteSet ids ? ids.spriteId(age, lifetime) : null
        );
    }

    /// In blocks.
    @ScriptFunction(pure = true, category = "Particle/Sprite", desc = "The quad's half extent, in blocks")
    public float getSpriteSize() {
        return size;
    }

    @ScriptFunction(category = "Particle/Sprite", desc = "Sets the quad's half extent, in blocks")
    public void setSpriteSize(final float size) {
        this.size = size;
    }

    /// In degrees.
    @ScriptFunction(pure = true, category = "Particle/Sprite", desc = "The quad's rotation about the view axis, in degrees")
    public float getRoll() {
        return rollDegrees;
    }

    @ScriptFunction(category = "Particle/Sprite", desc = "Sets the quad's rotation about the view axis, in degrees")
    public void setRoll(final float roll) {
        this.rollDegrees = roll;
    }

    @Override
    protected Layer getLayer() {
        return Layer.bySprite(sprite);
    }

    @Override
    protected int getLightCoords(final float partialTickTime) {
        return level == null ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords(super.getLightCoords(partialTickTime));
    }

    // ***************************************************************
    // AkytheraParticle
    // ***************************************************************

    @Override
    public ParticleState state() {
        return state;
    }

    @Override
    public Vec3 getPosition() {
        return new Vec3(x, y, z);
    }

    @Override
    public RandomSource getRandom() {
        return random;
    }

    @Override
    public int getAge() {
        return age;
    }

    /// One frame of a sprite particle.
    ///
    /// @param position in world space
    /// @param size     the quad's half extent, in blocks
    /// @param roll     the quad's rotation about the view axis, in radians
    /// @param sprite   the particle atlas sprite shown
    /// @param spriteId the id [#sprite] was looked up by, or null for sprites borrowed from a vanilla particle type
    public record Frame(Vec3 position, float size, float roll, RGBA color, TextureAtlasSprite sprite, @Nullable Identifier spriteId) {
    }
}
