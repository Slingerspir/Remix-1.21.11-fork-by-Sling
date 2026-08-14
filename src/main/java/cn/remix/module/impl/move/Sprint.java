package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.world.Scaffold;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.Move);
        setEnabled(true);
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        mc.options.sprintKey.setPressed(false);
        mc.player.setSprinting(false);
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        Scaffold scaffold = getModule(Scaffold.class);
        if (scaffold.isEnabled() && !scaffold.getSprint().getValue()) {
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
            return;
        }

        if (mc.player.getHungerManager().getFoodLevel() > 6 && mc.player.forwardSpeed > 0 && !mc.player.horizontalCollision) {
            mc.options.sprintKey.setPressed(true);
        }
    }
}