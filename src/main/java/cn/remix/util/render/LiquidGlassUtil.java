package cn.remix.util.render;

import cn.remix.module.impl.render.LiquidGlass;
import cn.remix.module.impl.render.LiquidGlow;
import cn.remix.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;


public final class LiquidGlassUtil implements IMinecraft {

    private static final long SHEEN_PERIOD = 5200L;

    private LiquidGlassUtil() {
    }

    public static boolean isGlass() {
        LiquidGlass glass = instance.getModuleManager().getModule(LiquidGlass.class);
        return glass != null && glass.isEnabled();
    }

    public static void drawGlass(DrawContext context, float x, float y, float width, float height) {
        if (!isGlass() || width <= 0 || height <= 0) return;

        LiquidGlass glass = instance.getModuleManager().getModule(LiquidGlass.class);
        float radius = glass.getRadius().getValue().floatValue();
        float opacity = glass.getOpacity().getValue().floatValue();
        long time = System.currentTimeMillis();

        
        if (LiquidGlow.isGlowEnabled()) {
            drawGlow(context, x, y, width, height, radius, time);
        }

        
        int top = new Color(255, 255, 255, (int) (255 * Math.min(1.0f, opacity * 1.35f))).getRGB();
        int bottom = new Color(255, 255, 255, (int) (255 * opacity * 0.55f)).getRGB();
        drawRoundedGradient(context, x, y, width, height, radius, top, bottom);

        
        Render2D.drawRect(context, x + radius, y + 1, width - radius * 2, 1,
                new Color(255, 255, 255, (int) (150 * opacity)).getRGB());
        
        Render2D.drawRect(context, x + 1, y + radius, 1, height - radius * 2,
                new Color(255, 255, 255, (int) (45 * opacity)).getRGB());
        Render2D.drawRect(context, x + width - 2, y + radius, 1, height - radius * 2,
                new Color(255, 255, 255, (int) (45 * opacity)).getRGB());

        
        float progress = (time % SHEEN_PERIOD) / (float) SHEEN_PERIOD;
        float sheenY = y + height * progress;
        float sheenHeight = Math.max(10.0f, height * 0.18f);
        int sheenTop = new Color(255, 255, 255, 0).getRGB();
        int sheenMid = new Color(255, 255, 255, (int) (70 * opacity)).getRGB();
        Render2D.drawGradient(context, x + radius, sheenY - sheenHeight / 2, width - radius * 2, sheenHeight / 2,
                sheenTop, sheenMid, false);
        Render2D.drawGradient(context, x + radius, sheenY, width - radius * 2, sheenHeight / 2,
                sheenMid, sheenTop, false);

        
        Render2D.drawRect(context, x + radius, y + height - 2, width - radius * 2, 2,
                new Color(0, 0, 0, (int) (50 * opacity)).getRGB());

        
        if (glass.getBorder().getValue()) {
            int border = new Color(255, 255, 255, (int) (140 * opacity)).getRGB();
            Render2D.drawRoundedRect(context, x, y, width, height, radius, border);
            drawRoundedGradient(context, x + 1, y + 1, width - 2, height - 2, Math.max(0.0f, radius - 1),
                    new Color(255, 255, 255, (int) (255 * Math.min(1.0f, opacity * 1.35f))).getRGB(),
                    new Color(255, 255, 255, (int) (255 * opacity * 0.55f)).getRGB());
        }
    }

    public static void drawGlow(DrawContext context, float x, float y, float width, float height, float radius, long time) {
        LiquidGlow glow = instance.getModuleManager().getModule(LiquidGlow.class);
        if (glow == null || !glow.isGlowEnabled()) return;

        float strength = glow.getStrength().getValue().floatValue();
        float opacity = glow.getOpacity().getValue().floatValue();
        Color color = glow.getColor().getValue();
        int rgb = color.getRGB() & 0x00FFFFFF;

        
        float pulse = 0.82f + 0.18f * (float) Math.sin(time / 320.0);
        int layers = 8;
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            float expand = strength * t;
            int alpha = (int) (255 * opacity * pulse * t * 0.62f);
            if (alpha <= 0) continue;
            Render2D.drawRoundedRect(context,
                    x - expand, y - expand,
                    width + expand * 2, height + expand * 2,
                    radius + expand, rgb | (alpha << 24));
        }
    }

    
    public static void drawRoundedGradient(DrawContext context, float x, float y, float width, float height,
                                           float radius, int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) return;
        radius = Math.max(0.0f, Math.min(radius, Math.min(width, height) * 0.5f));

        int mid = mix(topColor, bottomColor, 0.5f);

        
        Render2D.drawGradient(context, x + radius, y, width - radius * 2, height, topColor, bottomColor, false);
        
        Render2D.drawRect(context, x, y + radius, radius, height - radius * 2, mid);
        Render2D.drawRect(context, x + width - radius, y + radius, radius, height - radius * 2, mid);
        
        Render2D.drawGradient(context, x + radius, y, width - radius * 2, radius, topColor, mid, false);
        Render2D.drawGradient(context, x + radius, y + height - radius, width - radius * 2, radius, mid, bottomColor, false);
        
        Render2D.drawArc(context, x + radius, y + radius, radius, 180, 270, mid);
        Render2D.drawArc(context, x + width - radius, y + radius, radius, 270, 360, mid);
        Render2D.drawArc(context, x + width - radius, y + height - radius, radius, 0, 90, mid);
        Render2D.drawArc(context, x + radius, y + height - radius, radius, 90, 180, mid);
    }

    private static int mix(int colorA, int colorB, float t) {
        int a1 = (colorA >>> 24) & 0xFF, r1 = (colorA >> 16) & 0xFF, g1 = (colorA >> 8) & 0xFF, b1 = colorA & 0xFF;
        int a2 = (colorB >>> 24) & 0xFF, r2 = (colorB >> 16) & 0xFF, g2 = (colorB >> 8) & 0xFF, b2 = colorB & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
