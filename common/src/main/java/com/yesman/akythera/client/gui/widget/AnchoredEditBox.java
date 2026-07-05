package com.yesman.akythera.client.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/// Inherits [EditBox] (vanilla widget)
public class AnchoredEditBox extends EditBox implements AnchoredWidget, DataBoundWidget<String> {
    public AnchoredEditBox(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        AnchoredWidget.VerticalAnchorType verticalAnchor,
        Callable<String> dataProvider,
        Consumer<String> onWidgetChanged,
        Component message
    ) {
        super(font, 0, 0, 0, 0, message);
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onValueChanged = onWidgetChanged;
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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

    /// *****************************************************************
    /// [DataBoundWidget] implementations                               *
    /// *****************************************************************
    private final Callable<String> dataProvider;
    private final Consumer<String> onValueChanged;

    @Override
    public Callable<String> getDataProvider() {
        return this.dataProvider;
    }

    @Override
    public Consumer<String> valueChangeCallback() {
        return this.onValueChanged;
    }

    @Override
    public Consumer<String> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        this.setValue("");
    }
}
