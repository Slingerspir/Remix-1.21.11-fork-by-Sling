package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

public class WorldTweaks extends Module {
    public final NumberValue ctimeVal = new NumberValue("Time", 21000, 0, 23000, 1000);
    public final ModeValue weather = new ModeValue("Weather", "Normal", "Normal", "Clear", "Rain", "Snow");
    public final NumberValue intensity = new NumberValue("Intensity", 1, 0.1f, 1, 0.1f);
    private float oldRainGradient;
    private boolean oldRaining;
    private long oldTime;

    public WorldTweaks() {
        super("WorldTweaks", Category.World);
    }

    @Override
    public void onEnable() {
        if (mc.world == null) return;

        oldTime = mc.world.getTimeOfDay();
        oldRaining = mc.world.getLevelProperties().isRaining();
        oldRainGradient = mc.world.getRainGradient(1);
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;

        mc.world.getLevelProperties().setTimeOfDay(oldTime);
        mc.world.getLevelProperties().setRaining(oldRaining);
        mc.world.setRainGradient(oldRainGradient);
    }

    @EventTarget
    private void onPacket(PacketEvent event) {
        if (mc.world == null) return;

        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket packet) {
            oldTime = packet.timeOfDay();
            event.setCancelled(true);
        }

        if (event.getPacket() instanceof GameStateChangeS2CPacket packet && !weather.is("Normal")) {
            GameStateChangeS2CPacket.Reason reason = packet.getReason();
            if (reason == GameStateChangeS2CPacket.RAIN_STARTED ||
                    reason == GameStateChangeS2CPacket.RAIN_STOPPED ||
                    reason == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED ||
                    reason == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED) {

                if (reason == GameStateChangeS2CPacket.RAIN_STARTED) {
                    oldRaining = true;
                    oldRainGradient = 1;
                }
                if (reason == GameStateChangeS2CPacket.RAIN_STOPPED) {
                    oldRaining = false;
                    oldRainGradient = 0;
                }
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.world == null) return;

        setSuffix(String.format("%.1f", ctimeVal.getValue()));

        mc.world.getLevelProperties().setTimeOfDay(ctimeVal.getValue().longValue());

        if (weather.is("Clear")) {
            mc.world.getLevelProperties().setRaining(false);
            mc.world.setRainGradient(0);
        } else if (weather.is("Rain") || weather.is("Snow")) {
            mc.world.getLevelProperties().setRaining(true);
            mc.world.setRainGradient(intensity.getValue());
        }
    }
}