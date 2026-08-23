package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.StringValue;
import injection.accessor.MinecraftClientAccessor;
import net.minecraft.client.util.Window;

public final class Title extends Module {
    private final StringValue text = new StringValue("Text", "Remix Client Fork By Sling");

    public Title() {
        super("Title", Category.Render);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        apply();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        
        
        apply();
    }

    @Override
    public void onDisable() {
        Window window = mc.getWindow();
        if (window != null) {
            window.setTitle(((MinecraftClientAccessor) mc).invokeGetWindowTitle());
        }
    }

    public void apply() {
        Window window = mc.getWindow();
        if (window == null || !isEnabled()) {
            return;
        }
        window.setTitle(text.getValue());
    }
}
