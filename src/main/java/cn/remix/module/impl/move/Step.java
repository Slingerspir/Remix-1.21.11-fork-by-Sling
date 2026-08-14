package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

public final class Step extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla");

    public Step() {
        super("Step", Category.Move);
    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.world == null) return;
        EntityAttributeInstance step = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);

        if (step == null) return;
        step.setBaseValue(0.6);
    }

    @EventTarget
    public void onMotionEvent(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;
        setSuffix(mode.getValue());

        if (event.isPre()) {
            EntityAttributeInstance step = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (step == null) return;

            if (mc.player.isOnGround()) {
                if (step.getBaseValue() != 1) {
                    step.setBaseValue(1);
                }
            } else {
                if (step.getBaseValue() != 0.6) {
                    step.setBaseValue(0.6);
                }
            }
        }
    }
}