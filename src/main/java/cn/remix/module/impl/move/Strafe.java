package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.util.player.MovementUtil;

public class Strafe extends Module {

    public Strafe() {
        super("Strafe", Category.Move);
    }

    @EventTarget
    public void onUpdate(MotionEvent event) {
        if (event.isPre()) {
            MovementUtil.strafe();
        }
    }
}