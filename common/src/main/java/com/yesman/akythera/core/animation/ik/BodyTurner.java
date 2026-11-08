package com.yesman.akythera.core.animation.ik;

import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;

/// The shown body of one armature under [ArmaturePhysicsData.BodyTurn]: how far it stays behind the body yaw while it
/// eases after a turn, with a critically damped spring that turns no faster than a speed limit.
final class BodyTurner {
    /// Farthest the shown body stays behind the yaw, in degrees.
    private static final float MAX_LAG = 180.0F;

    /// How far the shown body is behind the body yaw, in degrees. A right turn raises the yaw.
    private float lag;

    /// How fast [#lag] changes, in degrees per second.
    private float lagRate;

    /// Eases the shown body for `frameTime` seconds after the yaw turned by `frameTurn`, no faster than `maxTurnSpeed`.
    void update(final ArmaturePhysicsData.BodyTurn settings, final float maxTurnSpeed, final float frameTurn, final float frameTime) {
        lag = Math.clamp(lag + frameTurn, -MAX_LAG, MAX_LAG);

        if (frameTime <= 0.0F) return;

        float omega = 2.0F / settings.turnTime();
        float x = omega * frameTime;
        float decay = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x); // close to e^-x
        float maxChange = maxTurnSpeed * settings.turnTime();
        float change = Math.clamp(lag, -maxChange, maxChange);
        float pull = (lagRate + omega * change) * frameTime;
        float eased = lag - change + (change + pull) * decay;

        lagRate = (lagRate - omega * pull) * decay;

        if (lag > 0.0F ? eased < 0.0F : eased > 0.0F) {
            eased = 0.0F;
            lagRate = 0.0F;
        }

        lag = eased;
    }

    /// How far the shown body is behind the body yaw, in degrees.
    float lag() {
        return lag;
    }

    /// Puts the shown body on the yaw.
    void reset() {
        lag = 0.0F;
        lagRate = 0.0F;
    }
}
