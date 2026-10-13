package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.ParticleProperties;
import com.yesman.akythera.core.blueprint.particle.StaticMeshParticleBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.UnaryOperator;

/// A 3D model a [StaticMeshParticleBlueprint] describes.
@ScriptType
public class AkytheraStaticMeshParticle extends Particle implements AkytheraMeshParticle {
    private final ParticleState state;
    private final AssetAccessor<? extends Mesh<?>> mesh;

    /// The starting size of the index buffer [#drawSorted] sorts into, which grows as needed.
    private static final int SORTED_INDEX_BYTES = 1536;

    /// The texture the model is drawn with, or null for the one the mesh's own metadata names.
    private final @Nullable Identifier texture;

    private Vec3 scale;

    /// [#scale] one tick back.
    private Vec3 oScale;

    private final Quaternionf rotation;

    /// [#rotation] one tick back.
    private final Quaternionf oRotation;

    /// @param placement the model's position, starting rotation and starting scale, in world space
    public AkytheraStaticMeshParticle(
        final AkytheraParticleEmitter emitter,
        final ParticleProperties properties,
        final AssetAccessor<? extends Mesh<?>> mesh,
        final @Nullable Identifier texture,
        final RGBA color,
        final Transform placement
    ) {
        super(emitter.getSystem().getLevel(), placement.loc().x, placement.loc().y, placement.loc().z);

        this.state = new ParticleState(emitter, placement.loc(), properties, color);
        this.mesh = mesh;
        this.texture = texture;
        this.hasPhysics = properties.hasPhysics();
        this.setSize(properties.bbWidth(), properties.bbHeight());
        this.lifetime = properties.lifetime();
        this.scale = placement.sca();
        this.oScale = this.scale;
        this.rotation = new Quaternionf(placement.rot());
        this.oRotation = new Quaternionf(this.rotation);
    }

    @Override
    public void emitterInit(final AkytheraParticleEmitter emitter) {
        state.runInitializers(this);

        xo = x - emitter.getTickMotion().x;
        yo = y - emitter.getTickMotion().y;
        zo = z - emitter.getTickMotion().z;
        oScale = scale;
        oRotation.set(rotation);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oScale = scale;
        oRotation.set(rotation);

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
    }

    @Override
    public void submit(final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final Vec3 cameraPosition, final float partialTick) {
        Mesh<?> resolved = mesh.getOrHandleException(Akythera.LOGGER::error);

        if (resolved == null) return;

        poseStack.pushPose();
        place(poseStack, cameraPosition, partialTick);
        submitMesh(poseStack, submitNodeCollector, resolved, RenderTypes.entityTranslucent(drawnTexture(resolved)), getLightCoords(partialTick), partialTick);
        poseStack.popPose();
    }

    @Override
    public void drawImmediate(
        final PoseStack poseStack,
        final ByteBufferBuilder byteBuffer,
        final float partialTick,
        final int tint,
        final UnaryOperator<Identifier> textures,
        final Vector3fc eye
    ) {
        Mesh<?> resolved = mesh.getOrHandleException(Akythera.LOGGER::error);

        if (resolved == null) return;

        RGBA color = state.getColor();

        poseStack.pushPose();
        place(poseStack, Vec3.ZERO, partialTick);
        drawMeshImmediate(
            poseStack,
            byteBuffer,
            resolved,
            RenderTypes.entityTranslucent(textures.apply(drawnTexture(resolved))),
            ARGB.multiply(ARGB.colorFromFloat(color.a(), color.r(), color.g(), color.b()), tint),
            eye
        );
        poseStack.popPose();
    }

    /// Moves `poseStack` to this frame's position relative to `origin`, then turns and scales it.
    private void place(final PoseStack poseStack, final Vec3 origin, final float partialTick) {
        Vec3 drawnScale = oScale.lerp(scale, partialTick);

        poseStack.translate(Mth.lerp(partialTick, xo, x) - origin.x, Mth.lerp(partialTick, yo, y) - origin.y, Mth.lerp(partialTick, zo, z) - origin.z);
        poseStack.mulPose(new Quaternionf(oRotation).slerp(rotation, partialTick));
        poseStack.scale((float)drawnScale.x, (float)drawnScale.y, (float)drawnScale.z);
    }

    /// [#texture], else the one `resolved`'s metadata names, else the missing texture.
    private Identifier drawnTexture(final Mesh<?> resolved) {
        Identifier drawn = texture != null ? texture : resolved.getMetadata().texture();

        return drawn == null ? MissingTextureAtlasSprite.getLocation() : drawn;
    }

    /// Submits `resolved` under `poseStack`, which already places, turns and scales the model.
    protected void submitMesh(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final Mesh<?> resolved,
        final RenderType renderType,
        final int lightCoords,
        final float partialTick
    ) {
        RGBA color = state.getColor();

        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (pose, buffer) -> resolved.draw(
                pose,
                buffer,
                Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY),
                lightCoords,
                color.r(), color.g(), color.b(), color.a(),
                OverlayTexture.NO_OVERLAY
            )
        );
    }

    /// Draws `resolved` on the spot under `poseStack`, which already places, turns and scales the model.
    ///
    /// @param color ARGB
    /// @param eye   the camera, in the space `poseStack` places vertices in
    protected void drawMeshImmediate(
        final PoseStack poseStack, final ByteBufferBuilder byteBuffer, final Mesh<?> resolved, final RenderType renderType, final int color, final Vector3fc eye
    ) {
        BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);

        resolved.draw(
            poseStack.last(),
            builder,
            Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY),
            LightCoordsUtil.FULL_BRIGHT,
            ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color), ARGB.alphaFloat(color),
            OverlayTexture.NO_OVERLAY
        );

        drawSorted(renderType, builder.build(), eye);
    }

    /// Draws `meshData`. A render type that sorts on upload is translucent: its quads are sorted farthest from `eye`
    /// first, as vanilla does, and only the fragments nearest the camera are colored ([RenderUtils#drawBuffers]).
    protected static void drawSorted(final RenderType renderType, final @Nullable MeshData meshData, final Vector3fc eye) {
        if (meshData == null) return;

        try (ByteBufferBuilder sortedIndices = new ByteBufferBuilder(SORTED_INDEX_BYTES)) {
            boolean translucent = renderType.sortOnUpload();

            if (translucent) meshData.sortQuads(sortedIndices, VertexSorting.byDistance(eye));

            RenderUtils.drawImmediate(renderType, meshData, translucent);
        }
    }

    @Override
    public ParticleRenderType getGroup() {
        return AkytheraParticleRenderTypes.MESH;
    }

    @Override
    protected int getLightCoords(final float partialTickTime) {
        return level == null ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords(super.getLightCoords(partialTickTime));
    }

    // ***************************************************************
    // AkytheraMeshParticle
    // ***************************************************************

    @Override
    public Vec3 getScale() {
        return scale;
    }

    @Override
    public void setScale(final Vec3 scale) {
        this.scale = scale;
    }

    @Override
    public Quaternionf getRotation() {
        return rotation;
    }

    @Override
    public void setRotation(final Quaternionf rotation) {
        this.rotation.set(rotation);
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
}
