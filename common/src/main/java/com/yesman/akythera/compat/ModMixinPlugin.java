package com.yesman.akythera.compat;

import com.yesman.akythera.platform.ModPlatformProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/// Enables registering mixins for other mods only when those mods are installed.
///
/// Maintainers: Do not reference any game or Epic Fight classes here.
/// This runs during early game loading.
/// [ModPlatformProvider] is used because the mod loader is already initialized and is loading all mods,
/// including Epic Fight.
///
/// @see IMixinConfigPlugin
@ApiStatus.Internal
public abstract class ModMixinPlugin implements IMixinConfigPlugin {
    protected ModMixinPlugin() {
    }

    /// Judges if the named mod exists by mod platform classes
    /// [ModPlatformProvider#get] is not used here because this mixin
    /// plugin activates before the game classes may be loaded, and the
    /// platform implementations reference them. The loader-provided
    /// checker classes below are safe to touch this early.
    ///
    /// Definitely it is better to use more advanced build tool
    /// like Stonecutter in the future.
    private boolean isModInstalled() {
        boolean installed;
        Class<?> modPlatformCls;

        try {
            modPlatformCls = Class.forName("com.yesman.akythera.platform.fabric.FabricModChecker");
        } catch (ClassNotFoundException e) {
            try {
                modPlatformCls = Class.forName("com.yesman.akythera.platform.neoforged.NeoForgeModChecker");
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }

        try {
            Method m = modPlatformCls.getMethod("isModLoaded", String.class);
            installed = (boolean)m.invoke(null, getModId());
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return installed;
    }

    public abstract @NonNull String getModId();

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return isModInstalled();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
