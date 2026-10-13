package com.yesman.akythera.client.api.gui;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.client.api.gui.script.HUDScript;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/// Shows [AkytheraHUD]s over the game, laid out against the GUI-scaled window. Every HUD is dropped when
/// the local player's patch is replaced (respawn, dimension change) or the world is left.
public final class HudManager {
    private static final HudManager INSTANCE = new HudManager();

    public static HudManager getInstance() {
        return INSTANCE;
    }

    /// Drops the HUDs when their player patch goes away.
    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Entity.CONSTRUCTED.registerEvent(event -> {
            if (event.getEntityPatch().getOriginal() instanceof LocalPlayer) INSTANCE.clear();
        });
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(_ -> INSTANCE.clear());
    }

    /// The shown HUDs in draw order.
    private final List<AkytheraHUD> huds = new ArrayList<>();

    /// The GUI-scaled window rect the HUDs were last laid out against.
    private ScreenRectangle windowRectangle = ScreenRectangle.empty();

    private HudManager() {
    }

    /// Shows `hud` over the ones already shown.
    public void addHud(final AkytheraHUD hud) {
        huds.add(hud);
        hud.relocate(windowRectangle);

        if (hud.getScript() instanceof HUDScript script) script.onAdded();
    }

    /// Stops showing `hud`.
    public void removeHud(final AkytheraHUD hud) {
        if (huds.remove(hud) && hud.getScript() instanceof HUDScript script) script.onRemoved();
    }

    /// Stops showing every HUD.
    public void clear() {
        for (AkytheraHUD hud : List.copyOf(huds)) {
            removeHud(hud);
        }
    }

    /// The shown HUDs in draw order.
    public List<AkytheraHUD> getHuds() {
        return List.copyOf(huds);
    }

    /// Ticks the shown HUDs that are enabled.
    @ApiStatus.Internal
    public void tick() {
        for (AkytheraHUD hud : List.copyOf(huds)) {
            if (hud.isEnabled()) hud.tick();
        }
    }

    /// Draws the shown HUDs, laying them out again when the window was resized. They take no input, so they
    /// see no pointer.
    @ApiStatus.Internal
    public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
        Window window = Minecraft.getInstance().getWindow();
        ScreenRectangle rectangle = new ScreenRectangle(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());

        if (!rectangle.equals(windowRectangle)) {
            windowRectangle = rectangle;

            for (AkytheraHUD hud : huds) {
                hud.relocate(windowRectangle);
            }
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        for (AkytheraHUD hud : huds) {
            hud.extractRenderState(graphics, -1, -1, partialTick);
        }
    }
}
