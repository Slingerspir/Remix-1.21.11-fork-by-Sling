package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import net.minecraft.entity.effect.StatusEffects;

public final class AntiNausea extends Module {
    public AntiNausea() {
        super("AntiNausea", Category.Render);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        if (mc.player.hasStatusEffect(StatusEffects.NAUSEA)) {
            mc.player.removeStatusEffect(StatusEffects.NAUSEA);
        }
    }
}
