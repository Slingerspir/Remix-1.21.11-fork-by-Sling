package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.Hand;

public final class AntiFireball extends Module {
    private final BoolValue protectSelf = new BoolValue("Protect Self", true);
    private final NumberValue range = new NumberValue("Range", 4, 2, 8, 0.5f);

    private final TimerUtil attackTimer = new TimerUtil();

    public AntiFireball() {
        super("AntiFireball", Category.Misc);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        for (FireballEntity fireball : mc.world.getEntitiesByClass(FireballEntity.class, mc.player.getBoundingBox().expand(range.getValue(), range.getValue(), range.getValue()), e -> true)) {
            if (fireball == null || !fireball.isAlive()) continue;
            if (protectSelf.getValue() && fireball.getOwner() == mc.player) continue;
            if (mc.player.squaredDistanceTo(fireball) > range.getValue() * range.getValue()) continue;
            if (!attackTimer.hasTimeElapsed(200)) continue;

            mc.interactionManager.attackEntity(mc.player, fireball);
            mc.player.swingHand(Hand.MAIN_HAND);
            attackTimer.reset();
        }
    }
}
