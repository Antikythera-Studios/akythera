package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.render.TriangleFanRenderState;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Draws one [WidgetImage] over its whole rect, the texture read whole. Takes no input.
@ScriptType
public class Image extends AbstractWidget implements AnchoredWidget {
    private WidgetImage image;

    public Image(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final WidgetImage image
    ) {
        super(0, 0, 0, 0, Component.empty());

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.image = image;
    }

    @ScriptFunction(pure = true)
    public WidgetImage getImage() {
        return image;
    }

    @ScriptFunction
    public void setImage(final WidgetImage image) {
        this.image = image;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        float left = getX();
        float top = getY();
        float right = getRight();
        float bottom = getBottom();

        TriangleFanRenderState.fill(graphics, image, getRectangle(), new float[] { left, top, left, bottom, right, bottom, right, top });
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
