package cn.remix.module.impl.move;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.TeleportUtil;

public final class VClip extends Module {
    private final NumberValue verticalDistance = new NumberValue("Vertical Distance", 8, 1, 256, 1);

    public VClip() {
        super("VClip", Category.Move);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            TeleportUtil.teleportDirect(
                    mc.player.getX(),
                    mc.player.getY() + verticalDistance.getValue(),
                    mc.player.getZ()
            );
        }

        setEnabled(false);
    }
}
