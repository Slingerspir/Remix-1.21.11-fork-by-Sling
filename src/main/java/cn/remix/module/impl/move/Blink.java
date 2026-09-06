package cn.remix.module.impl.move;

import cn.remix.module.Category;
import cn.remix.module.Module;

public final class Blink extends Module {
    public Blink() {
        super("Blink", Category.Move);
    }

    @Override
    public void onEnable() {
        instance.getPacketManager().getBlink().start(this);
    }

    @Override
    public void onDisable() {
        instance.getPacketManager().getBlink().dispatch(this, true);
    }
}
