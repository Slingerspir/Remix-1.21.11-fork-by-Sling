package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

public final class TimeChanger extends Module {
    private final NumberValue time = new NumberValue("World Time", 8000, 0, 24000, 100);

    public TimeChanger() {
        super("TimeChanger", Category.Render);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.world == null) return;
        mc.world.setTime(time.getValue().longValue(), mc.world.getTime(), true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received) return;
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            event.setCancelled();
        }
    }
}
