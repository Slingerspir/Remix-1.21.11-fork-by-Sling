package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import lombok.Getter;


@Getter
public final class LiquidGlass extends Module {
    private final BoolValue blur = new BoolValue("Blur", true);
    private final NumberValue opacity = new NumberValue("Opacity", 0.22f, 0.05f, 0.6f, 0.01f);
    private final NumberValue radius = new NumberValue("Radius", 8.0f, 2.0f, 24.0f, 0.5f);
    private final BoolValue border = new BoolValue("Border", true);

    public LiquidGlass() {
        super("LiquidGlass", Category.Render);
    }

    public static boolean isBlurEnabled() {
        LiquidGlass glass = cn.remix.util.IMinecraft.instance.getModuleManager().getModule(LiquidGlass.class);
        return glass != null && glass.isEnabled() && glass.blur.getValue();
    }
}
