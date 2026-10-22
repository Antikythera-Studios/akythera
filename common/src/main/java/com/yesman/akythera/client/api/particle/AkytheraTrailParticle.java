package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailSpawnRule;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptFunction;
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
import java.util.Objects;
import java.util.function.UnaryOperator;

/// A ribbon a [TrailParticleBlueprint] describes, swept by the emitter it follows.
///
/// Every tick samples the edge at the tick and up to [TrailParticleBlueprint#interpolation] edges evenly between it and
/// the last tick's, each stamped with its time in ticks. The edges between are the fewest that keep two edges at most
/// the trail's length × [TrailParticleBlueprint#interpolationSpacing] apart, on the side of the edge that moved further.
/// A frame draws the edges between `now - trailLifetime` and `now`, where `now` is the frame's time,
/// `age + partialTick`. While sampling, the frame closes the trail with one more edge at the emitter as the frame draws
/// it, so the trail reaches its socket. An edge is drawn translucent by its lifetime left, see
/// [TrailParticleBlueprint#fadeOut].
///
/// Its lifetime is what is left of its [TrailSpawnRule]'s time span when it spawns, rounded up to whole ticks, and it
/// samples until the lifetime ends. A sample places the emitter by its own age, never by the emitter's last tick, since
/// the particle engine ticks the trail and its system in no set order. A socket is placed as its entity is drawn at the
/// sampled time, see [ParticleAttachment#worldTransform].
@ScriptType
public class AkytheraTrailParticle extends Particle implements AkytheraParticle {
    private final ParticleState state;
    private final TrailParticleBlueprint blueprint;

    /// The socket the system follows, or null when the trail follows the system's own transform.
    private final @Nullable ParticleAttachment attachment;

    /// Seconds since the emitter started at the moment the trail spawned.
    private final float spawnTime;

    /// How far the trail's far side is from the emitter's origin along the emitter's +Y axis, in blocks.
    private float length;

    /// Sampled edges, oldest first.
    private final List<Edge> edges = new ArrayList<>();

    /// The emitter's transform at the last tick and the one before, in world space.
    private Transform source;
    private Transform previousSource;

    /// Whether the trail still samples edges: until its lifetime ends, its system stops, or its socket is gone.
    private boolean sampling = true;

    public AkytheraTrailParticle(final AkytheraParticleEmitter emitter, final TrailParticleBlueprint blueprint) {
        super(emitter.getSystem().getLevel(), emitter.getWorldTransform().loc().x, emitter.getWorldTransform().loc().y, emitter.getWorldTransform().loc().z);

        this.state = new ParticleState(emitter, emitter.getWorldTransform().loc(), blueprint.properties(), blueprint.color());
        this.blueprint = blueprint;
        this.attachment = emitter.getSystem().getAttachment();
        this.spawnTime = emitter.getSpawnTime();
        this.length = blueprint.length();
        this.source = Objects.requireNonNullElse(sourceTransform(0.0F, 0.0F), emitter.getWorldTransform());
        this.previousSource = this.source;
        this.hasPhysics = false;
        this.setSize(blueprint.properties().bbWidth(), blueprint.properties().bbHeight());
        this.lifetime = Math.max(1, Mth.ceil((emitter.getBlueprint().spawn().duration() - this.spawnTime) * 20.0F));
        this.edges.add(edgeOf(this.source, 0.0F));
    }

