package com.yesman.akythera.client.api.camera;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.SpringArmComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Quaterniond;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// The view of the local player in third person: a rotation of its own, apart from the player's, and a location
/// set by [#localTransform].
///
/// Under a [SpringArmComponent], [#localTransform] is in the space of the arm's end, and the arm turns about its pivot
/// with the pivot's rotation, then with the view's pitch, see [SpringArmComponent#pivotWorldTransform]. While the arm
/// [SpringArmComponent#ridesArmature], the camera is the arm's end times [#localTransform], with no view pitch. Anywhere else it is in the patch's model space (origin at the feet, -Z forward, +X right) turned
/// by the view rotation around the player's eyes. In third person the camera always takes this place over vanilla's,
/// and at level pitch it sits where the editor shows it. With [#collideWithTerrain] the arm shrinks where blocks are in
/// the way. The rotation turns the camera on top of the arm's rotation; its roll is dropped. The scale is not read.
///
/// In first person the view is the player's own, and the component only follows the player's rotation. While the
/// player's turn is locked the camera looks around on its own, and the player turns to it once the lock ends.
@ClientOnly @ScriptType
public final class CameraComponent implements UniqueComponent, TransformComponent, TickableComponent, DynamicPatchComponent<ComponentAttachable> {
    /// Share of the remaining turn toward a lock target taken each tick.
    private static final double LOCK_SLERP_RATE = 0.4D;

    /// How far the crosshair ray reaches past the interaction ranges for aiming, in blocks.
    private static final double AIM_DISTANCE = 128.0D;

    private ComponentAttachable owner;

    private boolean enabled = true;

    private Transform localTransform;

    /// Whether the player turns with the camera. Off, the player takes the camera's rotation only while moving.
    private boolean matchPlayerYawToCamera;

    /// Whether the camera's arm shrinks where blocks are in the way.
    private boolean collideWithTerrain;

    /// How far the camera keeps off blocks, in blocks.
    private double probeRadius;

    /// The camera's rotation, in degrees, with the values of the last tick for interpolation.
    private float xRot;
    private float yRot;
    private float xRotO;
    private float yRotO;

    /// Whether the camera held the view in the last tick.
    private boolean heldView;

    /// The location the camera is locked to look at, in world space. Null while unlocked or locked to an entity.
    private @Nullable Vec3 lockLocation;

    /// The entity the camera is locked to look at. Null while unlocked or locked to a location.
    private @Nullable Entity lockEntity;

    /// Added to the lock target to give the location the camera looks at, in world space.
    private Vec3 lockOffset = Vec3.ZERO;

    public CameraComponent(
        final ComponentAttachable owner,
        final Transform localTransform,
        final boolean matchPlayerYawToCamera,
        final boolean collideWithTerrain,
        final double probeRadius
    ) {
        this.owner = owner;
        this.localTransform = localTransform;
        this.matchPlayerYawToCamera = matchPlayerYawToCamera;
        this.collideWithTerrain = collideWithTerrain;
        this.probeRadius = probeRadius;
    }

