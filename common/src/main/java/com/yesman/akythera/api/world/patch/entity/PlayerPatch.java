package com.yesman.akythera.api.world.patch.entity;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@ScriptType(autoRegister = false)
public class PlayerPatch extends LivingEntityPatch<Player> {
    public PlayerPatch(final Holder.@Nullable Reference<EntityPatchBlueprint> blueprint, @NonNull final Player original) {
        super(blueprint, original);
    }

    public PlayerPatch(final EntityPatchBlueprint blueprint, @NonNull final Player original) {
        super(blueprint, original);
    }

    public PlayerPatch(final PlayerPatch copyTarget) {
        super(copyTarget);
    }

    /// Whether the attack [#hurtTarget] runs sweeps and resets the attack strength. True outside one.
    private boolean vanillaAttackEffects = true;

    /// The entity this player attacks, or null.
    private @Nullable LivingEntity target;

    /// Clears a target that left the level.
    @Override
    public @Nullable LivingEntity getTarget() {
        if (target != null && target.isRemoved()) target = null;

        return target;
    }

    @Override
    public void setTarget(final @Nullable LivingEntity target) {
        this.target = target;
    }

    /// Strikes `target` as a player's attack, with its attack strength and critical hit.
    @Override
    public void hurtTarget(final Entity target, final boolean vanillaAttackEffects) {
        if (getOriginal().level().isClientSide()) return;

        this.vanillaAttackEffects = vanillaAttackEffects;

        try {
            getOriginal().attack(target);
        } finally {
            this.vanillaAttackEffects = true;
        }
    }

    public boolean hasVanillaAttackEffects() {
        return vanillaAttackEffects;
    }

    @ScriptFunction(pure = true, label = "Get Player Skin", category = "Online", desc = """
        Retrieves the player's skin from Minecraft's authentication server
    """)
    public PlayerSkin getPlayerSkin() {
        return ComponentsFactory.getImpl().getPlayerSkin(getOriginal());
    }

    @ScriptFunction(pure = true, label = "Is Cape Shown", category = "Online", desc = """
        Returns whether the player shows the cape in their skin customization
    """)
    public boolean isCapeShown() {
        return getOriginal().isModelPartShown(PlayerModelPart.CAPE);
    }
}
