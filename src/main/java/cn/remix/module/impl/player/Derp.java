package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import lombok.Getter;

@Getter
public final class Derp extends Module {
    private final NumberValue spinSpeed = new NumberValue("Spin Speed", 30.0, -40.0, 40.0, 1.0);
    private final NumberValue pitch = new NumberValue("Pitch", 30.0, -40.0, 40.0, 1.0);
    private final BoolValue priority = new BoolValue("Priority", false);

    private float spinYaw;
    private float[] rotations;

    public Derp() {
        super("Derp", Category.Player);
    }

    @Override
    public void onDisable() {
        spinYaw = 0.0f;
        rotations = null;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) {
            rotations = null;
            return;
        }

        if (spinYaw >= 360.0f || spinYaw <= -360.0f) {
            spinYaw = 0.0f;
        }

        spinYaw += spinSpeed.getValue();
        rotations = new float[]{spinYaw, pitch.getValue()};
    }
}
