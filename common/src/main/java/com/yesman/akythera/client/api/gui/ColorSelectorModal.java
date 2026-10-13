package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.render.TriangleFanRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/// Edits an [RGBA]: a hue / saturation wheel, a vertical brightness bar, an alpha slider, and the color as an
/// `RRGGBBAA` hex code. Every change goes to the callback it was opened with; closing runs the close callback.
public class ColorSelectorModal extends AbstractContainerWidget implements Modal, AnchoredWidget, CloseableWidget {
    private static final int WIDTH = 206;
    private static final int HEIGHT = 128;
    private static final int PAD = 6;

    /// Side of the square the wheel is drawn in, in pixels.
    private static final int WHEEL_SIZE = 100;
    private static final int BAR_X = PAD + WHEEL_SIZE + 8;
    private static final int BAR_W = 10;
    private static final int ALPHA_Y = PAD + WHEEL_SIZE + 6;
    private static final int ALPHA_H = 8;
    private static final int COLUMN_X = BAR_X + BAR_W + 8;
    private static final int COLUMN_W = WIDTH - COLUMN_X - PAD;
    private static final int SWATCH_H = 24;
    private static final int HEX_Y = PAD + SWATCH_H * 2 + 8;
    private static final int HEX_H = 16;

    /// Rim vertices the wheel is drawn with.
    private static final int WHEEL_SEGMENTS = 72;

    /// Edge of a checkerboard cell behind translucent colors, in pixels.
    private static final int CHECKER = 4;

    /// What a held press drags.
    private enum Drag { NONE, WHEEL, BRIGHTNESS, ALPHA }

    private final Consumer<RGBA> onChanged;
    private final Runnable onClosed;
    private final RGBA initial;
    private final AkytheraEditBox<String> hexBox;

    /// The color as hue / saturation / value, so a black or grey color keeps the hue it was dragged from.
    private float hue;
    private float saturation;
    private float value;
    private float alpha;

    private Drag drag = Drag.NONE;

    public ColorSelectorModal(final Font font, final RGBA initial, final Consumer<RGBA> onChanged, final Runnable onClosed) {
        super(0, 0, WIDTH, HEIGHT, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.onChanged = onChanged;
        this.onClosed = onClosed;
        this.initial = initial;
        this.x2 = AnchoredWidget.pixel(WIDTH);
        this.y2 = AnchoredWidget.pixel(HEIGHT);
        this.hexBox = createHexBox(font);
        this.hexBox.setX1(AnchoredWidget.pixel(COLUMN_X));
        this.hexBox.setX2(AnchoredWidget.pixel(COLUMN_W));
        this.hexBox.setY1(AnchoredWidget.pixel(HEX_Y));
        this.hexBox.setY2(AnchoredWidget.pixel(HEX_H));
        this.hexBox.setHorizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH);
        this.hexBox.setVerticalAnchorType(VerticalAnchorType.TOP_HEIGHT);
        this.hexBox.setOnCommitted((_, typed) -> applyHex(typed));

        setColor(initial);
        hexBox.setValue(toHex(color()));
    }

