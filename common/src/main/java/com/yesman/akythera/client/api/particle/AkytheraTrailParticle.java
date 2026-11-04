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
import java.util.function.UnaryOperator;

/// A ribbon a [TrailParticleBlueprint] describes, swept by the emitter it follows.
///
/// Times are in ticks since the tick before the trail spawned: a tick is at `age`, a frame at `age + partialTick`.
///
/// Every frame samples edges evenly in time from the last edge to the frame: the fewest that keep two edges at most the
/// trail's length × [TrailParticleBlueprint#edgeSpacing] apart, and at most [TrailParticleBlueprint#maxEdgesPerFrame].
/// They are placed along the system's transform at the frame, and with an [AkytheraParticleSystem#getFrameSampler],
/// along the transforms it samples at up to [TrailParticleBlueprint#maxEdgesPerFrame] times between, so a motion faster
/// than the frames keeps its shape. A frame draws the edges between `now - trailLifetime` and `now`, where `now` is the
/// frame's time. An edge is drawn translucent by its lifetime left, see [TrailParticleBlueprint#fadeOut].
///
/// It samples from [#startTick] to [#endTick], its [TrailSpawnRule]'s time span, or until its system stops. A socket is
/// placed as its entity is drawn in the frame, see [ParticleAttachment#worldTransform].
@ScriptType
public class AkytheraTrailParticle extends Particle implements AkytheraParticle {
    private final ParticleState state;
    private final TrailParticleBlueprint blueprint;

    /// The socket the system follows, or null when the trail follows the system's own transform.
    private final @Nullable ParticleAttachment attachment;

    /// The system's [AkytheraParticleSystem#getAge] when the trail spawned, which turns the system's time into the
    /// trail's.
    private final int systemSpawnAge;

    /// The time the emitter's time span started at. Negative for a trail its emitter spawned behind time, see
    /// [AkytheraParticleSystem#start].
    private final float startTick;

    /// The time the trail stops sampling at, the end of its [TrailSpawnRule]'s time span.
    private final float endTick;

    /// How far the trail's far side is from the emitter's origin along the emitter's +Y axis, in blocks.
    private float length;

    /// Sampled edges, oldest first.
    private final List<Edge> edges = new ArrayList<>();

    /// The system's transforms from the last edge on, oldest first, in world space.
    private final List<AkytheraParticleSystem.Waypoint> path = new ArrayList<>();

    /// Whether the trail still samples edges: until its time span ends, its system stops, or its socket is gone.
    private boolean sampling = true;

    public AkytheraTrailParticle(final AkytheraParticleEmitter emitter, final TrailParticleBlueprint blueprint) {
        super(emitter.getSystem().getLevel(), emitter.getWorldTransform().loc().x, emitter.getWorldTransform().loc().y, emitter.getWorldTransform().loc().z);

        AkytheraParticleSystem system = emitter.getSystem();

        this.state = new ParticleState(emitter, emitter.getWorldTransform().loc(), blueprint.properties(), blueprint.color());
        this.blueprint = blueprint;
        this.attachment = system.getAttachment();
        this.systemSpawnAge = system.getAge();
        this.startTick = system.tickPartialTick() - emitter.getSpawnTime() * 20.0F;
        this.endTick = this.startTick + emitter.getBlueprint().spawn().duration() * 20.0F;
        this.length = blueprint.length();
        this.hasPhysics = false;
        this.setSize(blueprint.properties().bbWidth(), blueprint.properties().bbHeight());
        this.lifetime = Math.max(1, Mth.ceil(this.endTick));
        this.path.add(new AkytheraParticleSystem.Waypoint(system.getTransform(), this.startTick));
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

        // Frames sample the trail, and the first one past the end closes it. A whole tick past the end, none drew it
        if (sampling && age - 1 >= endTick) sampling = false;

        // The earliest time a later frame draws from
        float cutoff = age - blueprint.trailLifetime();

        while (edges.size() >= 2 && edges.get(1).time() <= cutoff) {
            edges.removeFirst();
        }

        if (!sampling && (edges.size() < 2 || edges.getLast().time() <= cutoff)) remove();
    }

