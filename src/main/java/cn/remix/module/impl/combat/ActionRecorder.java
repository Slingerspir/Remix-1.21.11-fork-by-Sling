package cn.remix.module.impl.combat;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import lombok.Getter;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;

@Getter
public final class ActionRecorder extends Module {
    private final BoolValue logPackets = new BoolValue("Log Packets", true);
    private final BoolValue logHotbar = new BoolValue("Log Hotbar", true);
    private final BoolValue logTicks = new BoolValue("Log Ticks", true);

    private final Deque<String> buffer = new ArrayDeque<>();
    private long sessionStart;
    private int ticks;
    private int lastSlot = -1;

    public ActionRecorder() {
        super("ActionRecorder", Category.Combat);
    }

    @Override
    public void onEnable() {
        sessionStart = System.currentTimeMillis();
        ticks = 0;
        lastSlot = -1;
        buffer.clear();
        log("=== ActionRecorder session started ===");
    }

    @Override
    public void onDisable() {
        flush();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        ticks++;
        if (logTicks.getValue() && ticks % 20 == 0) {
            log("tick " + ticks + " hand=" + handName());
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;
        if (!logHotbar.getValue()) return;

        int slot = mc.player.getInventory().getSelectedSlot();
        if (slot != lastSlot) {
            log("hotbar -> slot " + slot + " (" + handName() + ")");
            lastSlot = slot;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        if (!logPackets.getValue()) return;
        if (event.getType() != PacketEvent.Type.Send) return;

        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            log("packet UpdateSelectedSlot -> " + packet.getSelectedSlot() + " (" + handName() + ")");
        } else if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
            log("packet UseItem hand=" + packet.getHand() + " (" + handName() + ")");
        } else if (event.getPacket() instanceof PlayerInteractEntityC2SPacket) {
            log("packet InteractEntity/Attack (" + handName() + ")");
        } else if (event.getPacket() instanceof PlayerActionC2SPacket packet) {
            log("packet PlayerAction action=" + packet.getAction() + " (" + handName() + ")");
        }
    }

    private String handName() {
        if (mc.player == null) return "none";
        return mc.player.getMainHandStack().getItem().toString();
    }

    private void log(String msg) {
        long ms = System.currentTimeMillis() - sessionStart;
        String line = String.format("%5dms [%s] %s", ms, handName(), msg);
        buffer.add(line);
        if (buffer.size() > 5000) {
            buffer.poll();
        }
        Client.logger.info("[ActionRecorder] " + line);
    }

    private void flush() {
        if (buffer.isEmpty()) return;
        try {
            File dir = new File(Client.name, "debug");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "action_recorder.log");
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                writer.println("=== session " + sessionStart + " ===");
                for (String line : buffer) {
                    writer.println(line);
                }
                writer.println("=== end (ticks=" + ticks + ") ===");
            }
        } catch (Exception ignored) {}
        buffer.clear();
    }
}
