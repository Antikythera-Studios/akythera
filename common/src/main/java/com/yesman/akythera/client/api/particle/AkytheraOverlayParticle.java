package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.yesman.akythera.client.api.component.impl.OverlayRendererComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.OverlayParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/// A second draw of an entity's model an [OverlayParticleBlueprint] describes. The particle engine ticks it and draws
/// nothing for it: the [OverlayRendererComponent] it is kept in hands it to whatever draws the model.
public class AkytheraOverlayParticle extends Particle implements AkytheraParticle {
    /// The entity shader over [com.mojang.blaze3d.vertex.DefaultVertexFormat#ENTITY] vertices, two-sided, with a
    /// texture matrix. It colors only the fragments at the depth already written, and writes no depth. The multiply
    /// pipeline leaves the overlay's color unshaded, so its faces filter the model alike.
    private static final Map<OverlayParticleBlueprint.Blend, RenderPipeline> PIPELINES = Map.of(
        OverlayParticleBlueprint.Blend.TRANSLUCENT, pipeline("translucent", BlendFunction.TRANSLUCENT, "PER_FACE_LIGHTING"),
        OverlayParticleBlueprint.Blend.ADDITIVE, pipeline("additive", BlendFunction.LIGHTNING, "PER_FACE_LIGHTING"),
        OverlayParticleBlueprint.Blend.GLINT, pipeline("glint", BlendFunction.GLINT, "PER_FACE_LIGHTING"),
        OverlayParticleBlueprint.Blend.MULTIPLY, pipeline("multiply", new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO), "NO_CARDINAL_LIGHTING")
    );

    private static final Function<Look, RenderType> RENDER_TYPES = Util.memoize(
        look -> RenderType.create(
            "akythera_overlay",
            RenderSetup.builder(PIPELINES.get(look.blend))
                .withTexture("Sampler0", look.texture, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST))
                .useLightmap()
                .setTextureTransform(new TextureTransform("akythera_overlay_texturing", () -> {
                    double seconds = Util.getMillis() / 1000.0D;

                    return new Matrix4f()
                        .translation((float)(seconds * look.uScroll % 1.0D), (float)(seconds * look.vScroll % 1.0D), 0.0F)
                        .rotateZ(look.uvRotation * Mth.DEG_TO_RAD)
                        .scale(look.uvScale);
                }))
                .createRenderSetup()
        )
    );

    private final ParticleState state;
    private final OverlayParticleBlueprint blueprint;

    /// Where this particle is kept while it lives.
    private final OverlayRendererComponent renderer;

    /// Turns the blueprint's texture into the one drawn.
    private UnaryOperator<Identifier> textures = UnaryOperator.identity();

    /// @throws IllegalStateException when the emitter's system has nothing to draw over
    public AkytheraOverlayParticle(final AkytheraParticleEmitter emitter, final OverlayParticleBlueprint blueprint) {
        super(emitter.getSystem().getLevel(), emitter.getWorldTransform().loc().x, emitter.getWorldTransform().loc().y, emitter.getWorldTransform().loc().z);

        OverlayRendererComponent target = emitter.getSystem().getHost().overlayRenderer(emitter.getSystem());

        if (target == null) throw new IllegalStateException("An overlay particle has nothing to draw over");

        this.state = new ParticleState(emitter, emitter.getWorldTransform().loc(), blueprint.properties(), blueprint.color());
        this.blueprint = blueprint;
        this.renderer = target;
        this.hasPhysics = false;
        this.lifetime = blueprint.lifetime();
    }

    /// @param lighting the shader define that picks how the overlay's color is shaded
    private static RenderPipeline pipeline(final String name, final BlendFunction blend, final String lighting) {
        return RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Akythera.identifier("pipeline/overlay_" + name))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine(lighting)
            .withShaderDefine("APPLY_TEXTURE_MATRIX")
            .withColorTargetState(new ColorTargetState(blend))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false))
            .build();
    }

    @Override
    public void emitterInit(final AkytheraParticleEmitter emitter) {
        state.runInitializers(this);
        renderer.add(this);
    }

    /// Ages the particle and runs its updaters. It is removed with the entity its system is attached to, and at the
    /// end of its lifetime unless that is [OverlayParticleBlueprint#ENDLESS].
    @Override
    public void tick() {
        ParticleAttachment attachment = getEmitter().getSystem().getAttachment();
        boolean expired = lifetime != OverlayParticleBlueprint.ENDLESS && age >= lifetime;

        age++;

        if (expired || attachment != null && level.getEntity(attachment.entityId()) == null) {
            remove();

            return;
        }

        state.update(this);
    }

    @Override
    public void remove() {
        super.remove();
        renderer.remove(this);
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.NO_RENDER;
    }

    /// The render type the model is drawn again with.
    public RenderType renderType() {
        return RENDER_TYPES.apply(
            new Look(
                textures.apply(blueprint.texture()), blueprint.blend(), blueprint.uvScale(), blueprint.uvRotation(), blueprint.uScroll(), blueprint.vScroll()
            )
        );
    }

    /// The color the model is drawn again in, ARGB.
    public int color() {
        return state.getColor().packARGB();
    }

    /// `modelLight`, the light the model is drawn in, with the particle's own brightness added.
    public int lightCoords(final int modelLight) {
        return state.lightCoords(modelLight);
    }

    /// Draws with the texture `textures` turns the blueprint's into.
    public void setTextures(final UnaryOperator<Identifier> textures) {
        this.textures = textures;
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

    /// What one render type draws with.
    private record Look(Identifier texture, OverlayParticleBlueprint.Blend blend, float uvScale, float uvRotation, float uScroll, float vScroll) {
    }
}
