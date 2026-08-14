package cn.remix.ui.screen.util;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class AdaptiveButton implements IMinecraft {
    private final EasingAnimation animation = new EasingAnimation(Easing.EASE_OUT_EXPO, 350);
    private float x, y, width, height;
    private final String text;
    private final Runnable action;

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

    public void render(DrawContext context, int mouseX, int mouseY) {
        animation.run(isHovered(mouseX, mouseY) ? 1 : 0);
        TrueTypeFont font = instance.getFontManager().getFont(19);

        float textWidth = font.getStringWidth(text);
        float textX = x + (width - textWidth) / 2f;
        float textY = y + (height - font.getHeight()) / 2f;
        int textColor = ColorUtil.interpolate(new Color(200, 200, 200).getRGB(), new Color(255, 255, 255).getRGB(), animation.getValue().floatValue());

        Render2D.drawRect(context, x, y, width, height, new Color(0, 0, 0, 80).getRGB());
        font.drawString(context, text, textX, textY, textColor, false);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void onClick() {
        action.run();
    }
}