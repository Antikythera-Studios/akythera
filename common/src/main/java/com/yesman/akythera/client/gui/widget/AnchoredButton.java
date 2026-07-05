package com.yesman.akythera.client.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.ThemeApplicableWidget;
import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import com.yesman.akythera.core.Akythera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/// Extends properties of [Button], but supports easy resizing, positioning by [AnchoredWidget].
///
/// Easily switchable button styles (background, font colors) by [ThemeApplicableWidget]
public class AnchoredButton extends Button implements AnchoredWidget, ThemeApplicableWidget<ButtonTheme> {
    public static final WidgetSprites NUMBERED_TAB_SPRITES = new WidgetSprites(
        Akythera.identifier("widget/numbered_tab_button"),
        Akythera.identifier("widget/numbered_tab_button_disabled"),
        Akythera.identifier("widget/numbered_tab_button_highlighted")
    );

    public static final WidgetSprites EMOTE_WHEEL_UP_SPRITES = new WidgetSprites(
        Akythera.identifier("widget/emote_wheel_up"),
        Akythera.identifier("widget/emote_wheel_up"),
        Akythera.identifier("widget/emote_wheel_up_highlighted")
    );

    public static final WidgetSprites EMOTE_WHEEL_DOWN_SPRITES = new WidgetSprites(
        Akythera.identifier("widget/emote_wheel_down"),
        Akythera.identifier("widget/emote_wheel_down"),
        Akythera.identifier("widget/emote_wheel_down_highlighted")
    );

    protected final Font font;
    protected final Function<AbstractWidget, Boolean> isHighlighted;
    @Nullable
    protected final SpriteInfo sprite;
    protected final Holder.@Nullable Reference<@NonNull SoundEvent> pressSound;
    protected ButtonTheme theme;

    protected AnchoredButton(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final Component message,
        final @Nullable SpriteInfo sprite,
        final Button.OnPress onPress,
        final Button.CreateNarration createNarration,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        final VerticalAnchorType verticalAnchorType,
        final ButtonTheme theme,
        final Function<AbstractWidget, Boolean> isHighlighted,
        final Holder.@Nullable Reference<@NonNull SoundEvent> pressSound
    ) {
        super(0, 0, 0, 0, message, onPress, createNarration);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
        this.theme = theme;
        this.isHighlighted = isHighlighted;
        this.sprite = sprite;
        this.pressSound = pressSound;
    }

    protected AnchoredButton(AnchoredButton.Builder builder) {
        this(
            builder.font,
            builder.x1,
            builder.x2,
            builder.y1,
            builder.y2,
            builder.message,
            builder.sprite,
            builder.onPress,
            builder.createNarration,
            builder.horizontalAnchorType,
            builder.verticalAnchorType,
            builder.buttonTheme,
            builder.isHighlighted,
            builder.pressSound
        );

        setTooltip(builder.tooltip);
        setAlpha(builder.alpha);
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        theme.renderBackground(this, graphics, mouseX, mouseY, partialTick);

        // Apply theme label font color
        int fontColor = theme.getFontColor(this);
        message = message.copy().withStyle(style -> style.withColor(fontColor));
        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));

        if (sprite != null) {
            sprite.render(graphics, this);
        }
    }

    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return ButtonTheme.class.isAssignableFrom(theme.getClass());
    }

    @Override
    public @NotNull ButtonTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(ButtonTheme theme) {
        this.theme = theme;
    }

    /// Returns whether the widget is highlighted
    public boolean isHighlighted() {
        return isHighlighted.apply(this);
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
        if (pressSound != null) soundManager.play(SimpleSoundInstance.forUI(pressSound, 1.0F));
    }

    public static AnchoredButton.Builder buttonBuilder(Font font, Component message, OnPress onPress) {
        return new AnchoredButton.Builder(font, message, onPress);
    }

    public static class Builder {
        private final Font font;
        private final Component message;
        private final OnPress onPress;
        @Nullable
        private Tooltip tooltip;
        @Nullable
        private SpriteInfo sprite;
        private Unit x1;
        private Unit x2;
        private Unit y1;
        private Unit y2;
        private CreateNarration createNarration;
        private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
        private AnchoredWidget.VerticalAnchorType verticalAnchorType;
        private ButtonTheme buttonTheme;
        private Function<AbstractWidget, Boolean> isHighlighted;
        private Holder.Reference<@NonNull SoundEvent> pressSound;
        private float alpha;

        private Builder(Font font, Component message, OnPress onPress) {
            this.font = font;
            this.createNarration = Button.DEFAULT_NARRATION;
            this.message = message;
            this.onPress = onPress;
            this.horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
            this.verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;
            this.buttonTheme = BuiltInButtonTheme.VANILLA_BUTTON;
            this.isHighlighted = AbstractWidget::isHoveredOrFocused;
            this.alpha = 1.0F;
        }

        public AnchoredButton.Builder xUnits(Unit x1, Unit x2) {
            this.x1 = x1;
            this.x2 = x2;
            return this;
        }

        public AnchoredButton.Builder yUnits(Unit y1, Unit y2) {
            this.y1 = y1;
            this.y2 = y2;
            return this;
        }

        public AnchoredButton.Builder horizontalAnchorType(AnchoredWidget.HorizontalAnchorType horizontalAnchorType) {
            this.horizontalAnchorType = horizontalAnchorType;
            return this;
        }

        public AnchoredButton.Builder verticalAnchorType(AnchoredWidget.VerticalAnchorType verticalAnchorType) {
            this.verticalAnchorType = verticalAnchorType;
            return this;
        }

        public AnchoredButton.Builder tooltip(@Nullable Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public AnchoredButton.Builder sprite(Identifier sprite, int width, int height) {
            this.sprite = new SpriteInfo(sprite, width, height);
            return this;
        }

        public AnchoredButton.Builder createNarration(CreateNarration createNarration) {
            this.createNarration = createNarration;
            return this;
        }

        public AnchoredButton.Builder theme(ButtonTheme theme) {
            this.buttonTheme = theme;
            return this;
        }

        public AnchoredButton.Builder isHighlighted(Function<AbstractWidget, Boolean> function) {
            this.isHighlighted = function;
            return this;
        }

        public AnchoredButton.Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public AnchoredButton.Builder pressSound(Holder.Reference<@NonNull SoundEvent> pressSound) {
            this.pressSound = pressSound;
            return this;
        }

        public AnchoredButton build() {
            return this.build(AnchoredButton::new);
        }

        public AnchoredButton build(Function<AnchoredButton.Builder, AnchoredButton> builder) {
            return builder.apply(this);
        }
    }

    public record SpriteInfo(Identifier sprite, int width, int height) {
        public void render(@NonNull final GuiGraphicsExtractor graphics, final AnchoredButton button) {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                sprite,
                button.getX() + (button.getWidth() - width) / 2,
                button.getY() + (button.getHeight() - height) / 2,
                width,
                height
            );
        }
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
