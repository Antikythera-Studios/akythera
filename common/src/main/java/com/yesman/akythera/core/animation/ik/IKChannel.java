package com.yesman.akythera.core.animation.ik;

import net.minecraft.util.Mth;

/// How one IK chain is placed, each value in [0, 1].
///
/// @param alpha      mix of the animated tip (0) and the IK placement (1)
/// @param plant      in curve placement, mix of the animated tip on the ground (0) and the held foot (1)
/// @param procedural mix of curve placement (0) and procedural stepping (1)
public record IKChannel(float alpha, float plant, float procedural) {
    /// No IK: the animation places the chain.
    public static final IKChannel NONE = new IKChannel(0.0F, 0.0F, 0.0F);

    /// Procedural stepping places the chain fully.
    public static final IKChannel PROCEDURAL = new IKChannel(1.0F, 0.0F, 1.0F);

    public static IKChannel lerp(final IKChannel from, final IKChannel to, final float delta) {
        return new IKChannel(
            Mth.lerp(delta, from.alpha, to.alpha),
            Mth.lerp(delta, from.plant, to.plant),
            Mth.lerp(delta, from.procedural, to.procedural)
        );
    }
}
