package com.yesman.akythera.api.world.patch.entity;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    @ScriptFunction(pure = true, label = "Get Player Skin", category = "Online", desc = """
        Retrieves the player's skin from Minecraft's authentication server
    """)
    public PlayerSkin getPlayerSkin() {
        return ComponentsFactory.getImpl().getPlayerSkin(getOriginal());
    }
}
