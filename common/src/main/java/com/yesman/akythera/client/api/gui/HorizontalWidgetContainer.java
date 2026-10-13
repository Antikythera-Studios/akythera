package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.gui.navigation.ScreenAxis;

/// A [LinearWidgetContainer] laying its children out left to right.
@ScriptType
public class HorizontalWidgetContainer extends LinearWidgetContainer {
    public HorizontalWidgetContainer(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final int padding
    ) {
        super(ScreenAxis.HORIZONTAL, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, padding);
    }
}