    /// Samples this frame's edges, then returns the draw with the edges camera-relative, or null when nothing of the
    /// trail is visible. The first frame past [#endTick] or its system's stop ends the sampling.
    ///
    /// @param tint     multiplies the trail's color, ARGB
    /// @param textures turns the blueprint's texture into the one drawn
    public TrailRenderer.@Nullable Trail extractTrail(final Vec3 cameraPosition, final float partialTick, final int tint, final UnaryOperator<Identifier> textures) {
        float now = age + partialTick;

        if (sampling) {
            AkytheraParticleSystem system = getEmitter().getSystem();
            Transform head = attachment == null ? system.getTransform() : attachment.worldTransform(level, partialTick);
            float end = Math.min(now, endTick);

            if (head == null) {
                sampling = false;
            } else if (end > path.getLast().time()) {
                AkytheraParticleSystem.FrameSampler sampler = system.getFrameSampler();
                int maxEdges = blueprint.maxEdgesPerFrame();

                if (sampler != null) {
                    Transform start = edges.isEmpty() ? sampler.sample(startTick + systemSpawnAge) : null;

                    if (start != null) path.set(0, new AkytheraParticleSystem.Waypoint(start, startTick));

                    float from = path.getFirst().time();

                    // Samples place the entity as this frame draws it. Shifted by where the last edge sat, fading to
                    // none at the frame, they join the last edge and the frame's socket with no jump
                    Transform sampledFrom = sampler.sample(from + systemSpawnAge);
                    Vec3 shift = sampledFrom == null ? Vec3.ZERO : path.getFirst().transform().loc().subtract(sampledFrom.loc());

                    for (int i = 1; i <= maxEdges; i++) {
                        float time = Mth.lerp((float)i / maxEdges, from, end);
                        Transform sampled = time < now ? sampler.sample(time + systemSpawnAge) : null;

                        if (sampled != null) {
                            path.add(new AkytheraParticleSystem.Waypoint(sampled.translate(shift.scale((now - time) / (now - from))), time));
                        }
                    }
                }

                path.add(new AkytheraParticleSystem.Waypoint(head, now));

                if (edges.isEmpty()) edges.add(edgeOf(path.getFirst()));

                double travel = 0.0D;

                for (int i = 1; i < path.size(); i++) {
                    Edge from = edgeOf(path.get(i - 1));
                    Edge to = edgeOf(path.get(i));
                    travel += Math.max(from.start().distanceTo(to.start()), from.end().distanceTo(to.end()));
                }

                float spacing = length * blueprint.edgeSpacing();
                int steps = spacing > 0.0F ? Mth.clamp(Mth.ceil(travel / spacing), 1, maxEdges) : maxEdges;
                float last = path.getFirst().time();
                int segment = 1;
                Transform placed = path.getFirst().transform();

                for (int i = 1; i <= steps; i++) {
                    float edgeTime = Mth.lerp((float)i / steps, last, end);

                    while (segment < path.size() - 1 && path.get(segment).time() < edgeTime) {
                        segment++;
                    }

                    AkytheraParticleSystem.Waypoint from = path.get(segment - 1);
                    AkytheraParticleSystem.Waypoint to = path.get(segment);
                    float delta = to.time() > from.time() ? Mth.clamp((edgeTime - from.time()) / (to.time() - from.time()), 0.0F, 1.0F) : 1.0F;

                    placed = Transform.slerp(from.transform(), to.transform(), delta);
                    edges.add(edgeOf(new AkytheraParticleSystem.Waypoint(placed, edgeTime)));
                }

                path.clear();
                path.add(new AkytheraParticleSystem.Waypoint(placed, end));

                Vec3 origin = edges.getLast().start();
                setPos(origin.x, origin.y, origin.z);
            }

            if (now >= endTick || getEmitter().isFinished()) sampling = false;
        }

        float cutoff = now - blueprint.trailLifetime();
        int first = 0;

        while (first + 1 < edges.size() && edges.get(first + 1).time() <= cutoff) {
            first++;
        }

        int count = edges.size() - first;

        if (count < 2) return null;

        List<Edge> controls = new ArrayList<>(edges);

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
            floats[base + 6] = span > 0.0F ? (edge.time() - tailTime) / span : 0.0F;
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

    /// The edge of the system at `waypoint`, with the emitter placed as at the waypoint's time.
    private Edge edgeOf(final AkytheraParticleSystem.Waypoint waypoint) {
        ParticleEmitterBlueprint emitter = getEmitter().getBlueprint();
        Transform source = waypoint.transform().mul(emitter.placementAt(emitter.spawn().spanTime((waypoint.time() - startTick) / 20.0F)));

        return new Edge(source.loc(), source.mul(new Vec3(0.0D, length, 0.0D)), waypoint.time());
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
