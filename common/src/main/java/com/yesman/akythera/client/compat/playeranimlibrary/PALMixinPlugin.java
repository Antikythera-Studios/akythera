package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.compat.ModMixinPlugin;
import org.jspecify.annotations.NonNull;

public final class PALMixinPlugin extends ModMixinPlugin {
    @Override
    public @NonNull String getModId() {
        return "player_animation_library";
    }
}
