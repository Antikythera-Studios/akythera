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
/// Times are in ticks since the tick before the trail spawned: a tick is at `age`, a frame at `age + partialTick`.
///
/// Every tick samples the edge at the tick and up to [TrailParticleBlueprint#interpolation] edges evenly in time between
/// it and the last edge, each stamped with its time. The edges between are the fewest that keep two edges at most the
/// trail's length × [TrailParticleBlueprint#interpolationSpacing] apart, on the side of the edge that moved further.
/// They are placed along the emitter's transforms the frames drew since the last sample, so a swing turning more than
/// half a circle in a tick keeps its direction. A frame
/// draws the edges between `now - trailLifetime` and `now`, where `now` is the frame's time. While sampling, the frame
/// closes the trail with one more edge at the emitter as the frame draws it, so the trail reaches its socket. An edge
/// is drawn translucent by its lifetime left, see [TrailParticleBlueprint#fadeOut].
///
/// It samples from [#startTick] to [#endTick], what is left of its [TrailSpawnRule]'s time span. The first frame past
/// the end, or past its system's stop, keeps the edge it draws as the last one. A sample places the emitter by the
/// trail's own time, never by the emitter's last tick, since the particle engine ticks the trail and its system in no
/// set order. A socket is placed as its entity is drawn at the sampled time, see [ParticleAttachment#worldTransform].
@ScriptType
public class AkytheraTrailParticle extends Particle implements AkytheraParticle {
    private final ParticleState state;
    private final TrailParticleBlueprint blueprint;

    /// The socket the system follows, or null when the trail follows the system's own transform.
    private final @Nullable ParticleAttachment attachment;

    /// Seconds since the emitter started at the moment the trail spawned.
    private final float spawnTime;

    /// The time the trail spawned at: how far past a tick its system's tick ran, see
    /// [AkytheraParticleSystem#tickPartialTick].
    private final float startTick;

    /// The time the trail stops sampling at, the end of its [TrailSpawnRule]'s time span.
    private final float endTick;

    /// How far the trail's far side is from the emitter's origin along the emitter's +Y axis, in blocks.
    private float length;

    /// Sampled edges, oldest first.
    private final List<Edge> edges = new ArrayList<>();

    /// The emitter's transforms since the last sample, oldest first, in world space: the last sample, then each frame's.
    private final List<Waypoint> path = new ArrayList<>();

    /// Whether the trail still samples edges: until its lifetime ends, its system stops, or its socket is gone.
    private boolean sampling = true;

    public AkytheraTrailParticle(final AkytheraParticleEmitter emitter, final TrailParticleBlueprint blueprint) {
        super(emitter.getSystem().getLevel(), emitter.getWorldTransform().loc().x, emitter.getWorldTransform().loc().y, emitter.getWorldTransform().loc().z);

        this.state = new ParticleState(emitter, emitter.getWorldTransform().loc(), blueprint.properties(), blueprint.color());
        this.blueprint = blueprint;
        this.attachment = emitter.getSystem().getAttachment();
        this.spawnTime = emitter.getSpawnTime();
        this.startTick = emitter.getSystem().tickPartialTick();
        this.endTick = this.startTick + (emitter.getBlueprint().spawn().duration() - this.spawnTime) * 20.0F;
        this.length = blueprint.length();
        this.hasPhysics = false;
        this.setSize(blueprint.properties().bbWidth(), blueprint.properties().bbHeight());
        this.lifetime = Math.max(1, Mth.ceil(this.endTick));

        Transform source = Objects.requireNonNullElse(sourceTransform(this.startTick, this.startTick), emitter.getWorldTransform());
        this.path.add(new Waypoint(source, this.startTick));
        this.edges.add(edgeOf(source, this.startTick));
    }

    @Override
    public void emitterInit(final AkytheraParticleEmitter emitter) {
        state.runInitializers(this);
        edges.set(0, edgeOf(path.getFirst().transform(), startTick));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        age++;

        if (!state.update(this)) return;

        if (sampling) {
            // Past the end, this tick's sample is the last one, unless a frame already kept its own
            boolean ends = age >= endTick;
            Transform sampled = getEmitter().isFinished() && !ends ? null : sourceTransform(age, 0.0F);

            if (sampled == null) {
                sampling = false;
            } else {
                sampleTo(sampled, age);
                setPos(sampled.loc().x, sampled.loc().y, sampled.loc().z);
                sampling = !ends;
            }
        }

        // The earliest time a later frame draws from
        float cutoff = age - blueprint.trailLifetime();

        while (edges.size() >= 2 && edges.get(1).time() <= cutoff) {
            edges.removeFirst();
        }

        if (!sampling && (edges.size() < 2 || edges.getLast().time() <= cutoff)) remove();
    }

