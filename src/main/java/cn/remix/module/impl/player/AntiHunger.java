package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class AntiHunger extends Module {
    private final BoolValue ground = new BoolValue("Cancel Ground", true);
    private final BoolValue sprint = new BoolValue("Cancel Sprint", true);

    public AntiHunger() {
        super("AntiHunger", Category.Player);
    }

    @EventTarget
    public void onPacketSend(PacketEvent e) {
        if (e.getPacket() instanceof PlayerMoveC2SPacket pac && ground.getValue()) {
            ((PlayerMoveC2SPacketAccessor) pac).setOnGround(false);
        }

        if (e.getPacket() instanceof ClientCommandC2SPacket pac && sprint.getValue()) {
            if (pac.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                e.setCancelled();
            }
        }
    }
}
