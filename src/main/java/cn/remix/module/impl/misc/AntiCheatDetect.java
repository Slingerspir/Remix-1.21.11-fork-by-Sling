package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.util.Util;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class AntiCheatDetect extends Module {

    private String detectedAntiCheat = "Unknown";
    private final Map<String, Integer> patterns = new HashMap<>();
    private int packetCount = 0;
    private boolean detected = false;

    public AntiCheatDetect() {
        super("AntiCheatDetect", Category.Misc);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        detectedAntiCheat = "Unknown";
        patterns.clear();
        packetCount = 0;
        detected = false;
        Util.log("[AntiCheatDetect] Started analyzing server...");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (!detectedAntiCheat.equals("Unknown")) {
            Util.log("[AntiCheatDetect] Detected: " + detectedAntiCheat);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Received) return;

        Packet<?> packet = event.getPacket();
        String packetName = packet.getClass().getSimpleName();

        packetCount++;


        if (packet instanceof PlayerPositionLookS2CPacket) {
            addPattern("PositionLook");
        }

        if (packetName.contains("KeepAlive")) {
            addPattern("KeepAlive");
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket) {
            addPattern("Velocity");
        }

        if (packet instanceof WorldTimeUpdateS2CPacket) {
            addPattern("WorldTime");
        }

        if (packet instanceof GameJoinS2CPacket) {
            addPattern("GameMode");
            detected = true;
        }

        if (packetName.contains("Transaction")) {
            addPattern("Transaction");
        }

        if (packet instanceof PlayerPositionLookS2CPacket && packetCount % 5 == 0) {
            addPattern("FrequentPositionLook");
        }

        if (packetName.contains("Cubecraft") || packetName.contains("CubeCraft") ||
                packetName.contains("Sentinel")) {
            detectedAntiCheat = "Sentinel (CubeCraft)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Sentinel (CubeCraft)");
            setEnabled(false);
        }

        if (packetName.contains("Hypixel") || packetName.contains("Watchdog")) {
            detectedAntiCheat = "Watchdog (Hypixel)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Watchdog (Hypixel)");
            setEnabled(false);
        }

        if (packetName.contains("Verus")) {
            detectedAntiCheat = "Verus";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Verus");
            setEnabled(false);
        }

        if (packetName.contains("Grim") || packetName.contains("GrimAC")) {
            detectedAntiCheat = "GrimAC";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: GrimAC");
            setEnabled(false);
        }

        if (packetName.contains("Vulcan")) {
            detectedAntiCheat = "Vulcan";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Vulcan");
            setEnabled(false);
        }

        if (packetName.contains("NCP") || packetName.contains("NoCheatPlus")) {
            detectedAntiCheat = "NoCheatPlus (NCP)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: NoCheatPlus (NCP)");
            setEnabled(false);
        }

        if (packetName.contains("Spartan")) {
            detectedAntiCheat = "Spartan";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Spartan");
            setEnabled(false);
        }

        if (packetName.contains("Intave")) {
            detectedAntiCheat = "Intave";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Intave");
            setEnabled(false);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (packetCount > 50 && !detected) {
            analyzePatterns();
        }

        setSuffix(detectedAntiCheat);
    }


    private void addPattern(String pattern) {
        patterns.put(pattern, patterns.getOrDefault(pattern, 0) + 1);
    }

    private void analyzePatterns() {
        if (patterns.isEmpty()) return;

        int posLook = patterns.getOrDefault("PositionLook", 0);
        int keepAlive = patterns.getOrDefault("KeepAlive", 0);
        int transaction = patterns.getOrDefault("Transaction", 0);
        int velocity = patterns.getOrDefault("Velocity", 0);
        int frequentPos = patterns.getOrDefault("FrequentPositionLook", 0);

        if (posLook > 15 && frequentPos > 5) {
            detectedAntiCheat = "GrimAC";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: GrimAC");
            setEnabled(false);
            return;
        }

        if (keepAlive > 20 && posLook > 10) {
            detectedAntiCheat = "Sentinel (CubeCraft)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Sentinel (CubeCraft)");
            setEnabled(false);
            return;
        }

        if (transaction > 30) {
            detectedAntiCheat = "Watchdog (Hypixel)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Watchdog (Hypixel)");
            setEnabled(false);
            return;
        }

        if (velocity > 10) {
            detectedAntiCheat = "Verus";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Verus");
            setEnabled(false);
            return;
        }

        if (posLook > 5 && patterns.getOrDefault("GameMode", 0) > 0) {
            detectedAntiCheat = "Vulcan";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Vulcan");
            setEnabled(false);
            return;
        }

        if (keepAlive > 10 && posLook < 5) {
            detectedAntiCheat = "NoCheatPlus (NCP)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: NoCheatPlus (NCP)");
            setEnabled(false);
            return;
        }

        if (patterns.getOrDefault("WorldTime", 0) > 0 && posLook > 3) {
            detectedAntiCheat = "Spartan";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Spartan");
            setEnabled(false);
            return;
        }

        String ip = "";
        if (mc.getCurrentServerEntry() != null) {
            ip = mc.getCurrentServerEntry().address;
        }
        if (ip.toLowerCase().contains("cubecraft")) {
            detectedAntiCheat = "Sentinel (CubeCraft)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Sentinel (CubeCraft) by IP");
            setEnabled(false);
            return;
        }
        if (ip.toLowerCase().contains("hypixel")) {
            detectedAntiCheat = "Watchdog (Hypixel)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Watchdog (Hypixel) by IP");
            setEnabled(false);
            return;
        }
        if (ip.toLowerCase().contains("blocksmc")) {
            detectedAntiCheat = "Verus (BlocksMC)";
            detected = true;
            Util.log("[AntiCheatDetect] Detected: Verus (BlocksMC) by IP");
            setEnabled(false);
            return;
        }

        if (packetCount > 100 && !detected) {
            detectedAntiCheat = "Unknown / Vanilla";
            detected = true;
            Util.log("[AntiCheatDetect] No anti-cheat detected (Vanilla or unknown)");
            setEnabled(false);
        }
    }

    public String getSuffix() {
        return detectedAntiCheat;
    }
}