    /// This frame's draw, with the edges camera-relative, or null when nothing of the trail is visible. The first frame
    /// past [#endTick] or its system's stop keeps its newest edge and ends the sampling.
    ///
    /// @param tint     multiplies the trail's color, ARGB
    /// @param textures turns the blueprint's texture into the one drawn
    public TrailRenderer.@Nullable Trail extractTrail(final Vec3 cameraPosition, final float partialTick, final int tint, final UnaryOperator<Identifier> textures) {
        float now = age + partialTick;
        Transform head = sampling && now > edges.getLast().time() ? sourceTransform(now, partialTick) : null;

        if (sampling && (now >= endTick || getEmitter().isFinished())) {
            if (head != null) sampleTo(head, now);

            sampling = false;
        } else if (head != null && now > path.getLast().time()) {
            path.add(new Waypoint(head, now));
        }

        float cutoff = now - blueprint.trailLifetime();
        List<Edge> controls = new ArrayList<>(edges);

        if (sampling && head != null) controls.add(edgeOf(head, now));

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

    /// Every sampled edge camera-relative, with its lifetime left at the frame's time.
    public List<DebugEdge> debugEdges(final Vec3 cameraPosition, final float partialTick) {
        float cutoff = age + partialTick - blueprint.trailLifetime();

        return edges.stream()
            .map(edge -> new DebugEdge(
                edge.start().subtract(cameraPosition),
                edge.end().subtract(cameraPosition),
                Mth.clamp((edge.time() - cutoff) / blueprint.trailLifetime(), 0.0F, 1.0F)
            ))
            .toList();
    }

    /// Samples `sampled`'s edge at `time` and the edges between it and the last one, placed along [#path]. Then
    /// `sampled` starts the path.
    private void sampleTo(final Transform sampled, final float time) {
        Edge last = edges.getLast();
        path.add(new Waypoint(sampled, time));

        double travel = 0.0D;

        for (int i = 1; i < path.size(); i++) {
            Edge from = edgeOf(path.get(i - 1).transform(), 0.0F);
            Edge to = edgeOf(path.get(i).transform(), 0.0F);
            travel += Math.max(from.start().distanceTo(to.start()), from.end().distanceTo(to.end()));
        }

        float spacing = length * blueprint.interpolationSpacing();
        int steps = spacing > 0.0F
            ? Mth.clamp(Mth.ceil(travel / spacing), 1, blueprint.interpolation() + 1)
            : blueprint.interpolation() + 1;
        int segment = 1;

        for (int i = 1; i <= steps; i++) {
            float edgeTime = Mth.lerp((float)i / steps, last.time(), time);

            while (segment < path.size() - 1 && path.get(segment).time() < edgeTime) {
                segment++;
            }

            Waypoint from = path.get(segment - 1);
            Waypoint to = path.get(segment);
            float delta = to.time() > from.time() ? Mth.clamp((edgeTime - from.time()) / (to.time() - from.time()), 0.0F, 1.0F) : 1.0F;

            edges.add(edgeOf(Transform.slerp(from.transform(), to.transform(), delta), edgeTime));
        }

        path.clear();
        path.add(new Waypoint(sampled, time));
    }

    /// Where the emitter is at the trail's `time`, in world space, or null once its socket is gone: the socket's
    /// transform with its entity drawn `partialTick` after its last tick, else the system's, with the emitter's
    /// placement at `time`.
    private @Nullable Transform sourceTransform(final float time, final float partialTick) {
        Transform system = attachment == null ? getEmitter().getSystem().getTransform() : attachment.worldTransform(level, partialTick);
        ParticleEmitterBlueprint emitter = getEmitter().getBlueprint();

        return system == null ? null : system.mul(emitter.placementAt(emitter.spawn().spanTime(spawnTime + (time - startTick) / 20.0F)));
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

    /// The emitter's transform at `time`, in world space.
    private record Waypoint(Transform transform, float time) {
    }

    /// One sampled edge as [#debugEdges] answers it.
    ///
    /// @param start    the emitter's origin, camera-relative
    /// @param end      the trail's far side, camera-relative
    /// @param lifeLeft the share of [TrailParticleBlueprint#trailLifetime] left, 0 to 1
    public record DebugEdge(Vec3 start, Vec3 end, float lifeLeft) {
    }
}
