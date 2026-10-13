package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.ThemeApplicableWidget;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.core.blueprint.widget.ButtonBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/// Extends properties of [Button], but supports easy resizing, positioning by [AnchoredWidget].
///
/// Styled by a [ButtonTheme], or, while it has none, by three [WidgetImage] sprites: normal, focused (hovered
/// or focused) and disabled.
@ScriptType
public class AkytheraButton extends Button implements AnchoredWidget, ThemeApplicableWidget<ButtonTheme> {
    protected final Font font;
    protected final Function<AbstractWidget, Boolean> isHighlighted;
    protected final Function<AkytheraButton, @Nullable Tooltip> tooltipProvider;

    @Nullable
    protected final SpriteInfo sprite;

    /// Played when the pointer starts hovering the button. Null plays nothing.
    protected final @Nullable Holder<SoundEvent> hoverSound;

    /// Played when the button is pressed. Null plays nothing.
    protected final @Nullable Holder<SoundEvent> pressSound;

    /// Null draws [#normalImage], [#focusedImage] or [#disabledImage] instead.
    protected @Nullable ButtonTheme theme;

    private WidgetImage normalImage;
    private WidgetImage focusedImage;
    private WidgetImage disabledImage;
    private LocalizedMessage localizedMessage;

    /// Whether the pointer hovered the button in the last frame, so [#hoverSound] plays on entering only.
    private boolean wasHovered;

    protected AkytheraButton(AkytheraButton.Builder builder) {
        super(0, 0, 0, 0, builder.message.toComponent(), _ -> builder.onPressed.run(), builder.createNarration);

        this.font = builder.font;
        this.x1 = builder.x1;
        this.x2 = builder.x2;
        this.y1 = builder.y1;
        this.y2 = builder.y2;
        this.horizontalAnchorType = builder.horizontalAnchorType;
        this.verticalAnchorType = builder.verticalAnchorType;
        this.theme = builder.buttonTheme;
        this.isHighlighted = builder.isHighlighted;
        this.tooltipProvider = builder.tooltipProvider;
        this.sprite = builder.sprite;
        this.hoverSound = builder.hoverSound;
        this.pressSound = builder.pressSound;
        this.normalImage = builder.normalImage;
        this.focusedImage = builder.focusedImage;
        this.disabledImage = builder.disabledImage;
        this.localizedMessage = builder.message;

        setAlpha(builder.alpha);
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        setTooltip(tooltipProvider.apply(this));

        if (isHovered() && !wasHovered && hoverSound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(hoverSound, 1.0F));
        }

        wasHovered = isHovered();

        int fontColor;

