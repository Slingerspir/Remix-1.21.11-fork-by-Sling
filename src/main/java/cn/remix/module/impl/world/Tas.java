package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.management.TasManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import net.minecraft.server.MinecraftServer;

public final class Tas extends Module {

    private final NumberValue speed = new NumberValue("Speed", 1.0f, 0.1f, 10.0f, 0.05f);
    private final BoolValue pause = new BoolValue("Pause", false);
    private final BoolValue soundPitch = new BoolValue("Sound Pitch", false);

    private float lastSpeed = 1.0f;
    private boolean lastPause = false;
    private boolean lastSoundPitch = false;
    private MinecraftServer lastServer;

    public Tas() {
        super("TAS", Category.World);
    }

    @Override
    public void onEnable() {
        lastSpeed = speed.getValue();
        lastPause = pause.getValue();
        lastSoundPitch = soundPitch.getValue();
        apply();
        Util.log("[TAS] Enabled");
    }

    @Override
    public void onDisable() {
        TasManager.resetTas();
        TasManager.setSoundPitchEnabled(false);

        if (mc.isInSingleplayer()) {
            MinecraftServer server = mc.getServer();
            if (server != null) {
                server.execute(() -> {
                    server.getTickManager().setTickRate(20.0f);
                    server.getTickManager().setFrozen(false);
                });
            }
        }
        lastServer = null;

        setSuffix("");
        Util.log("[TAS] Disabled - Restored 20 TPS");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.world == null) return;

        MinecraftServer currentServer = mc.getServer();
        if (currentServer != lastServer) {
            lastServer = currentServer;
            apply();
        }

        float currentSpeed = speed.getValue();
        boolean currentPause = pause.getValue();
        boolean currentSoundPitch = soundPitch.getValue();

        if (currentSpeed != lastSpeed || currentPause != lastPause || currentSoundPitch != lastSoundPitch) {
            lastSpeed = currentSpeed;
            lastPause = currentPause;
            lastSoundPitch = currentSoundPitch;
            apply();
        }

        updateSuffix();
    }

    private void apply() {
        float multiplier = lastPause ? 0.0f : lastSpeed;
        TasManager.setTasMultiplier(multiplier);
        TasManager.setSoundPitchEnabled(lastSoundPitch);

        if (mc.isInSingleplayer()) {
            MinecraftServer server = mc.getServer();
            if (server != null) {
                float serverRate = 20.0f * lastSpeed;
                boolean frozen = lastPause;
                server.execute(() -> {
                    server.getTickManager().setTickRate(serverRate);
                    server.getTickManager().setFrozen(frozen);
                });
            }
        }
    }

    private void updateSuffix() {
        if (lastPause) {
            setSuffix("Paused");
        } else {
            setSuffix(String.format("%.1fx", lastSpeed));
        }
    }
}