package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/// A ribbon a [TrailParticleBlueprint] describes, swept by the emitter it follows.
///
/// Edges are sampled twice a tick, at its middle and its end, and stamped with their time in ticks. A frame draws the
/// edges between `now - trailLifetime` and `now`, where `now` is the drawn time between the last two ticks, closed by
/// one more edge sampled at `now` itself.
@ScriptType
public class AkytheraTrailParticle extends Particle implements AkytheraParticle {
    private final ParticleState state;
    private final TrailParticleBlueprint blueprint;

    /// The socket the system follows, or null when the trail follows the system's own transform.
    private final @Nullable ParticleAttachment attachment;

    /// The trail's far side from the emitter's origin, in the emitter's space.
    private final Vec3 farSide;

    /// Sampled edges, oldest first.
    private final List<Edge> edges = new ArrayList<>();

    /// The emitter's transform at the last tick and the one before, in world space.
    private Transform source;
    private Transform previousSource;

    /// Whether the trail still samples edges: until its lifetime ends or its socket is gone.
    private boolean sampling = true;

    public AkytheraTrailParticle(final AkytheraParticleEmitter emitter, final TrailParticleBlueprint blueprint) {
        super(emitter.getSystem().getLevel(), emitter.getWorldTransform().loc().x, emitter.getWorldTransform().loc().y, emitter.getWorldTransform().loc().z);

        this.state = new ParticleState(emitter, emitter.getWorldTransform().loc(), blueprint.properties(), blueprint.color());
        this.blueprint = blueprint;
        this.attachment = emitter.getSystem().getAttachment();
        this.farSide = new Vec3(0.0D, 0.0D, blueprint.length());
        this.source = emitter.getWorldTransform();
        this.previousSource = this.source;
        this.hasPhysics = false;
        this.setSize(blueprint.properties().bbWidth(), blueprint.properties().bbHeight());
        this.lifetime = blueprint.properties().lifetime();
        this.edges.add(edgeOf(this.source, 0.0F));
    }

    @Override
    public void emitterInit(final AkytheraParticleEmitter emitter) {
        state.runInitializers(this);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        age++;

        if (!state.update(this)) return;

        if (sampling) {
            Transform sampled = sourceTransform();

            if (sampled == null || age > lifetime) {
                sampling = false;
            } else {
                previousSource = source;
                source = sampled;
                edges.add(edgeOf(Transform.slerp(previousSource, source, 0.5F), age - 0.5F));
                edges.add(edgeOf(source, age));
                setPos(source.loc().x, source.loc().y, source.loc().z);
            }
        }

        // The earliest time a later frame draws from
        float cutoff = age - 1 - blueprint.trailLifetime();

        while (edges.size() >= 2 && edges.get(1).time() <= cutoff) {
            edges.removeFirst();
        }

        if (!sampling && (edges.size() < 2 || edges.getLast().time() <= cutoff)) remove();
    }

    /// This frame's draw, with the edges camera-relative, or null when nothing of the trail is visible.
    ///
    /// @param tint     multiplies the trail's color, ARGB
    /// @param textures turns the blueprint's texture into the one drawn
    public TrailRenderer.@Nullable Trail extractTrail(final Vec3 cameraPosition, final float partialTick, final int tint, final UnaryOperator<Identifier> textures) {
        float now = age - 1 + partialTick;
        float cutoff = now - blueprint.trailLifetime();
        List<Edge> controls = new ArrayList<>(edges.size() + 1);

        for (Edge edge : edges) {
            if (edge.time() < now) controls.add(edge);
        }

        if (sampling) controls.add(edgeOf(Transform.slerp(previousSource, source, partialTick), now));

        int first = 0;

        while (first + 1 < controls.size() && controls.get(first + 1).time() <= cutoff) {
            first++;
        }

        int count = controls.size() - first;

        if (count < 2) return null;

        Edge oldest = controls.get(first);
        Edge next = controls.get(first + 1);
        float tail = Mth.clamp((cutoff - oldest.time()) / (next.time() - oldest.time()), 0.0F, 1.0F);
        float[] floats = new float[count * 6];

        for (int i = 0; i < count; i++) {
            Edge edge = controls.get(first + i);

            floats[i * 6] = (float)(edge.start().x - cameraPosition.x);
            floats[i * 6 + 1] = (float)(edge.start().y - cameraPosition.y);
            floats[i * 6 + 2] = (float)(edge.start().z - cameraPosition.z);
            floats[i * 6 + 3] = (float)(edge.end().x - cameraPosition.x);
            floats[i * 6 + 4] = (float)(edge.end().y - cameraPosition.y);
            floats[i * 6 + 5] = (float)(edge.end().z - cameraPosition.z);
        }

        float fadeOut = blueprint.fadeOut();
        float elapsed = now / lifetime;
        float fade = fadeOut >= 1.0F || elapsed <= fadeOut ? 1.0F : Mth.clamp(1.0F - (elapsed - fadeOut) / (1.0F - fadeOut), 0.0F, 1.0F);

        return new TrailRenderer.Trail(
            TrailRenderer.renderType(textures.apply(blueprint.texture())),
            floats,
            count,
            tail,
            (count - 1) * blueprint.interpolation(),
            ARGB.multiply(ARGB.colorFromFloat(getColor().a() * fade, getColor().r(), getColor().g(), getColor().b()), tint),
            getLightCoords(partialTick)
        );
    }

    /// Where the emitter is now, in world space, or null once its socket is gone: the socket's transform, else the
    /// system's, with the emitter's current placement.
    private @Nullable Transform sourceTransform() {
        Transform system = attachment == null ? getEmitter().getSystem().getTransform() : attachment.worldTransform(level);

        return system == null ? null : system.mul(getEmitter().getPlacement());
    }

    private Edge edgeOf(final Transform transform, final float time) {
        return new Edge(transform.loc(), transform.mul(farSide), time);
    }

    @Override
    public ParticleRenderType getGroup() {
        return AkytheraParticleRenderTypes.TRAIL;
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

    /// One sampled edge, in world space.
    ///
    /// @param start the emitter's origin
    /// @param end   the trail's far side
    /// @param time  in ticks since the particle spawned
    private record Edge(Vec3 start, Vec3 end, float time) {
    }
}
