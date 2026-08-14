package cn.remix.management.packet.impl;

import cn.remix.management.packet.SubCore;
import cn.remix.util.network.PacketUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;

public final class Blink extends SubCore {

    @Override
    protected void onRelease(Packet<?> packet) {
        PacketUtil.sendPacketNoEvent(packet);
    }

    @Override
    protected boolean shouldIgnore(Packet<?> packet) {
        return packet instanceof KeepAliveC2SPacket
                || packet instanceof CommonPongC2SPacket
                || packet instanceof ChatMessageC2SPacket
                || packet instanceof CommandExecutionC2SPacket;
    }
}