    /// Builds the box the color is typed into as an `RRGGBBAA` code. The modal lays it out and reads it.
    protected AkytheraEditBox<String> createHexBox(final Font font) {
        return AkytheraEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> {},
            Component.literal("RRGGBBAA")
        );
    }

    /// The color being edited.
    public RGBA color() {
        int rgb = Mth.hsvToRgb(hue, saturation, value);

        return new RGBA(ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb), Math.round(alpha * 255.0F));
    }

    private void setColor(final RGBA color) {
        float max = Math.max(color.r(), Math.max(color.g(), color.b()));
        float min = Math.min(color.r(), Math.min(color.g(), color.b()));
        float chroma = max - min;

        if (chroma > 0.0F) {
            float sector;

            if (max == color.r()) {
                sector = (color.g() - color.b()) / chroma;
            } else if (max == color.g()) {
                sector = (color.b() - color.r()) / chroma + 2.0F;
            } else {
                sector = (color.r() - color.g()) / chroma + 4.0F;
            }

            hue = Mth.positiveModulo(sector / 6.0F, 1.0F);
        }

        saturation = max <= 0.0F ? 0.0F : chroma / max;
        value = max;
        alpha = color.a();
    }

    /// Takes a typed `RRGGBB` or `RRGGBBAA` code. Anything else leaves the color as it is.
    private void applyHex(final String typed) {
        String hex = typed.trim();

        if (hex.startsWith("#")) hex = hex.substring(1);

        if (hex.length() != 6 && hex.length() != 8) {
            hexBox.setValue(toHex(color()));
            return;
        }

        try {
            long packed = Long.parseLong(hex, 16);
            int rgba = (int) (hex.length() == 6 ? (packed << 8) | 0xFF : packed);

            setColor(new RGBA((rgba >>> 24) & 0xFF, (rgba >>> 16) & 0xFF, (rgba >>> 8) & 0xFF, rgba & 0xFF));
            changed();
        } catch (NumberFormatException e) {
            hexBox.setValue(toHex(color()));
        }
    }

    private static String toHex(final RGBA color) {
        return String.format(
            Locale.ROOT, "%02X%02X%02X%02X",
            Math.round(color.r() * 255.0F), Math.round(color.g() * 255.0F), Math.round(color.b() * 255.0F), Math.round(color.a() * 255.0F)
        );
    }

    /// Hands the new color to the callback and shows it in the hex box, unless the box is being typed in.
    private void changed() {
        RGBA color = color();

        if (!hexBox.isFocused()) hexBox.setValue(toHex(color));

        onChanged.accept(color);
    }

    private void dragTo(final double mouseX, final double mouseY) {
        switch (drag) {
            case WHEEL -> {
                float radius = WHEEL_SIZE * 0.5F;
                double dx = mouseX - (getX() + PAD + radius);
                double dy = mouseY - (getY() + PAD + radius);

                hue = Mth.positiveModulo((float) (Math.atan2(-dy, dx) / Mth.TWO_PI), 1.0F);
                saturation = Math.min(1.0F, (float) Math.sqrt(dx * dx + dy * dy) / radius);
            }
            case BRIGHTNESS -> value = Mth.clamp(1.0F - (float) (mouseY - (getY() + PAD)) / WHEEL_SIZE, 0.0F, 1.0F);
            case ALPHA -> alpha = Mth.clamp((float) (mouseX - (getX() + PAD)) / (BAR_X + BAR_W - PAD), 0.0F, 1.0F);
            case NONE -> {
                return;
            }
        }

        changed();
    }

    @Override
    public void destroy() {
        onClosed.run();
    }

    /// Covers the whole screen, so a press outside reaches [#mouseClicked] and closes the modal.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - getX();
        double y = event.y() - getY();

        if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        float radius = WHEEL_SIZE * 0.5F;
        double wheelX = x - PAD - radius;
        double wheelY = y - PAD - radius;

        if (wheelX * wheelX + wheelY * wheelY <= (radius + 2) * (radius + 2)) {
            drag = Drag.WHEEL;
        } else if (x >= BAR_X && x < BAR_X + BAR_W && y >= PAD && y < PAD + WHEEL_SIZE) {
            drag = Drag.BRIGHTNESS;
        } else if (x >= PAD && x < BAR_X + BAR_W && y >= ALPHA_Y && y < ALPHA_Y + ALPHA_H) {
            drag = Drag.ALPHA;
        } else {
            return super.mouseClicked(event, doubleClick);
        }

        setFocused(null);
        dragTo(event.x(), event.y());

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (drag == Drag.NONE) return super.mouseDragged(event, dx, dy);

        dragTo(event.x(), event.y());

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (drag == Drag.NONE) return super.mouseReleased(event);

        drag = Drag.NONE;

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();

        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();

        return focused != null && focused.charTyped(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = getX();
        int top = getY();

        graphics.fill(left, top, getRight(), getBottom(), 0xFF202020);
        graphics.outline(left, top, getWidth(), getHeight(), 0xFF555555);

        drawWheel(graphics, left + PAD, top + PAD);

        // Brightness: the picked hue and saturation, from full brightness at the top to black.
        int bright = 0xFF000000 | Mth.hsvToRgb(hue, saturation, 1.0F);
        graphics.fillGradient(left + BAR_X, top + PAD, left + BAR_X + BAR_W, top + PAD + WHEEL_SIZE, bright, 0xFF000000);
        graphics.outline(left + BAR_X, top + PAD, BAR_W, WHEEL_SIZE, 0xFF555555);

        int valueY = top + PAD + Math.round((1.0F - value) * (WHEEL_SIZE - 1));
        graphics.fill(left + BAR_X - 1, valueY, left + BAR_X + BAR_W + 1, valueY + 1, 0xFFFFFFFF);

        // Alpha: the color from transparent on the left to opaque on the right, over a checkerboard.
        RGBA color = color();
        int opaque = 0xFF000000 | (color.packARGB() & 0xFFFFFF);
        int alphaLeft = left + PAD;
        int alphaRight = left + BAR_X + BAR_W;
        int alphaTop = top + ALPHA_Y;
        int alphaBottom = alphaTop + ALPHA_H;

        drawCheckerboard(graphics, alphaLeft, alphaTop, alphaRight, alphaBottom);
        TriangleFanRenderState.fill(
            graphics,
            new float[] { alphaLeft, alphaTop, alphaLeft, alphaBottom, alphaRight, alphaBottom, alphaRight, alphaTop },
            new int[] { opaque & 0xFFFFFF, opaque & 0xFFFFFF, opaque, opaque }
        );
        graphics.outline(alphaLeft, alphaTop, alphaRight - alphaLeft, ALPHA_H, 0xFF555555);

        int alphaX = alphaLeft + Math.round(alpha * (alphaRight - alphaLeft - 1));
        graphics.fill(alphaX, alphaTop - 1, alphaX + 1, alphaBottom + 1, 0xFFFFFFFF);

        // Swatches: the color now over the color the modal opened with.
        int columnLeft = left + COLUMN_X;
        int columnRight = columnLeft + COLUMN_W;

        drawCheckerboard(graphics, columnLeft, top + PAD, columnRight, top + PAD + SWATCH_H * 2);
        graphics.fill(columnLeft, top + PAD, columnRight, top + PAD + SWATCH_H, color.packARGB());
        graphics.fill(columnLeft, top + PAD + SWATCH_H, columnRight, top + PAD + SWATCH_H * 2, initial.packARGB());
        graphics.outline(columnLeft, top + PAD, COLUMN_W, SWATCH_H * 2, 0xFF555555);

        hexBox.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /// The hue / saturation wheel at the picked brightness, with a ring where the color sits.
    private void drawWheel(final GuiGraphicsExtractor graphics, final int left, final int top) {
        float radius = WHEEL_SIZE * 0.5F;
        float centerX = left + radius;
        float centerY = top + radius;
        float[] positions = new float[(WHEEL_SEGMENTS + 2) * 2];
        int[] colors = new int[WHEEL_SEGMENTS + 2];

        // Saturation runs linearly from white at the center to the full hue on the rim, which is exactly what
        // the per-vertex color blend draws.
        positions[0] = centerX;
        positions[1] = centerY;
        colors[0] = 0xFF000000 | Mth.hsvToRgb(0.0F, 0.0F, value);

        for (int i = 0; i <= WHEEL_SEGMENTS; i++) {
            float turn = (float) i / WHEEL_SEGMENTS;
            float angle = turn * Mth.TWO_PI;

            positions[i * 2 + 2] = centerX + radius * Mth.cos(angle);
            positions[i * 2 + 3] = centerY - radius * Mth.sin(angle);
            colors[i + 1] = 0xFF000000 | Mth.hsvToRgb(turn % 1.0F, 1.0F, value);
        }

        TriangleFanRenderState.fill(graphics, positions, colors);

        float angle = hue * Mth.TWO_PI;
        int markX = Math.round(centerX + radius * saturation * Mth.cos(angle));
        int markY = Math.round(centerY - radius * saturation * Mth.sin(angle));

        graphics.outline(markX - 2, markY - 2, 5, 5, value > 0.5F ? 0xFF000000 : 0xFFFFFFFF);
    }

    private static void drawCheckerboard(final GuiGraphicsExtractor graphics, final int left, final int top, final int right, final int bottom) {
        for (int y = top; y < bottom; y += CHECKER) {
            for (int x = left; x < right; x += CHECKER) {
                boolean light = ((x - left) / CHECKER + (y - top) / CHECKER) % 2 == 0;
                graphics.fill(x, y, Math.min(x + CHECKER, right), Math.min(y + CHECKER, bottom), light ? 0xFFC0C0C0 : 0xFF808080);
            }
        }
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(hexBox);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1 = AnchoredWidget.pixel(0);
    private Unit x2;
    private Unit y1 = AnchoredWidget.pixel(0);
    private Unit y2;
    private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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
