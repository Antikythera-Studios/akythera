package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

public record BackgroundComponent(int color) implements Model3DViewerComponent {
    public BackgroundComponent(int r, int g, int b, int a) {
        this(ARGB.color(a, r, g, b));
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        int x = model3DViewer.getX(), y = model3DViewer.getY(), w = model3DViewer.getWidth(), h = model3DViewer.getHeight();
        graphics.fill(x, y, x + w, y + h, color);
    }

    @Override
    public int drawOrder() {
        return 0;
    }
}
