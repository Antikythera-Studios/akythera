package com.yesman.akythera.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/// The key mappings Akythera ships, which an [InputAction] may name.
@ClientOnly
public final class AkytheraKeyMappings {
    public static final String CATEGORY = "key.categories.akythera";

    public static final KeyMapping PRIMARY_ABILITY = ability("key.akythera.primary_ability", GLFW.GLFW_KEY_R);
    public static final KeyMapping SECONDARY_ABILITY = ability("key.akythera.secondary_ability", GLFW.GLFW_KEY_G);
    public static final KeyMapping ULTIMATE_ABILITY = ability("key.akythera.ultimate_ability", GLFW.GLFW_KEY_V);

    private AkytheraKeyMappings() {}

    /// Every mapping this class declares.
    public static List<KeyMapping> all() {
        return List.of(PRIMARY_ABILITY, SECONDARY_ABILITY, ULTIMATE_ABILITY);
    }

    /// Runs this class's initializer, which registers the mappings.
    public static void bootStrap() {

    }

    private static KeyMapping ability(final String name, final int key) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, key, KeyMapping.Category.GAMEPLAY);
    }
}
