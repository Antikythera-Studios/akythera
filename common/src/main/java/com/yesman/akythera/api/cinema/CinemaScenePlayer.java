package com.yesman.akythera.api.cinema;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundJoinCinemaScene;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// Plays a [CinemaScene]: takes an entity for each role, then places them around a root and starts their montages
/// together. The actors follow the root while the scene plays. Played on a server, every client that tracks an actor
/// plays that actor's part too.
///
/// ```java
/// CinemaScenePlayer.of(scene)
///     .actor("attacker", playerPatch)
///     .actor("victim", zombiePatch)
///     .playOn(zombie);
/// ```
@com.yesman.akythera.core.reflection.ScriptType
public final class CinemaScenePlayer {
    private final CinemaScene scene;

    /// The patch of each role.
    private final Map<String, EntityPatch<?>> actors = new LinkedHashMap<>();

    private CinemaScenePlayer(final CinemaScene scene) {
        this.scene = scene;
    }

    @ScriptFunction(pure = true, label = "Make Cinema Scene Player", category = "Animation/Cinema Scene", desc = """
        Makes a player of the cinema scene. Add an actor for each role, then play it
    """)
    public static CinemaScenePlayer of(final CinemaScene scene) {
        return new CinemaScenePlayer(scene);
    }

    /// Casts `patch` as `role`, replacing the patch cast before.
    @ScriptFunction(label = "Add Actor", category = "Animation/Cinema Scene", desc = """
        Casts the entity as the role, replacing the entity cast before. Returns the same player
    """)
    public CinemaScenePlayer actor(final String role, final EntityPatch<?> patch) {
        if (role != null && patch != null) actors.put(role, patch);

        return this;
    }

    /// Plays the scene in `root`'s model space: at its position, facing where its body faces. A root that is cast as an
    /// actor gives its place at the start only, since the scene places it.
    @ScriptFunction(label = "Play Cinema Scene", category = "Animation/Cinema Scene", desc = """
        Plays the scene around the root entity, at its feet and facing where its body faces. The actors follow the root
        while the scene plays, unless the root is one of them
    """)
    public void playOn(final Entity root) {
        boolean cast = actors.values().stream().anyMatch(patch -> patch.getOriginal() == root);

        play(cast ? new CinemaSceneRoot.At(root.position(), root instanceof LivingEntity living ? living.yBodyRot : root.getYRot()) : new CinemaSceneRoot.On(root));
    }

    @ScriptFunction(label = "Play Cinema Scene At", category = "Animation/Cinema Scene", desc = """
        Plays the scene with its origin at the position, facing the yaw in degrees
    """)
    public void playAt(final Vec3 root, final float yRot) {
        play(new CinemaSceneRoot.At(root, yRot));
    }

    /// Plays the scene around `root`. A role with no patch cast is left out.
    public void play(final CinemaSceneRoot root) {
        Optional<ResourceKey<CinemaScene>> key = ScriptType.entryKey(AkytheraRegistries.DataPack.CINEMA_SCENE_KEY, scene);

        scene.actors().forEach((role, actor) -> {
            EntityPatch<?> patch = actors.get(role);

            if (patch == null) {
                Akythera.LOGGER.warn("Cinema scene {} plays without the role '{}': no patched entity is cast", key.map(ResourceKey::identifier).orElse(null), role);
                return;
            }

            ArmatureComponent armature = patch.getArmatureComponent();

            if (armature != null && actor.montage().isPresent()) armature.getAnimator().playAnimMontage(actor.montage().get());

            join(patch, scene, actor, root);

            if (patch.isLogicalServer() && key.isPresent()) {
                NetworkingHelper.getInstance().sendToPlayersTrackingEntityWithSelfIfPlayer(
                    patch.getOriginal(), ClientBoundJoinCinemaScene.of(key.get(), role, patch.getOriginal().getId(), root)
                );
            }
        });
    }

    /// Starts `actor`'s part of `scene` on `patch`: stands the entity at its place around `root`, and anchors the root
    /// motion it plays to that place, or holds it there without one.
    public static void join(final EntityPatch<?> patch, final CinemaScene scene, final CinemaScene.Actor actor, final CinemaSceneRoot root) {
        CinemaScenePlayback playback = new CinemaScenePlayback(scene, actor, root);
        Transform placed = playback.placement();
        float yRot = 180.0F - (float)Math.toDegrees(QuaternionUtils.toSwingTwist(placed.rot()).y);
        Entity entity = patch.getOriginal();

        entity.snapTo(placed.loc(), yRot, entity.getXRot());
        entity.setYHeadRot(yRot);

        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yRot;
            living.yBodyRotO = yRot;
            living.yHeadRotO = yRot;
        }

        ArmatureComponent armature = patch.getArmatureComponent();
        RootMotionComponent rootMotion = armature == null ? null : armature.getComponent(RootMotionComponent.class);

        if (rootMotion != null && rootMotion.isPlaying()) {
            rootMotion.setAnchor(playback);
        } else {
            playback.setHoldsActor(true);
        }

        patch.setCinemaScenePlayback(playback);
    }
}
