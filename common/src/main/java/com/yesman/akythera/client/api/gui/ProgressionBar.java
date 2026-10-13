package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.data.serializable.ProgressionFillDirection;
import com.yesman.akythera.api.data.serializable.ProgressionFillStyle;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.render.TriangleFanRenderState;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// Shows how far something has progressed, from 0 to 1, by filling an empty image with a filled one.
///
/// A change of progression animates: the area between the old and the new value shows the lerp image, held
/// at its full span for [#flashTime], then shrinking toward the new value over the lerp time. Takes no input.
@ScriptType
public class ProgressionBar extends AbstractWidget implements AnchoredWidget, TickableComponent {
    /// Segments a whole circle is drawn with.
    private static final int CIRCLE_SEGMENTS = 64;

    /// Polled once per client tick for the progression. Null leaves the progression to [#setProgression].
    @Nullable
    private final Supplier<Float> progressionSource;

    private ProgressionFillStyle fillStyle = ProgressionFillStyle.EQUAL;
    private ProgressionFillDirection fillDirection = ProgressionFillDirection.LEFT_TO_RIGHT;

    /// Inset of the non-highlighted image under the two highlight styles, in pixels.
    private int highlightThickness = 1;

    /// Seconds of real time the lerp area holds its full span after a change, before it starts shrinking.
    private float flashTime;

    /// Seconds of real time the interpolation takes: for every change while [#fixedLerpTime], else for a
    /// change across the whole bar, a smaller change taking its share of it.
    private float lerpTime;
    private boolean fixedLerpTime;

    private WidgetImage filledImage = WidgetImage.of(new RGBA(1.0F, 1.0F, 1.0F));
    private WidgetImage emptyImage = WidgetImage.of(new RGBA(0.12F, 0.12F, 0.12F));
    private WidgetImage lerpImage = WidgetImage.of(new RGBA(0.9F, 0.75F, 0.25F));

    /// The real progression, from 0 to 1.
    private float progression;

    /// The real progression before the last change, which the interpolation starts from.
    private float lerpFrom;

    /// When the progression last changed, in [Util#getMillis].
    private long changedAt;

    public ProgressionBar(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final @Nullable Supplier<Float> progressionSource
    ) {
        super(0, 0, 0, 0, Component.empty());

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.progressionSource = progressionSource;
    }

    /// Polls the progression source, if any.
    @Override
    public void tick() {
        if (progressionSource != null) setProgression(progressionSource.get());
    }

    /// Changes the progression, animating from the real value before this change.
    @ScriptFunction
    public void setProgression(final float progression) {
        float clamped = Mth.clamp(progression, 0.0F, 1.0F);

        if (clamped == this.progression) return;

        lerpFrom = this.progression;
        this.progression = clamped;
        changedAt = Util.getMillis();
    }

    /// Changes the progression without animating.
    @ScriptFunction
    public void setProgressionImmediately(final float progression) {
        this.progression = Mth.clamp(progression, 0.0F, 1.0F);
        lerpFrom = this.progression;
    }

    /// The real progression.
    @ScriptFunction(pure = true)
    public float getProgression() {
        return progression;
    }

    /// The progression as drawn now, between the value before the last change and the real one.
    public float getVisualProgression() {
        if (lerpFrom == progression) return progression;

        float elapsed = (Util.getMillis() - changedAt) / 1000.0F - flashTime;

        if (elapsed < 0.0F) return lerpFrom;

        float duration = fixedLerpTime ? lerpTime : lerpTime * Math.abs(progression - lerpFrom);

        if (elapsed >= duration) return progression;

        return Mth.lerp(elapsed / duration, lerpFrom, progression);
    }

    public ProgressionBar setFillStyle(final ProgressionFillStyle fillStyle) {
        this.fillStyle = fillStyle;
        return this;
    }

    public ProgressionBar setFillDirection(final ProgressionFillDirection fillDirection) {
        this.fillDirection = fillDirection;
        return this;
    }

    public ProgressionBar setHighlightThickness(final int highlightThickness) {
        this.highlightThickness = Math.max(0, highlightThickness);
        return this;
    }

    public ProgressionBar setFlashTime(final float flashTime) {
        this.flashTime = Math.max(0.0F, flashTime);
        return this;
    }

    public ProgressionBar setLerpTime(final float lerpTime, final boolean fixedLerpTime) {
        this.lerpTime = Math.max(0.0F, lerpTime);
        this.fixedLerpTime = fixedLerpTime;
        return this;
    }

    public ProgressionBar setImages(final WidgetImage filledImage, final WidgetImage emptyImage, final WidgetImage lerpImage) {
        this.filledImage = filledImage;
        this.emptyImage = emptyImage;
        this.lerpImage = lerpImage;
        return this;
    }

    public ProgressionFillStyle getFillStyle() {
        return fillStyle;
    }

    public ProgressionFillDirection getFillDirection() {
        return fillDirection;
    }

    public int getHighlightThickness() {
        return highlightThickness;
    }

    public float getFlashTime() {
        return flashTime;
    }

    public float getLerpTime() {
        return lerpTime;
    }

    public boolean isFixedLerpTime() {
        return fixedLerpTime;
    }

    public WidgetImage getFilledImage() {
        return filledImage;
    }

    public WidgetImage getEmptyImage() {
        return emptyImage;
    }

