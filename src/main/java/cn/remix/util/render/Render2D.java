package cn.remix.util.render;

import cn.remix.util.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

@UtilityClass
public final class Render2D implements IMinecraft {
    private static float globalAlpha = 1.0f;

    public void setGlobalAlpha(float alpha) {
        globalAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }
    public float getGlobalAlpha() {
        return globalAlpha;
    }
    public void drawRect(DrawContext context, float x, float y, float width, float height, int color) {
        drawGradient(context, x, y, width, height, color, color, false);
    }

    public void drawRoundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0) return;

        radius = Math.max(0.0f, Math.min(radius, Math.min(width, height) * 0.5f));
        if (radius <= 0.0f) {
            drawRect(context, x, y, width, height, color);
            return;
        }

        drawRect(context, x + radius, y, width - radius * 2.0f, height, color);                      // 垂直主块
        drawRect(context, x, y + radius, radius, height - radius * 2.0f, color);                    // 左翼
        drawRect(context, x + width - radius, y + radius, radius, height - radius * 2.0f, color);  // 右翼

        drawArc(context, x + radius, y + radius, radius, 180, 270, color);               // 左上角
        drawArc(context, x + width - radius, y + radius, radius, 270, 360, color);        // 右上角
        drawArc(context, x + width - radius, y + height - radius, radius, 0, 90, color);  // 右下角
        drawArc(context, x + radius, y + height - radius, radius, 90, 180, color);        // 左下角
    }

    public void drawArc(DrawContext context, float cx, float cy, float radius, float startAngle, float endAngle, int color) {
        int segments = 10;
        double step = Math.toRadians(endAngle - startAngle) / segments;
        double startRad = Math.toRadians(startAngle);

        for (int i = 0; i < segments; i++) {
            double a1 = startRad + i * step;
            double a2 = startRad + (i + 1) * step;

            float x1 = (float) (cx + Math.cos(a1) * radius);
            float y1 = (float) (cy + Math.sin(a1) * radius);
            float x2 = (float) (cx + Math.cos(a2) * radius);
            float y2 = (float) (cy + Math.sin(a2) * radius);

            drawTriangle(context, cx, cy, x1, y1, x2, y2, color);
        }
    }

    public void drawGradient(DrawContext context, float x, float y, float width, float height, int startColor, int endColor, boolean horizontal) {
        if (width <= 0 || height <= 0) return;

        context.state.addSimpleElement(new FloatQuadGuiElementRenderState(
                RenderPipelines.GUI, TextureSetup.empty(), new Matrix3x2f(context.getMatrices()),
                x, y, x + width, y + height, applyGlobalAlpha(startColor), applyGlobalAlpha(endColor), horizontal, context.scissorStack.peekLast()
        ));
    }

    public void drawTriangle(DrawContext context, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        context.state.addSimpleElement(new FloatTriangleGuiElementRenderState(
                RenderPipelines.GUI, TextureSetup.empty(), new Matrix3x2f(context.getMatrices()),
                x1, y1, x2, y2, x3, y3, applyGlobalAlpha(color), context.scissorStack.peekLast()
        ));
    }

    public void drawQuad(DrawContext context, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        context.state.addSimpleElement(new FloatFreeQuadGuiElementRenderState(
                RenderPipelines.GUI, TextureSetup.empty(), new Matrix3x2f(context.getMatrices()),
                x1, y1, x2, y2, x3, y3, x4, y4, applyGlobalAlpha(color), context.scissorStack.peekLast()
        ));
    }

    public void drawOutline(DrawContext context, float x, float y, float width, float height, float thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;

        drawRect(context, x, y, width, thickness, color);
        drawRect(context, x, y + height - thickness, width, thickness, color);
        drawRect(context, x, y + thickness, thickness, height - thickness - thickness, color);
        drawRect(context, x + width - thickness, y + thickness, thickness, height - thickness - thickness, color);
    }

    public static void beginScissor(DrawContext context, float x, float y, float width, float height) {
        context.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
    }

    public void endScissor(DrawContext context) {
        context.disableScissor();
    }

    public void drawItem(DrawContext context, ItemStack stack, float x, float y) {
        if (stack.isEmpty()) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    public void drawPlayerHead(DrawContext context, AbstractClientPlayerEntity player, float x, float y, float width, float height) {
        if (player == null) return;

        var skin = player.getSkin().body().texturePath();
        drawTexture(context, skin, x, y, width, height, 0.125f, 0.125f, 0.25f, 0.25f, -1);
        drawTexture(context, skin, x, y, width, height, 0.625f, 0.125f, 0.75f, 0.25f, -1);
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height) {
        drawTexture(context, texture, x, y, width, height, 0f, 0f, 1f, 1f, -1);
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        drawTexture(context, texture, x, y, width, height, 0f, 0f, 1f, 1f, color);
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, int color) {
        if (width <= 0 || height <= 0) return;

        var tex = mc.getTextureManager().getTexture(texture);
        var textureSetup = TextureSetup.of(tex.getGlTextureView(), tex.getSampler());
        context.state.addSimpleElement(new FloatQuadTexturedGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED, textureSetup, new Matrix3x2f(context.getMatrices()),
                x, y, x + width, y + height, u0, v0, u1, v1, applyGlobalAlpha(color), context.scissorStack.peekLast()
        ));
    }

    private int applyGlobalAlpha(int color) {
        if (globalAlpha >= 0.999f) {
            return color;
        }

        int alpha = Math.round(((color >>> 24) & 0xFF) * globalAlpha);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public void drawModel(DrawContext context, LivingEntity entity, float x, float y) {
        if (entity == null) return;

        int x1 = (int) (x + 5);
        int y1 = (int) (y + 5);
        int x2 = (int) (x + 40);
        int y2 = (int) (y + 40);
        InventoryScreen.drawEntity(context, x1, y1, x2, y2, 16, 0.0625F, 0, 0, entity);
    }

    public void drawPingIcon(DrawContext context, float x, float y, int ping) {
        Identifier icons = Identifier.ofVanilla("textures/gui/icons.png");

        int u;
        if (ping < 0) {
            u = 5;
        } else if (ping < 150) {
            u = 0;
        } else if (ping < 300) {
            u = 1;
        } else if (ping < 600) {
            u = 2;
        } else if (ping < 1000) {
            u = 3;
        } else {
            u = 4;
        }

        int textureX = u * 10;
        int textureY = 176;

        drawTexture(context, icons, x, y, 10, 8,
                textureX / 256f, textureY / 256f,
                (textureX + 10) / 256f, (textureY + 8) / 256f, -1);
    }

    private record FloatQuadTexturedGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private FloatQuadTexturedGuiElementRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color, @Nullable ScreenRect scissorArea) {
            this(pipeline, textureSetup, pose, x0, y0, x1, y1, u0, v0, u1, v1, color, scissorArea, createBounds(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            v.vertex(pose, x0, y0).texture(u0, v0).color(color);
            v.vertex(pose, x0, y1).texture(u0, v1).color(color);
            v.vertex(pose, x1, y1).texture(u1, v1).color(color);
            v.vertex(pose, x1, y0).texture(u1, v0).color(color);
        }

        private static @Nullable ScreenRect createBounds(float x0, float y0, float x1, float y1, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            ScreenRect rect = new ScreenRect(Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }

    private record FloatQuadGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x0, float y0, float x1, float y1, int col1, int col2, boolean horizontal,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private FloatQuadGuiElementRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x0, float y0, float x1, float y1, int col1, int col2, boolean horizontal, @Nullable ScreenRect scissorArea) {
            this(pipeline, textureSetup, pose, x0, y0, x1, y1, col1, col2, horizontal, scissorArea, createBounds(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            if (horizontal) {
                v.vertex(pose, x0, y0).color(col1);
                v.vertex(pose, x0, y1).color(col1);
                v.vertex(pose, x1, y1).color(col2);
                v.vertex(pose, x1, y0).color(col2);
            } else {
                v.vertex(pose, x0, y0).color(col1);
                v.vertex(pose, x0, y1).color(col2);
                v.vertex(pose, x1, y1).color(col2);
                v.vertex(pose, x1, y0).color(col1);
            }
        }

        private static @Nullable ScreenRect createBounds(float x0, float y0, float x1, float y1, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            ScreenRect rect = new ScreenRect(Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }

    private record FloatTriangleGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x1, float y1, float x2, float y2, float x3, float y3, int color,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private FloatTriangleGuiElementRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x1, float y1, float x2, float y2, float x3, float y3, int color, @Nullable ScreenRect scissorArea) {
            this(pipeline, textureSetup, pose, x1, y1, x2, y2, x3, y3, color, scissorArea, createBounds(x1, y1, x2, y2, x3, y3, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            v.vertex(pose, x1, y1).color(color);
            v.vertex(pose, x2, y2).color(color);
            v.vertex(pose, x3, y3).color(color);
            v.vertex(pose, x3, y3).color(color);
        }

        private static @Nullable ScreenRect createBounds(float x1, float y1, float x2, float y2, float x3, float y3, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            float minX = Math.min(x1, Math.min(x2, x3));
            float minY = Math.min(y1, Math.min(y2, y3));
            float maxX = Math.max(x1, Math.max(x2, x3));
            float maxY = Math.max(y1, Math.max(y2, y3));
            ScreenRect rect = new ScreenRect(Math.round(minX), Math.round(minY), Math.round(maxX - minX), Math.round(maxY - minY)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }

    private record FloatFreeQuadGuiElementRenderState(
            RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
            float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color,
            @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState {

        private FloatFreeQuadGuiElementRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color, @Nullable ScreenRect scissorArea) {
            this(pipeline, textureSetup, pose, x1, y1, x2, y2, x3, y3, x4, y4, color, scissorArea, createBounds(x1, y1, x2, y2, x3, y3, x4, y4, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer v) {
            v.vertex(pose, x1, y1).color(color);
            v.vertex(pose, x2, y2).color(color);
            v.vertex(pose, x3, y3).color(color);
            v.vertex(pose, x4, y4).color(color);
        }

        private static @Nullable ScreenRect createBounds(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
            float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
            float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
            float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
            ScreenRect rect = new ScreenRect(Math.round(minX), Math.round(minY), Math.round(maxX - minX), Math.round(maxY - minY)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(rect) : rect;
        }
    }
}