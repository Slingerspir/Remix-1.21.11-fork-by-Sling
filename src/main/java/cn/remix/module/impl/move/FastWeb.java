package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.CobwebEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.MovementUtil;

public class FastWeb extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Motion");
    private final NumberValue motion = new NumberValue("Motion", 0.6f,0.1f, 1, 0.1f, () -> mode.is("Motion"));

    public FastWeb() {
        super("FastWeb", Category.Move);
    }

    @EventTarget
    public void onCobweb(CobwebEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Vanilla")) {
            event.setCancelled(true);
        } else {
            MovementUtil.strafe(mode.is("GrimAC") ? 0.6 : motion.getValue());

            if (mc.options.jumpKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x, 1, mc.player.getVelocity().z);
            } else if (mc.options.sneakKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x, -1, mc.player.getVelocity().z);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
    }
}