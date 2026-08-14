package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class AntiBot extends Module {
    private final BoolValue entityID = new BoolValue("EntityID",  false);
    private final BoolValue sleep = new BoolValue("Sleep", false);
    private final BoolValue sentinel = new BoolValue("Sentinel", false);

    public AntiBot() {
        super("AntiBot", Category.Player);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(sentinel.getValue() ? "Sentinel" : "Custom");
    }

    public boolean isBot(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            if (sleep.getValue() && player.isSleeping()) {
                return true;
            }

            if (sentinel.getValue() && (entity.getWidth() <= 0.3f || entity.getHeight() <= 0.3f)) {
                return true;
            }

            return entityID.getValue() && (entity.getId() >= 1000000000 || entity.getId() <= -1);

        }

        return false;
    }
}