        if (theme != null) {
            theme.renderBackground(this, graphics, mouseX, mouseY, partialTick);
            fontColor = theme.getFontColor(this);
        } else {
            WidgetImage image = !active ? disabledImage : isHighlighted() ? focusedImage : normalImage;
            int color = image.color().packARGB();

            if (image.texture() == null) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), color);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, image.texture(), getX(), getY(), getWidth(), getHeight(), color);
            }

            fontColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        }

        // Apply the label font color
        message = message.copy().withStyle(style -> style.withColor(fontColor));
        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));

        if (sprite != null) {
            sprite.render(graphics, this);
        }
    }

    @ScriptFunction(pure = true)
    public LocalizedMessage getLocalizedMessage() {
        return localizedMessage;
    }

    @ScriptFunction
    public void setMessage(final LocalizedMessage message) {
        this.localizedMessage = message;
        setMessage(message.toComponent());
    }

    /// Sets the sprites drawn while the button has no theme.
    public void setImages(final WidgetImage normalImage, final WidgetImage focusedImage, final WidgetImage disabledImage) {
        this.normalImage = normalImage;
        this.focusedImage = focusedImage;
        this.disabledImage = disabledImage;
    }

    public WidgetImage getNormalImage() {
        return normalImage;
    }

    public WidgetImage getFocusedImage() {
        return focusedImage;
    }

    public WidgetImage getDisabledImage() {
        return disabledImage;
    }

    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return ButtonTheme.class.isAssignableFrom(theme.getClass());
    }

    @Override
    public @Nullable ButtonTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(@Nullable ButtonTheme theme) {
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

    public static AkytheraButton.Builder buttonBuilder(Font font, LocalizedMessage message, Runnable onPressed) {
        return new AkytheraButton.Builder(font, message, onPressed);
    }

    public static class Builder {
        private final Font font;
        private final LocalizedMessage message;
        private final Runnable onPressed;
        private Function<AkytheraButton, @Nullable Tooltip> tooltipProvider;
        @Nullable
        private SpriteInfo sprite;
        private Unit x1;
        private Unit x2;
        private Unit y1;
        private Unit y2;
        private CreateNarration createNarration;
        private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
        private AnchoredWidget.VerticalAnchorType verticalAnchorType;
        private @Nullable ButtonTheme buttonTheme;
        private WidgetImage normalImage;
        private WidgetImage focusedImage;
        private WidgetImage disabledImage;
        private Function<AbstractWidget, Boolean> isHighlighted;
        private @Nullable Holder<SoundEvent> hoverSound;
        private @Nullable Holder<SoundEvent> pressSound;
        private float alpha;

        private Builder(Font font, LocalizedMessage message, Runnable onPressed) {
            this.font = font;
            this.createNarration = Button.DEFAULT_NARRATION;
            this.message = message;
            this.onPressed = onPressed;
            this.horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
            this.verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;
            this.buttonTheme = BuiltInButtonTheme.VANILLA_BUTTON;
            this.normalImage = ButtonBlueprint.DEFAULT_NORMAL_IMAGE;
            this.focusedImage = ButtonBlueprint.DEFAULT_FOCUSED_IMAGE;
            this.disabledImage = ButtonBlueprint.DEFAULT_DISABLED_IMAGE;
            this.isHighlighted = AbstractWidget::isHoveredOrFocused;
            this.tooltipProvider = _ -> null;
            this.alpha = 1.0F;
        }

        public AkytheraButton.Builder xUnits(Unit x1, Unit x2) {
            this.x1 = x1;
            this.x2 = x2;
            return this;
        }

        public AkytheraButton.Builder yUnits(Unit y1, Unit y2) {
            this.y1 = y1;
            this.y2 = y2;
            return this;
        }

        public AkytheraButton.Builder horizontalAnchorType(AnchoredWidget.HorizontalAnchorType horizontalAnchorType) {
            this.horizontalAnchorType = horizontalAnchorType;
            return this;
        }

        public AkytheraButton.Builder verticalAnchorType(AnchoredWidget.VerticalAnchorType verticalAnchorType) {
            this.verticalAnchorType = verticalAnchorType;
            return this;
        }

        /// The tooltip is asked for every frame, so a button whose state changes can answer
        /// differently. Return null for no tooltip.
        public AkytheraButton.Builder tooltip(Function<AkytheraButton, @Nullable Tooltip> tooltipProvider) {
            this.tooltipProvider = tooltipProvider;
            return this;
        }

        public AkytheraButton.Builder sprite(Identifier sprite, int width, int height) {
            this.sprite = new SpriteInfo(sprite, width, height);
            return this;
        }

        public AkytheraButton.Builder createNarration(CreateNarration createNarration) {
            this.createNarration = createNarration;
            return this;
        }

        /// The theme styling the button. Null draws the [#images] instead.
        public AkytheraButton.Builder theme(@Nullable ButtonTheme theme) {
            this.buttonTheme = theme;
            return this;
        }

        /// The sprites drawn while the button has no theme. Vanilla's button sprites by default.
        public AkytheraButton.Builder images(WidgetImage normalImage, WidgetImage focusedImage, WidgetImage disabledImage) {
            this.normalImage = normalImage;
            this.focusedImage = focusedImage;
            this.disabledImage = disabledImage;
            return this;
        }

        public AkytheraButton.Builder isHighlighted(Function<AbstractWidget, Boolean> function) {
            this.isHighlighted = function;
            return this;
        }

        public AkytheraButton.Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public AkytheraButton.Builder hoverSound(@Nullable Holder<SoundEvent> hoverSound) {
            this.hoverSound = hoverSound;
            return this;
        }

        public AkytheraButton.Builder pressSound(@Nullable Holder<SoundEvent> pressSound) {
            this.pressSound = pressSound;
            return this;
        }

        public AkytheraButton build() {
            return this.build(AkytheraButton::new);
        }

        public AkytheraButton build(Function<AkytheraButton.Builder, AkytheraButton> builder) {
            return builder.apply(this);
        }
    }

    public record SpriteInfo(Identifier sprite, int width, int height) {
        public void render(@NonNull final GuiGraphicsExtractor graphics, final AkytheraButton button) {
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

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
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
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
