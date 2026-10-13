package com.yesman.akythera.client.api.data.modularize;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.HudManager;
import com.yesman.akythera.client.config.AkytheraClientConfig;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Script functions whose signatures name client-only classes.
public final class AkytheraClientScriptFunctions {
    private AkytheraClientScriptFunctions() {}

    /// The `akythera:entity_render_type` function, answering [#entityRenderTypeOf].
    public static ScriptFunction entityRenderType() {
        return ScriptModularizeHelper.makeCustomScript(
            (BiFunction<String, Boolean, RenderType>) AkytheraClientScriptFunctions::entityRenderTypeOf,
            FunctionSignature.create(RenderType.class, List.of("texture", "translucent"), String.class, Boolean.class),
            "Entity Render Type",
            "Render",
            "The entity render type drawing a texture",
            true,
            ScriptFunction.Usage.SCRIPT
        );
    }

    /// The entity render type drawing `texture`: translucent, or alpha-cutout without culling. Null for a
    /// texture id that does not parse.
    public static @Nullable RenderType entityRenderTypeOf(final @Nullable String texture, final @Nullable Boolean translucent) {
        Identifier textureId = texture == null ? null : Identifier.tryParse(texture);

        if (textureId == null) return null;

        return Boolean.TRUE.equals(translucent) ? RenderTypes.entityTranslucent(textureId) : RenderTypes.entityCutout(textureId);
    }

    /// Whether [AkytheraClientConfig#getDefaultAnimationDriver] picks Player Animation Library.
    public static ScriptFunction prefersPlayerAnimationLibrary() {
        return ScriptModularizeHelper.makeCustomScript(
            (Supplier<Boolean>) () ->
                AkytheraClientConfig.getDefaultAnimationDriver() == AkytheraClientConfig.DefaultAnimationDriver.PLAYER_ANIMATION_LIBRARY,
            FunctionSignature.create(Boolean.class, List.of()),
            "Prefers Player Animation Library",
            "Animation Driver",
            "Whether the client config picks Player Animation Library over the skinned mesh",
            true,
            ScriptFunction.Usage.SCRIPT
        );
    }

    // ***************************************************************
    // Widget
    // ***************************************************************

