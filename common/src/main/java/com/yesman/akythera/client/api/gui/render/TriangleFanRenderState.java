package com.yesman.akythera.client.api.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/// A convex polygon drawn as a triangle fan around its first vertex, each vertex in its own color and, with a
/// texture, at its own UV. The GUI pipelines draw quads, so each triangle is a quad repeating its last vertex, and
/// they cull back faces, so each triangle is wound the way a vanilla blit is, whichever way `positions` turns.
///
/// @param positions `x, y` pairs, in the space `pose` maps to the screen
/// @param uvs       `u, v` pairs matching `positions`, or null without a texture
/// @param colors    one packed ARGB color per vertex
public record TriangleFanRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2fc pose,
    float[] positions,
    float @Nullable [] uvs,
    int[] colors,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    public TriangleFanRenderState {
        if (bounds == null) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

            for (int i = 0; i < positions.length; i += 2) {
                minX = Math.min(minX, positions[i]);
                maxX = Math.max(maxX, positions[i]);
                minY = Math.min(minY, positions[i + 1]);
                maxY = Math.max(maxY, positions[i + 1]);
            }

            int left = (int) Math.floor(minX);
            int top = (int) Math.floor(minY);
            ScreenRectangle area = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top)
                .transformMaxBounds(pose);

            bounds = scissorArea != null ? scissorArea.intersection(area) : area;
        }
    }

    /// Draws the polygon `positions` in per-vertex `colors`.
    public static void fill(final GuiGraphicsExtractor graphics, final float[] positions, final int[] colors) {
        if (positions.length < 6) return;

        graphics.guiRenderState.addGuiElement(new TriangleFanRenderState(
            RenderPipelines.GUI,
            TextureSetup.noTexture(),
            new Matrix3x2f(graphics.pose()),
            positions,
            null,
            colors,
            graphics.scissorStack.peek(),
            null
        ));
    }

    /// Draws the polygon `positions` cut out of `image` laid over `imageRect`: the texture where it lies,
    /// tinted by the image color, or the plain color while the image has no texture.
    public static void fill(final GuiGraphicsExtractor graphics, final WidgetImage image, final ScreenRectangle imageRect, final float[] positions) {
        if (positions.length < 6) return;

        int color = image.color().packARGB();
        int[] colors = new int[positions.length / 2];
        Arrays.fill(colors, color);

        if (image.texture() == null) {
            fill(graphics, positions, colors);
            return;
        }

        float[] uvs = new float[positions.length];

        for (int i = 0; i < positions.length; i += 2) {
            uvs[i] = (positions[i] - imageRect.left()) / Math.max(1, imageRect.width());
            uvs[i + 1] = (positions[i + 1] - imageRect.top()) / Math.max(1, imageRect.height());
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(image.texture());

        graphics.guiRenderState.addGuiElement(new TriangleFanRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
            new Matrix3x2f(graphics.pose()),
            positions,
            uvs,
            colors,
            graphics.scissorStack.peek(),
            null
        ));
    }

    @Override
    public void buildVertices(final VertexConsumer vertexConsumer) {
        int count = positions.length / 2;

        for (int i = 1; i < count - 1; i++) {
            float ax = positions[i * 2] - positions[0];
            float ay = positions[i * 2 + 1] - positions[1];
            float bx = positions[i * 2 + 2] - positions[0];
            float by = positions[i * 2 + 3] - positions[1];

            // A vanilla blit (top left, bottom left, bottom right) turns with a negative cross product.
            boolean flipped = ax * by - ay * bx > 0.0F;
            int second = flipped ? i + 1 : i;
            int third = flipped ? i : i + 1;

            vertex(vertexConsumer, 0);
            vertex(vertexConsumer, second);
            vertex(vertexConsumer, third);
            vertex(vertexConsumer, third);
        }
    }

    private void vertex(final VertexConsumer vertexConsumer, final int index) {
        VertexConsumer vertex = vertexConsumer.addVertexWith2DPose(pose, positions[index * 2], positions[index * 2 + 1]);

        if (uvs != null) vertex.setUv(uvs[index * 2], uvs[index * 2 + 1]);

        vertex.setColor(colors[index]);
    }
}
