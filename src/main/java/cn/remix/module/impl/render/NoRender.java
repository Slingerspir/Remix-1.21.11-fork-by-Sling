package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import lombok.Getter;

@Getter
public final class NoRender extends Module {
    private final BoolValue disableEffects = new BoolValue("Disable Effects", true);
    private final BoolValue hidePotionIcons = new BoolValue("Hide Potion Icons", true);

    public NoRender() {
        super("NoRender", Category.Render);
    }
}