    /// The [ScriptFunction.Usage#WIDGET_CTOR] building an [AkytheraScreen].
    public static ScriptFunction screenConstructor() {
        return ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Holder.Reference<WidgetBlueprint>, Object, AkytheraScreen>) AkytheraScreen::new,
            FunctionSignature.create(AkytheraScreen.class, List.of("blueprint", "owner"), Holder.Reference.class, Object.class),
            "Screen",
            "Widget",
            "A screen of widgets, opened over the game and taking its input",
            true,
            ScriptFunction.Usage.WIDGET_CTOR
        );
    }

    /// The [ScriptFunction.Usage#WIDGET_CTOR] building an [AkytheraWidget].
    public static ScriptFunction widgetConstructor() {
        return ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Holder.Reference<WidgetBlueprint>, Object, AkytheraWidget>) AkytheraWidget::new,
            FunctionSignature.create(AkytheraWidget.class, List.of("blueprint", "owner"), Holder.Reference.class, Object.class),
            "Widget",
            "Widget",
            "A group of widgets placed inside another widget blueprint",
            true,
            ScriptFunction.Usage.WIDGET_CTOR
        );
    }

    /// The [ScriptFunction.Usage#WIDGET_CTOR] building an [AkytheraHUD].
    public static ScriptFunction hudConstructor() {
        return ScriptModularizeHelper.makeCustomScript(
            (BiFunction<Holder.Reference<WidgetBlueprint>, Object, AkytheraHUD>) AkytheraHUD::new,
            FunctionSignature.create(AkytheraHUD.class, List.of("blueprint", "owner"), Holder.Reference.class, Object.class),
            "HUD",
            "Widget",
            "Widgets drawn over the game with the vanilla HUD, taking no input",
            true,
            ScriptFunction.Usage.WIDGET_CTOR
        );
    }

    /// Opens the [WidgetUsage#SCREEN] blueprint for the local player. Does nothing off the render thread, where an
    /// integrated server runs its scripts.
    public static ScriptFunction openScreen() {
        return ScriptModularizeHelper.makeCustomScript(
            (Function<WidgetBlueprint, AkytheraScreen>) blueprint ->
                blueprint != null && blueprint.usage() == WidgetUsage.SCREEN && Minecraft.getInstance().isSameThread()
                    && BlueprintWidgets.open(holderOf(blueprint)) instanceof AkytheraScreen screen ? screen : null,
            FunctionSignature.create(AkytheraScreen.class, List.of("blueprint"), WidgetBlueprint.class),
            "Open Screen",
            "Widget",
            "Opens a screen blueprint for the local player, replacing the open screen. Null for a blueprint that is no screen",
            false,
            ScriptFunction.Usage.SCRIPT
        );
    }

    /// Shows the [WidgetUsage#HUD] blueprint for the local player. Does nothing off the render thread, where an
    /// integrated server runs its scripts.
    public static ScriptFunction addHud() {
        return ScriptModularizeHelper.makeCustomScript(
            (Function<WidgetBlueprint, AkytheraHUD>) blueprint ->
                blueprint != null && blueprint.usage() == WidgetUsage.HUD && Minecraft.getInstance().isSameThread()
                    && BlueprintWidgets.open(holderOf(blueprint)) instanceof AkytheraHUD hud ? hud : null,
            FunctionSignature.create(AkytheraHUD.class, List.of("blueprint"), WidgetBlueprint.class),
            "Add HUD",
            "Widget",
            "Shows a HUD blueprint for the local player over the shown HUDs. Null for a blueprint that is no HUD",
            false,
            ScriptFunction.Usage.SCRIPT
        );
    }

    public static ScriptFunction removeHud() {
        return ScriptModularizeHelper.makeCustomScript(
            (Consumer<AkytheraHUD>) hud -> {
                if (hud != null && Minecraft.getInstance().isSameThread()) HudManager.getInstance().removeHud(hud);
            },
            FunctionSignature.create(void.class, List.of("hud"), AkytheraHUD.class),
            "Remove HUD",
            "Widget",
            "Stops showing a HUD",
            false,
            ScriptFunction.Usage.SCRIPT
        );
    }

    /// The title of the blueprint `screen` was built from. Empty for a screen built in code.
    public static ScriptFunction screenTitle() {
        return ScriptModularizeHelper.makeCustomScript(
            (Function<AkytheraScreen, LocalizedMessage>) screen ->
                screen == null || screen.getBlueprint() == null ? LocalizedMessage.EMPTY : screen.getBlueprint().value().title(),
            FunctionSignature.create(LocalizedMessage.class, List.of("screen"), AkytheraScreen.class),
            "Get Title",
            "Widget",
            "The title the screen blueprint declares",
            true,
            ScriptFunction.Usage.SCRIPT
        );
    }

    /// What a container built from a blueprint was built for. Null for any other host.
    public static ScriptFunction widgetOwner() {
        return ScriptModularizeHelper.makeCustomScript(
            (Function<ComponentAttachable, Object>) host -> switch (host) {
                case AkytheraScreen screen -> screen.getOwner();
                case AkytheraWidget widget -> widget.getOwner();
                case null, default -> null;
            },
            FunctionSignature.create(Object.class, List.of("host"), ComponentAttachable.class),
            "Get Owner",
            "Widget",
            "What a widget blueprint was built for: the local player's patch for a screen or a HUD, the parent for a widget",
            true,
            ScriptFunction.Usage.SCRIPT
        );
    }

    @SuppressWarnings("unchecked")
    private static Holder.Reference<WidgetBlueprint> holderOf(final WidgetBlueprint blueprint) {
        return (Holder.Reference<WidgetBlueprint>) StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.WIDGET_KEY).wrapAsHolder(blueprint);
    }
}