    public WidgetImage getLerpImage() {
        return lerpImage;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        float visual = getVisualProgression();
        float low = Math.min(visual, progression);
        float high = Math.max(visual, progression);
        ScreenRectangle whole = getRectangle();
        ScreenRectangle inset = new ScreenRectangle(
            getX() + highlightThickness,
            getY() + highlightThickness,
            Math.max(0, getWidth() - highlightThickness * 2),
            Math.max(0, getHeight() - highlightThickness * 2)
        );

        switch (fillStyle) {
            case EQUAL -> {
                drawSpan(graphics, emptyImage, whole, 0.0F, 1.0F);
                drawSpan(graphics, filledImage, whole, 0.0F, low);
                drawSpan(graphics, lerpImage, whole, low, high);
            }
            case HIGHLIGHT_EMPTY -> {
                drawSpan(graphics, emptyImage, whole, 0.0F, 1.0F);
                drawSpan(graphics, filledImage, inset, 0.0F, low);
                drawSpan(graphics, lerpImage, inset, low, high);
            }
            case HIGHLIGHT_FILLED -> {
                drawSpan(graphics, filledImage, whole, 0.0F, 1.0F);
                drawSpan(graphics, emptyImage, inset, high, 1.0F);
                drawSpan(graphics, lerpImage, inset, low, high);
            }
        }
    }

    /// Draws the part of `rect` the progression covers between `from` and `to`, cut out of `image` laid over
    /// `rect`.
    private void drawSpan(final GuiGraphicsExtractor graphics, final WidgetImage image, final ScreenRectangle rect, final float from, final float to) {
        if (to <= from || rect.width() <= 0 || rect.height() <= 0) return;

        float left = rect.left();
        float top = rect.top();
        float right = rect.right();
        float bottom = rect.bottom();
        float width = rect.width();

        float[] positions = switch (fillDirection) {
            case LEFT_TO_RIGHT -> {
                float start = left + width * from;
                float end = left + width * to;
                yield new float[] { start, top, start, bottom, end, bottom, end, top };
            }
            case RIGHT_TO_LEFT -> {
                float start = right - width * to;
                float end = right - width * from;
                yield new float[] { start, top, start, bottom, end, bottom, end, top };
            }
            case RADIAL_CIRCLE_CLOCKWISE -> circleSector(rect, from, to, false);
            case RADIAL_CIRCLE_COUNTER_CLOCKWISE -> circleSector(rect, from, to, true);
            case RADIAL_BOX_CLOCKWISE -> boxSector(rect, from, to, false);
            case RADIAL_BOX_COUNTER_CLOCKWISE -> boxSector(rect, from, to, true);
        };

        TriangleFanRenderState.fill(graphics, image, rect, positions);
    }

    /// The sector of the ellipse inscribed in `rect` between `from` and `to` of a turn from the top, fanned
    /// from the center.
    private static float[] circleSector(final ScreenRectangle rect, final float from, final float to, final boolean counterClockwise) {
        float centerX = rect.left() + rect.width() * 0.5F;
        float centerY = rect.top() + rect.height() * 0.5F;
        float radiusX = rect.width() * 0.5F;
        float radiusY = rect.height() * 0.5F;
        int segments = Math.max(1, Mth.ceil(CIRCLE_SEGMENTS * (to - from)));
        float[] positions = new float[(segments + 2) * 2];

        positions[0] = centerX;
        positions[1] = centerY;

        for (int i = 0; i <= segments; i++) {
            float angle = Mth.TWO_PI * Mth.lerp((float) i / segments, from, to);
            float sin = Mth.sin(angle);

            positions[i * 2 + 2] = centerX + radiusX * (counterClockwise ? -sin : sin);
            positions[i * 2 + 3] = centerY - radiusY * Mth.cos(angle);
        }

        return positions;
    }

    /// The sector of `rect` between `from` and `to` of a turn from the top, fanned from the center, with the
    /// corners the sweep passes.
    private static float[] boxSector(final ScreenRectangle rect, final float from, final float to, final boolean counterClockwise) {
        float halfWidth = rect.width() * 0.5F;
        float halfHeight = rect.height() * 0.5F;

        // Turns from the top, clockwise, at which the sweep meets each corner: top right, bottom right,
        // bottom left, top left.
        float corner = (float) (Math.atan2(halfWidth, halfHeight) / Mth.TWO_PI);
        float[] corners = { corner, 0.5F - corner, 0.5F + corner, 1.0F - corner };

        float[] turns = new float[6];
        int count = 0;

        turns[count++] = from;

        for (float cornerTurn : corners) {
            if (cornerTurn > from && cornerTurn < to) turns[count++] = cornerTurn;
        }

        turns[count++] = to;

        float centerX = rect.left() + halfWidth;
        float centerY = rect.top() + halfHeight;
        float[] positions = new float[(count + 1) * 2];

        positions[0] = centerX;
        positions[1] = centerY;

        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * turns[i];
            float dx = Mth.sin(angle);
            float dy = -Mth.cos(angle);

            // Out from the center along the sweep until the ray leaves the rect.
            float reach = Math.min(
                Math.abs(dx) < 1.0E-6F ? Float.MAX_VALUE : halfWidth / Math.abs(dx),
                Math.abs(dy) < 1.0E-6F ? Float.MAX_VALUE : halfHeight / Math.abs(dy)
            );

            positions[i * 2 + 2] = centerX + reach * (counterClockwise ? -dx : dx);
            positions[i * 2 + 3] = centerY + reach * dy;
        }

        return positions;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
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
