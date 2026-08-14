package cn.remix.util.render;

import cn.remix.module.impl.render.HUD;
import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;

import java.awt.*;

@UtilityClass
public class ColorUtil implements IMinecraft {

    public int applyAlpha(int rgb, double alpha) {
        int alphaInt = (int) Math.clamp(alpha * 255, 0, 255);
        return (alphaInt << 24) | (rgb & 0xFFFFFF);
    }

    public int applyAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public Color applyAlpha(Color color, float alpha) {
        alpha = Math.min(1.0f, Math.max(0.0f, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha));
    }

    public int interpolate(int startColor, int endColor, float ratio) {
        float inv = 1f - ratio;
        int a = (int) ((startColor >> 24 & 0xFF) * inv + (endColor >> 24 & 0xFF) * ratio);
        int r = (int) ((startColor >> 16 & 0xFF) * inv + (endColor >> 16 & 0xFF) * ratio);
        int g = (int) ((startColor >> 8 & 0xFF) * inv + (endColor >> 8 & 0xFF) * ratio);
        int b = (int) ((startColor & 0xFF) * inv + (endColor & 0xFF) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public int getCustom(int alpha) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        return (alpha << 24) | (hud.getMainColor().getValue().getRGB() & 0xFFFFFF);
    }

    public int getFade(int counter, int alpha) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        int color1 = hud.getMainColor().getValue().getRGB();
        int color2 = hud.getSecondColor().getValue().getRGB();
        return getFade(color1, color2, counter, alpha);
    }

    public int getFade(int firstColor, int secondColor, int counter, int alpha) {
        long time = 2000L;
        long now = System.currentTimeMillis() - (counter * 110L);
        boolean isFirstPhase = (now % (time * 2L)) < time;
        int start = isFirstPhase ? firstColor : secondColor;
        int end = isFirstPhase ? secondColor : firstColor;
        float ratio = (float) (now % time) / (float) time;
        int res = interpolate(start, end, ratio);
        return (alpha << 24) | (res & 0xFFFFFF);
    }

    public int getRainbow(int counter, int alpha) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        double state = Math.ceil(System.currentTimeMillis() - (long) counter * 110) / 11.0 % 360;
        int rgb = Color.HSBtoRGB((float) (state / 360.0), hud.getMainColor().getSaturation(), hud.getMainColor().getBrightness());
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}