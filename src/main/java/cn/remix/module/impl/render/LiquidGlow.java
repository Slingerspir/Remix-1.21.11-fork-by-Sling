package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.IMinecraft;
import lombok.Getter;

import java.awt.*;


@Getter
public final class LiquidGlow extends Module {
    private final NumberValue strength = new NumberValue("Strength", 12.0f, 0.0f, 32.0f, 0.5f);
    private final NumberValue opacity = new NumberValue("Opacity", 0.65f, 0.05f, 1.0f, 0.05f);
    private final ColorValue color = new ColorValue("Color", new Color(160, 215, 255));

    public LiquidGlow() {
        super("LiquidGlow", Category.Render);
    }

    public static boolean isGlowEnabled() {
        LiquidGlow glow = IMinecraft.instance.getModuleManager().getModule(LiquidGlow.class);
        return glow != null && glow.isEnabled();
    }
}
