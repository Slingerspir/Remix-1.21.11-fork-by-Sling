package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.util.network.PacketUtil;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class Criticals extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Packet", "No Ground", "NCP", "Strict", "Sentinel", "Packet");

    public Criticals() {
        super("Criticals", Category.Combat);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.player == null) return;

        switch (mode.getValue()) {
            case "NCP" -> {
                sendCritPacket(0.000000271875, false);
                sendCritPacket(0, false);
            }

            case "Packet" -> {
                sendCritPacket(0.0625, false);
                sendCritPacket(0, false);
            }

            case "Strict" -> {
                sendCritPacket(0.062600301692775, false);
                sendCritPacket(0.07260029960661, false);
                sendCritPacket(0., false);
                sendCritPacket(0., false);
            }

            case "Sentinel" -> {
                if (!mc.player.isOnGround()) {
                    sendCritPacket(-0.000001, true);
                }
            }
        }
    }

    @EventTarget
    public void onAttack(MotionEvent event) {
        if (mc.player == null) return;

        setSuffix(mode.getValue());
        event.setOnGround(false);
    }

    private void sendCritPacket(double offset, boolean full) {
        if (mc.player == null) return;

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean h = mc.player.horizontalCollision;
        if (!full) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + offset, z, false, h));
        } else {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, mc.player.getY() + offset, z, ((ClientPlayerEntityAccessor) mc.player).getLastYaw(), ((ClientPlayerEntityAccessor) mc.player).getLastPitch(), false, h));
        }
    }
}