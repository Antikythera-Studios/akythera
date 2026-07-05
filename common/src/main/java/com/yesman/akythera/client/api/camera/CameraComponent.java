package com.yesman.akythera.client.api.camera;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// An abstracted [Camera] object in world
@ClientOnly
public final class CameraComponent implements PatchComponent<EntityPatch<LocalPlayer>> {
    private final EntityPatch<LocalPlayer> localPlayerPatch;

    /// Custom relative location of camera from player.
    ///
    /// If null, the camera location is determined by Minecraft's hardcoded
    /// FPV/TPV location.
    ///
    /// @see Camera#alignWithEntity
    private @Nullable Vec3 relativeLocation;

    private float xRot;
    private float yRot;

    public CameraComponent(final EntityPatch<LocalPlayer> localPlayerPatch) {
        this.localPlayerPatch = localPlayerPatch;
    }

    @Override
    public EntityPatch<LocalPlayer> getOwner() {
        return localPlayerPatch;
    }

    /// Determine whether camera and player entity share the same yaw.
    /// This is a stateful function called in initialization
    ///
    /// true:  Minecraft's classic camera rotation
    /// false: Camera & player rotation are decoupled. Developers will implement custom rotation rule for them.
    public void setAvatarUseCameraYaw() {

    }

    /// Set a custom relative location
    public void setRelativeLocation(final @Nullable Vec3 rel) {

    }

    /// Set the player entity to look at where the camera crosshair hits
    public void setAvatarToLookCameraCrosshair() {

    }

    /// Align the player entity to the camera so they have the same rotations.
    public void setAvatarRotationToCameraRotation() {

    }

    /// Set camera to look at the destination location
    public void look(final Vec3 destination) {

    }

    /// Set camera to look the center of an entity's bounding box
    public void look(final Entity target) {

    }

    public HitResult pickCrosshair() {
        return null;
    }

    /// Called in GameRenderer to set camera transform
    @ApiStatus.Internal
    public void update() {

    }
}
