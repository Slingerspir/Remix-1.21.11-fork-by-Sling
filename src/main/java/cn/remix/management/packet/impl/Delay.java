package cn.remix.management.packet.impl;

import cn.remix.management.packet.SubCore;
import cn.remix.util.network.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.*;

public final class Delay extends SubCore {

    @Override
    protected void onRelease(Packet<?> packet) {
        PacketUtil.receivePacketNoEvent(packet);
    }

    @Override
    protected boolean shouldIgnore(Packet<?> packet) {
        if (packet instanceof DisconnectS2CPacket
                || packet instanceof GameJoinS2CPacket
                || packet instanceof PlayerRespawnS2CPacket) {
            clear();
            return true;
        }

        if (packet instanceof KeepAliveS2CPacket) {
            return true;
        }

        if (packet instanceof EntityStatusS2CPacket statusPacket) {
            if (mc.world != null) {
                Entity entity = statusPacket.getEntity(mc.world);
                if (entity != null && (!entity.equals(mc.player) || statusPacket.getStatus() != 2)) {
                    return true;
                }
            }
        }

        return packet instanceof GameMessageS2CPacket
                || packet instanceof ChatMessageS2CPacket
                || packet instanceof PlaySoundS2CPacket
                || packet instanceof ScoreboardDisplayS2CPacket
                || packet instanceof ScoreboardObjectiveUpdateS2CPacket
                || packet instanceof ScoreboardScoreUpdateS2CPacket
                || packet instanceof TeamS2CPacket
                || packet instanceof ChatSuggestionsS2CPacket
                || packet instanceof ChunkDataS2CPacket
                || packet instanceof UnloadChunkS2CPacket
                || packet instanceof ChunkLoadDistanceS2CPacket
                || packet instanceof PlayerPositionLookS2CPacket;
    }
}