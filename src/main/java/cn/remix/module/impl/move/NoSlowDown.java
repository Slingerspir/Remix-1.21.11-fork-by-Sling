package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.SlowEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;

public class NoSlowDown extends Module {
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);

    public NoSlowDown() {
        super("NoSlowDown", Category.Move);
    }

    @EventTarget
    public void onSlow(SlowEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isUsingItem() && !mc.player.getActiveItem().isEmpty()) {
            event.setCancelled(true);

            if (keepSprint.getValue()) {
                mc.player.setSprinting(true);
            }
        }
    }
}