    /// The camera component anywhere under the local player's patch while the player is the camera entity, or null.
    public static @Nullable CameraComponent ofLocalPlayer() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) return null;

        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(minecraft.player);

        return patch == null ? null : patch.findComponentBelow(CameraComponent.class, _ -> true);
    }

    /// The patch this camera views from.
    public EntityPatch<?> getPatch() {
        return Objects.requireNonNull(TransformComponent.entityOf(owner), "A camera component is attached under no entity patch");
    }

    /// [#ofLocalPlayer] while it holds the view.
    public static @Nullable CameraComponent holdingView() {
        CameraComponent camera = ofLocalPlayer();

        return camera != null && camera.holdsView() ? camera : null;
    }

    /// Whether the camera holds the view, its rotation apart from the player's: in third person, or while the player's
    /// turn is locked. The location is its own in third person only.
    public boolean holdsView() {
        return !Minecraft.getInstance().options.getCameraType().isFirstPerson() || isPlayerRotationBlocked();
    }

    public boolean isMatchPlayerYawToCamera() {
        return matchPlayerYawToCamera;
    }

    public void setMatchPlayerYawToCamera(final boolean matchPlayerYawToCamera) {
        this.matchPlayerYawToCamera = matchPlayerYawToCamera;
    }

    public boolean isCollideWithTerrain() {
        return collideWithTerrain;
    }

    public void setCollideWithTerrain(final boolean collideWithTerrain) {
        this.collideWithTerrain = collideWithTerrain;
    }

    public double getProbeRadius() {
        return probeRadius;
    }

    public void setProbeRadius(final double probeRadius) {
        this.probeRadius = probeRadius;
    }

    /// Whether the player's rotation can't follow the mouse, see [LivingEntityPatch#isTurnLocked].
    public boolean isPlayerRotationBlocked() {
        return getPatch() instanceof LivingEntityPatch<?> living && living.isTurnLocked();
    }

    public float getXRot() {
        return xRot;
    }

    public float getYRot() {
        return yRot;
    }

    public float getViewXRot(final float partialTick) {
        return Mth.lerp(partialTick, xRotO, xRot);
    }

    public float getViewYRot(final float partialTick) {
        return Mth.rotLerp(partialTick, yRotO, yRot);
    }

    /// Sets the camera's rotation, in degrees.
    ///
    /// @param syncOld whether to drop the interpolation from the last tick
    public void setRotation(final float xRot, final float yRot, final boolean syncOld) {
        this.xRot = Mth.clamp(xRot, -90.0F, 90.0F);
        this.yRot = yRot;

        if (syncOld) {
            xRotO = this.xRot;
            yRotO = this.yRot;
        }
    }

    // ***************************************************************
    // Lock-on
    // ***************************************************************

    @ScriptFunction(label = "Lock Camera To Look", desc = """
        Turns the camera smoothly toward the location plus the offset, in world space, and keeps it there until Unlock Camera. Client side only
    """)
    public void lockCameraToLook(final Vec3 location, final @Nullable Vec3 offset) {
        lockLocation = location;
        lockEntity = null;
        lockOffset = offset == null ? Vec3.ZERO : offset;
    }

    @ScriptFunction(label = "Lock Camera To Look Entity", desc = """
        Turns the camera smoothly toward the entity plus the offset, in world space, and follows it until Unlock Camera, or until the entity is removed. Client side only
    """)
    public void lockCameraToLookEntity(final Entity target, final @Nullable Vec3 offset) {
        lockLocation = null;
        lockEntity = target;
        lockOffset = offset == null ? Vec3.ZERO : offset;
    }

    @ScriptFunction(label = "Unlock Camera", desc = "Gives the camera back to the mouse. Client side only")
    public void unlockCamera() {
        lockLocation = null;
        lockEntity = null;
    }

    @ScriptFunction(pure = true, label = "Is Camera Locked", desc = "Whether the camera is locked to look at a location or an entity")
    public boolean isCameraLocked() {
        return lockLocation != null || lockEntity != null;
    }

    /// The rotation, in degrees, that looks from the view at the lock target. Null while unlocked.
    ///
    /// In third person the camera sits off the player's look axis, and turning the camera moves it around
    /// the player. Aiming from the camera at a near target then keeps moving the target off center, turning the camera
    /// forever. The aim ray starts nearer the eyes, and ends nearer the target's center, the closer the target is.
    private float @Nullable [] lockRotation(final float rotX, final float rotY) {
        Entity avatar = getPatch().getOriginal();
        Vec3 eye = avatar.getEyePosition();
        boolean offAxis = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
        Vec3 end;
        double distanceSqr;

        if (lockEntity != null) {
            distanceSqr = avatar.position().distanceToSqr(lockEntity.position());
            end = offAxis
                ? lockEntity.getEyePosition().lerp(lockEntity.getBoundingBox().getCenter(), Mth.clampedMap(distanceSqr, 0.0D, 18.0D, 0.5D, 1.0D))
                : lockEntity.getEyePosition();
        } else if (lockLocation != null) {
            distanceSqr = avatar.position().distanceToSqr(lockLocation);
            end = lockLocation;
        } else {
            return null;
        }

        end = end.add(lockOffset);

        Vec3 start = offAxis
            ? eye.lerp(Minecraft.getInstance().gameRenderer.mainCamera().position(), Mth.clampedMap(distanceSqr, 1.0D, 18.0D, 0.2D, 1.0D))
            : eye;
        Vec3 toTarget = end.subtract(start);

        if (toTarget.lengthSqr() < 1.0E-6D) return null;

        Vec3 from = MathUtils.getDirectionVector(rotX, rotY);
        Quaterniond turn = new Quaterniond().rotationTo(new Vector3d(from.x, from.y, from.z), new Vector3d(toTarget.x, toTarget.y, toTarget.z).normalize());
        Vector3d slerped = new Quaterniond().slerp(turn, LOCK_SLERP_RATE).transform(new Vector3d(from.x, from.y, from.z));
        Vec3 direction = new Vec3(slerped.x, slerped.y, slerped.z);

        return new float[] {
            (float)VectorUtils.getVectorXRot(direction),
            rotY + Mth.wrapDegrees((float)VectorUtils.getVectorYRot(direction) - rotY)
        };
    }

    // ***************************************************************
    // Player alignment
    // ***************************************************************

    /// Turns the player to the camera's rotation.
    public void setAvatarRotationToCameraRotation() {
        Entity avatar = getPatch().getOriginal();

        avatar.setXRot(xRot);
        avatar.setYRot(yRot);
        avatar.setYHeadRot(yRot);
    }

    /// Turns the player to look at what the crosshair points at, [Minecraft#hitResult].
    public void setAvatarToLookCameraCrosshair() {
        HitResult crosshair = Minecraft.getInstance().hitResult;

        if (crosshair == null) return;

        Entity avatar = getPatch().getOriginal();
        Vec3 toTarget = crosshair.getLocation().subtract(avatar.getEyePosition());

        if (toTarget.lengthSqr() < 1.0E-6D) return;

        float lookYRot = (float)VectorUtils.getVectorYRot(toTarget);

        avatar.setXRot((float)VectorUtils.getVectorXRot(toTarget));
        avatar.setYRot(avatar.getYRot() + Mth.wrapDegrees(lookYRot - avatar.getYRot()));
        avatar.setYHeadRot(avatar.getYRot());
    }

    /// Turns the player to aim at the crosshair before it attacks, digs or uses an item.
    @ApiStatus.Internal
    public void correctAim() {
        if (!isPlayerRotationBlocked()) setAvatarToLookCameraCrosshair();
    }

    /// Turns the mouse movement into the camera's rotation, returning whether the player's own turn is taken over.
    @ApiStatus.Internal
    public boolean turn(final double xo, final double yo) {
        if (!holdsView()) return isCameraLocked();
        if (isCameraLocked()) return true;

        float xDelta = (float)yo * 0.15F;
        float yDelta = (float)xo * 0.15F;

        xRot = Mth.clamp(xRot + xDelta, -90.0F, 90.0F);
        yRot += yDelta;
        xRotO = Mth.clamp(xRotO + xDelta, -90.0F, 90.0F);
        yRotO += yDelta;

        if (matchPlayerYawToCamera && !isPlayerRotationBlocked()) getPatch().getOriginal().turn(xo, yo);

        return true;
    }

    /// Turns the player along with the camera once the movement input of the tick is read.
    @ApiStatus.Internal
    public void alignAvatar(final LocalPlayer player) {
        if (!holdsView() || isPlayerRotationBlocked()) return;

        if (player.isUsingItem()) {
            setAvatarToLookCameraCrosshair();
            return;
        }

        boolean moving = player.input.getMoveVector().lengthSquared() > 0.0F || player.input.keyPresses.jump();

        if (matchPlayerYawToCamera || moving) setAvatarRotationToCameraRotation();
    }

    // ***************************************************************
    // Camera transform
    // ***************************************************************

    /// The camera's place in world space `partialTick` after the last tick for `rotation`, the view rotation: its
    /// location and the rotation it looks with (along -Z). Under a [SpringArmComponent] the arm runs from the arm's
    /// pivot, and from `eyes`, the player's eyes `eyeHeight` above its feet, anywhere else.
    @ApiStatus.Internal
    public Transform place(final Level level, final Vec3 eyes, final float eyeHeight, final float partialTick, final Quaternionfc rotation) {
        Vec3 loc = localTransform.loc();
        SpringArmComponent arm = owner instanceof SpringArmComponent springArm ? springArm : null;
        Transform armPivot = arm == null ? null : arm.pivotWorldTransform(partialTick, getViewYRot(partialTick));
        Vec3 pivot = armPivot == null ? eyes : armPivot.loc();
        // The model space and the camera's own space share their axes: +X right, -Z forward
        Quaterniond armRotation = armPivot == null ? new Quaterniond(rotation) : new Quaterniond(armPivot.rot());

        if (armPivot != null && !arm.ridesArmature()) {
            armRotation.mul(new Quaterniond().rotationAxis(-getViewXRot(partialTick) * Mth.DEG_TO_RAD, 1.0D, 0.0D, 0.0D));
        }

        Vector3d offset = armRotation.transform(armPivot == null
            ? new Vector3d(loc.x, loc.y - eyeHeight, loc.z)
            : new Vector3d(loc.x, loc.y, loc.z + arm.getLength()));
        Vec3 end = pivot.add(offset.x, offset.y, offset.z);
        double reach = 1.0D;

        // Eight rays to a little around the end, as vanilla's camera zoom casts them. They all start at the pivot, which
        // may lie on a block's face, where a ray starting a little off it would start inside the block
        for (int i = 0; collideWithTerrain && i < 8; i++) {
            Vec3 jitter = new Vec3(((i & 1) * 2 - 1) * probeRadius, ((i >> 1 & 1) * 2 - 1) * probeRadius, ((i >> 2 & 1) * 2 - 1) * probeRadius);
            Vec3 to = end.add(jitter);
            HitResult hit = level.clip(new ClipContext(pivot, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, getPatch().getOriginal()));

            if (hit.getType() != HitResult.Type.MISS) reach = Math.min(reach, hit.getLocation().distanceTo(pivot) / pivot.distanceTo(to));
        }

        return new Transform(pivot.add(offset.x * reach, offset.y * reach, offset.z * reach), armRotation.mul(localTransform.rot()), Transform.IDENTITY.sca());
    }

    /// Picks what the crosshair points at from the camera, within the player's interaction ranges counted from its
    /// eyes. A miss lies where the ray hits a block, or [#AIM_DISTANCE] blocks ahead, so the player can aim at it.
    public HitResult pickCrosshair(final float partialTick) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        LocalPlayer player = (LocalPlayer)getPatch().getOriginal();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 direction = new Vec3(camera.forwardVector());
        // Starts abreast of the eyes, so nothing between the camera and the player is picked
        Vec3 from = camera.position().add(direction.scale(Math.max(0.0D, eye.subtract(camera.position()).dot(direction))));
        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        double reach = Math.max(blockRange, entityRange) + from.distanceTo(eye);
        BlockHitResult blockHit = player.level().clip(
            new ClipContext(from, from.add(direction.scale(reach + AIM_DISTANCE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
        );
        double entityReach = Math.min(reach, blockHit.getLocation().distanceTo(from));
        Vec3 entityTo = from.add(direction.scale(entityReach));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player, from, entityTo, new AABB(from, entityTo).inflate(1.0D), EntitySelector.CAN_BE_PICKED, entityReach * entityReach
        );

        if (entityHit != null && entityHit.getLocation().closerThan(eye, entityRange)) return entityHit;
        if (blockHit.getType() != HitResult.Type.MISS && blockHit.getLocation().closerThan(eye, blockRange)) return blockHit;

        Vec3 location = blockHit.getLocation();

        return BlockHitResult.miss(location, Direction.getApproximateNearest(direction.x, direction.y, direction.z), BlockPos.containing(location));
    }

    // ***************************************************************
    // TickableComponent
    // ***************************************************************

    @Override
    public void tick() {
        if (lockEntity != null && lockEntity.isRemoved()) unlockCamera();
        if (ofLocalPlayer() != this) return;

        Entity avatar = getPatch().getOriginal();

        if (!holdsView()) {
            // Back in first person, or out of a turn lock, the player looks where the camera did
            if (heldView) setAvatarRotationToCameraRotation();

            // The view is the player's own, so a lock turns the player
            float[] lock = lockRotation(avatar.getXRot(), avatar.getYRot());

            if (lock != null) {
                avatar.setXRot(lock[0]);
                avatar.setYRot(lock[1]);
                avatar.setYHeadRot(lock[1]);
            }

            heldView = false;
            setRotation(avatar.getXRot(), avatar.getYRot(), true);

            return;
        }

        if (!heldView) setRotation(avatar.getXRot(), avatar.getYRot(), true);

        heldView = true;
        xRotO = xRot;
        yRotO = yRot;

        float[] lock = lockRotation(xRot, yRot);

        if (lock != null) setRotation(lock[0], lock[1], false);
    }

    // ***************************************************************
    // TransformComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public ComponentAttachable host() {
        return owner;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return TransformInheritPolicy.LOC_ROT_SCA;
    }

    // ***************************************************************
    // DynamicPatchComponent
    // ***************************************************************

    @Override
    public ComponentAttachable getOwner() {
        return owner;
    }

    @Override
    public CameraComponent getRenewed(final ComponentAttachable newOwner) {
        owner = newOwner;
        return this;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
