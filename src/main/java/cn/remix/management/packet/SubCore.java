package cn.remix.management.packet;

import cn.remix.event.impl.PacketEvent;
import cn.remix.util.IMinecraft;
import cn.remix.util.network.PacketUtil;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class SubCore implements IMinecraft {
    public final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    private final List<Object> holders = new ArrayList<>();
    public boolean active;

    public void start() {
        if (!active) {
            active = true;
        }
    }

    public void start(Object holder) {
        if (!holders.contains(holder)) {
            holders.add(holder);
        }
        if (!active) {
            active = true;
        }
    }

    public void release(boolean clear) {
        if (!packets.isEmpty()) {
            packets.forEach(packet -> {
                if (mc.getNetworkHandler() != null && mc.player != null) {
                    onRelease(packet);
                }
            });

            if (clear) {
                packets.clear();
            }
        }
    }

    public void dispatch(boolean releasePackets) {
        if (releasePackets) {
            release(true);
        }
        holders.clear();
        active = false;
    }

    public void dispatch(Object holder, boolean releasePackets) {
        holders.remove(holder);

        if (holders.isEmpty()) {
            if (releasePackets) {
                release(true);
            }
            active = false;
        }
    }

    public void dispatch(Object holder) {
        dispatch(holder, true);
    }

    public void dispatch() {
        dispatch(true);
    }

    public void clear() {
        packets.clear();
        holders.clear();
        active = false;
    }

    public void handle(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (PacketUtil.getPackets().contains(packet)) {
            PacketUtil.getPackets().remove(packet);
            return;
        }

        if (shouldIgnore(packet)) return;

        if (active) {
            event.setCancelled(true);
            packets.add(packet);
        }
    }

    protected abstract void onRelease(Packet<?> packet);
    protected abstract boolean shouldIgnore(Packet<?> packet);
}