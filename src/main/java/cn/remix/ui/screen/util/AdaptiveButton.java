package cn.remix.ui.screen.util;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class AdaptiveButton implements IMinecraft {
    private float x, y, width, height;
    private final String text;
    private final Runnable action;

    private float hoverProgress = 0.0f;

    public AdaptiveButton(String text, Runnable action) {
        this.text = text;
        this.action = action;
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public void setY(float y) { this.y = y; }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered(mouseX, mouseY);
        float targetHover = hovered ? 1.0f : 0.0f;
        hoverProgress += (targetHover - hoverProgress) * Math.min(1.0f, 0.12f * delta);

        TrueTypeFont font = instance.getFontManager().getFont(18);

        int bgColor = ColorUtil.interpolate(
                new Color(255, 255, 255, 15).getRGB(),
                new Color(255, 255, 255, 40).getRGB(),
                hoverProgress
        );
        Render2D.drawRect(context, x, y, width, height, bgColor);

        int borderColor = ColorUtil.interpolate(
                new Color(255, 255, 255, 30).getRGB(),
                new Color(255, 255, 255, 80).getRGB(),
                hoverProgress
        );
        Render2D.drawOutline(context, x, y, width, height, 1.0f, borderColor);

        int textColor = ColorUtil.interpolate(
                new Color(180, 180, 180, 200).getRGB(),
                new Color(255, 255, 255, 255).getRGB(),
                hoverProgress
        );

        float textX = x + (width - font.getStringWidth(text)) / 2f;
        float textY = y + (height - font.getHeight()) / 2f + 1f;

        font.drawString(context, text, textX, textY, textColor, false);
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        render(context, mouseX, mouseY, 1.0f);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void onClick() {
        if (action != null) {
            action.run();
        }
    }
}