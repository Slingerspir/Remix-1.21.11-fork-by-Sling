package cn.remix.util.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

@UtilityClass
public final class BlurUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public void drawBlur(DrawContext context, float x, float y, float width, float height, float radius) {
        if (width <= 0 || height <= 0 || radius <= 0) return;

        Render2D.beginScissor(context, x, y, width, height);

        int blurColor = applyAlpha(0x80000000, Render2D.getGlobalAlpha());
        Render2D.drawRect(context, x, y, width, height, blurColor);

        context.disableScissor();
    }

    public void drawSoftGlow(DrawContext context, float x, float y, float width, float height, float blurRadius, int innerColor) {
        if (width <= 0 || height <= 0 || blurRadius <= 0) return;

        int transparentColor = innerColor & 0x00FFFFFF;

        Render2D.drawGradient(context, x, y - blurRadius, width, blurRadius, transparentColor, innerColor, false);
        Render2D.drawGradient(context, x, y + height, width, blurRadius, innerColor, transparentColor, false);
        Render2D.drawGradient(context, x - blurRadius, y, blurRadius, height, transparentColor, innerColor, true);
        Render2D.drawGradient(context, x + width, y, blurRadius, height, innerColor, transparentColor, true);
    }

    public void drawBlurredText(DrawContext context, String text, float x, float y, int color, float blurRadius) {
        if (text == null || text.isEmpty()) return;

        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        int steps = Math.max(4, (int) (blurRadius * 2));
        for (int i = 0; i < steps; i++) {
            double angle = (i * 2 * Math.PI) / steps;
            float offsetX = (float) (Math.cos(angle) * blurRadius * 0.6f);
            float offsetY = (float) (Math.sin(angle) * blurRadius * 0.6f);

            int blurAlpha = (int) (alpha * 0.15f);
            int sampleColor = (blurAlpha << 24) | rgb;

            context.drawText(mc.textRenderer, text, (int) (x + offsetX), (int) (y + offsetY), sampleColor, false);
        }

        context.drawText(mc.textRenderer, text, (int) x, (int) y, color, false);
    }

    private static int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}