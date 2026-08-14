package cn.remix.ui.font;

import cn.remix.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;

public class MinecraftFont implements IMinecraft {

    public float getStringWidth(String text) {
        if (mc.textRenderer == null) return 0;
        return mc.textRenderer.getWidth(text);
    }

    public float getHeight() {
        if (mc.textRenderer == null) return 0;
        return mc.textRenderer.fontHeight;
    }

    public void drawStringWithShadow(DrawContext context, String text, float x, float y, int color) {
        if (mc.textRenderer == null) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.drawTextWithShadow(mc.textRenderer, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    public void drawStringOutline(DrawContext context, String text, float x, float y, int color) {
        if (mc.textRenderer == null) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    drawString(context, text, x + dx * 0.5f, y + dy * 0.5f, 0xFF000000);
                }
            }
        }

        drawString(context, text, x, y, color);
    }

    public void drawString(DrawContext context, String text, float x, float y, int color) {
        if (mc.textRenderer == null) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.drawText(mc.textRenderer, text, 0, 0, color, false);
        context.getMatrices().popMatrix();
    }
}