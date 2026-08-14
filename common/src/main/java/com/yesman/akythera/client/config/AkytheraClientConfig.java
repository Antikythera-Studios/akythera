/// Written in `forgeconfigapiport`
///
/// the namespace `neoforge` in this class is a reflection
/// of NeoForge's original code, the usage is ensured by
/// non-forge platforms by `forgeconfigapiport`.

package com.yesman.akythera.client.config;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.EntityModelComponent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public final class AkytheraClientConfig {
    private AkytheraClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // config specs
    public static final ModConfigSpec.ConfigValue<DefaultAnimationDriver> DEFAULT_ANIMATION_DRIVER = BUILDER
        .comment("Whether to use built-in or external library mod to apply animation to the model")
        .define("anim_driver", DefaultAnimationDriver.SKINNED_MESH);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // config values
    private static DefaultAnimationDriver defaultAnimationDriver = DefaultAnimationDriver.SKINNED_MESH;

    public static DefaultAnimationDriver getDefaultAnimationDriver() {
        return defaultAnimationDriver;
    }

    public static void setDefaultAnimationDriver(final @NonNull DefaultAnimationDriver value) {
        defaultAnimationDriver = value;
    }

    /// Init config values = config specs
    public static void initConfigValues() {
        defaultAnimationDriver = DEFAULT_ANIMATION_DRIVER.get();
    }

    /// Save config specs = config values
    public static void saveConfigValues() {
        setAndSave(DEFAULT_ANIMATION_DRIVER, defaultAnimationDriver);
    }

    private static <T> void setAndSave(final ModConfigSpec.ConfigValue<T> configSpec, final T value) {
        if (!Objects.equals(configSpec.get(), value)) {
            configSpec.set(value);
            configSpec.save();
        }
    }

    public enum DefaultAnimationDriver {
        /// Requires "Player Animation Library" mod installed.
        ///
        /// Better compatibility with Minecraft addons that doesn't
        /// depend on Akythera, but requires dependency.
        PLAYER_ANIMATION_LIBRARY,

        /// Requires [EntityModelComponent] installed in [EntityPatch].
        ///
        /// Expresses the mesh deformation as seen on Blender better
        /// than PAL, but incompatible with outside addons.
        SKINNED_MESH
    }
}