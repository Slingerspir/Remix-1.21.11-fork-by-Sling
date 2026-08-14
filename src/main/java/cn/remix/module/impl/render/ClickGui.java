package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import org.lwjgl.glfw.GLFW;

public final class ClickGui extends Module {

    public ClickGui() {
        super("ClickGui", Category.Render);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        mc.setScreen(instance.getClickGuiScreen());
        toggle();
    }
}