    @Override
    public void emitterInit(final AkytheraParticleEmitter emitter) {
        state.runInitializers(this);
        edges.set(0, edgeOf(source, 0.0F));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        age++;

        if (!state.update(this)) return;

        if (sampling) {
            // The emitter finishes on the tick the time span ends, so a finished emitter stops the trail early only before then
            boolean stopped = age > lifetime || getEmitter().isFinished() && age < lifetime;
            Transform sampled = stopped ? null : sourceTransform(age, 0.0F);

            if (sampled == null) {
                sampling = false;
            } else {
                previousSource = source;
                source = sampled;
                Edge last = edges.getLast();
                Edge next = edgeOf(source, age);
                double travel = Math.max(last.start().distanceTo(next.start()), last.end().distanceTo(next.end()));
                float spacing = length * blueprint.interpolationSpacing();
                int steps = spacing > 0.0F
                    ? Mth.clamp(Mth.ceil(travel / spacing), 1, blueprint.interpolation() + 1)
                    : blueprint.interpolation() + 1;

                for (int i = 1; i <= steps; i++) {
                    float delta = (float)i / steps;
                    edges.add(edgeOf(Transform.slerp(previousSource, source, delta), age - 1 + delta));
                }

                setPos(source.loc().x, source.loc().y, source.loc().z);
            }
        }

        // The earliest time a later frame draws from
        float cutoff = age - blueprint.trailLifetime();

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
        float now = age + partialTick;
        float cutoff = now - blueprint.trailLifetime();
        List<Edge> controls = new ArrayList<>(edges);
        Transform head = sampling && partialTick > 0.0F && age < lifetime ? sourceTransform(now, partialTick) : null;

        if (head != null) controls.add(edgeOf(head, now));

        int first = 0;

        while (first + 1 < controls.size() && controls.get(first + 1).time() <= cutoff) {
            first++;
        }

        int count = controls.size() - first;

        if (count < 2) return null;

        // The oldest edge cut back to the cutoff, where its lifetime ends
        Edge oldest = controls.get(first);

        if (oldest.time() < cutoff) {
            Edge next = controls.get(first + 1);
            float delta = (cutoff - oldest.time()) / (next.time() - oldest.time());

            controls.set(first, new Edge(oldest.start().lerp(next.start(), delta), oldest.end().lerp(next.end(), delta), cutoff));
        }

        float tailTime = controls.get(first).time();
        float span = controls.getLast().time() - tailTime;
        int fadeOut = blueprint.fadeOut();
        float[] floats = new float[count * TrailRenderer.CONTROL_FLOATS];

        for (int i = 0; i < count; i++) {
            Edge edge = controls.get(first + i);
            int base = i * TrailRenderer.CONTROL_FLOATS;

            floats[base] = (float)(edge.start().x - cameraPosition.x);
            floats[base + 1] = (float)(edge.start().y - cameraPosition.y);
            floats[base + 2] = (float)(edge.start().z - cameraPosition.z);
            floats[base + 3] = (float)(edge.end().x - cameraPosition.x);
            floats[base + 4] = (float)(edge.end().y - cameraPosition.y);
            floats[base + 5] = (float)(edge.end().z - cameraPosition.z);
            floats[base + 6] = (edge.time() - tailTime) / span;
            floats[base + 7] = fadeOut <= 0 ? 1.0F : Mth.clamp((edge.time() - cutoff) / fadeOut, 0.0F, 1.0F);
        }

        return new TrailRenderer.Trail(
            TrailRenderer.renderType(textures.apply(blueprint.texture())),
            floats,
            count,
            ARGB.multiply(getColor().packARGB(), tint),
            getLightCoords(partialTick)
        );
    }

    /// Where the emitter is `ticks` after the trail spawned, in world space, or null once its socket is gone: the
    /// socket's transform with its entity drawn `partialTick` after its last tick, else the system's, with the
    /// emitter's placement at `ticks`.
    private @Nullable Transform sourceTransform(final float ticks, final float partialTick) {
        Transform system = attachment == null ? getEmitter().getSystem().getTransform() : attachment.worldTransform(level, partialTick);
        ParticleEmitterBlueprint emitter = getEmitter().getBlueprint();

        return system == null ? null : system.mul(emitter.placementAt(emitter.spawn().spanTime(spawnTime + ticks / 20.0F)));
    }

    private Edge edgeOf(final Transform transform, final float time) {
        return new Edge(transform.loc(), transform.mul(new Vec3(0.0D, length, 0.0D)), time);
    }

    /// In blocks.
    @ScriptFunction(pure = true, category = "Particle/Trail", desc = "The trail's length, in blocks")
    public float getTrailLength() {
        return length;
    }

    /// Edges sampled before keep their length.
    @ScriptFunction(category = "Particle/Trail", desc = "Sets the trail's length, in blocks. Edges sampled before keep theirs")
    public void setTrailLength(final float length) {
        this.length = length;
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
