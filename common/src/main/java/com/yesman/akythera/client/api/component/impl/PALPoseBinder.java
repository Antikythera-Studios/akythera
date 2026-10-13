package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.compat.playeranimlibrary.AkytheraAnimationLayer;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

/// Binds the entity's own vanilla model to the pose of the [ArmatureComponent] it is attached to,
/// through Player Animation Library.
///
/// Draws nothing itself: [LivingEntityRenderer] keeps submitting the model, and
/// [AkytheraAnimationLayer] poses it while this binder is on. Whether that model is drawn at all is
/// [EntityPatch#isBlockingVanillaRender] and the renderables standing in for it.
///
/// Requires Player Animation Library: [#instantiate] throws without it.
///
/// Simultaneously a component and a blueprint: [#instantiate] copies the authored binder per
/// armature, the way [TransformSocket] copies itself, so each armature owns its own switch.
public final class PALPoseBinder
    implements UniqueComponent, ComponentBlueprint<PALPoseBinder, ArmatureComponent>
{
    /// The only authored state is the switch each copy starts in.
    public static final MapCodec<PALPoseBinder> CODEC =
        ENABLED_CODEC.xmap(PALPoseBinder::new, PALPoseBinder::enabled);

    private boolean enabled;

    public PALPoseBinder() {
        this(true);
    }

    public PALPoseBinder(final boolean enabled) {
        this.enabled = enabled;
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

    // ***************************************************************
    // ComponentBlueprint
    // ***************************************************************

    @Override
    public PALPoseBinder instantiate(final ArmatureComponent blueprintOwner) {
        if (!palLoaded()) {
            throw new IllegalStateException("PAL Pose Binder requires PAL to be installed, but it doesn't.");
        }

        return new PALPoseBinder(enabled);
    }

    /// Built only on a client that has Player Animation Library. Elsewhere the binder is skipped, and
    /// a lookup by name answers null.
    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient && palLoaded();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<PALPoseBinder, ArmatureComponent>> mapCodec() {
        return CODEC;
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// Whether Player Animation Library is installed.
    private static boolean palLoaded() {
        return ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId());
    }
}
