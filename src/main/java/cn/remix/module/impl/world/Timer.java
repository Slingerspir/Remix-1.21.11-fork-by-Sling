package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.management.TasManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class Timer extends Module {

    private final ModeValue mode = new ModeValue("Mode", "Classic", "Classic", "Pulse", "Boost");

    private final NumberValue classicSpeed = new NumberValue("Speed", 2.0f, 0.1f, 20.0f, 0.1f, () -> mode.is("Classic"));

    private final NumberValue normalSpeed = new NumberValue("Normal Speed", 0.5f, 0.1f, 20.0f, 0.05f, () -> mode.is("Pulse"));
    private final NumberValue normalSpeedTicks = new NumberValue("Normal Ticks", 20, 1, 500, 1, () -> mode.is("Pulse"));
    private final NumberValue boostSpeed = new NumberValue("Boost Speed", 2.0f, 0.1f, 20.0f, 0.05f, () -> mode.is("Pulse"));
    private final NumberValue boostSpeedTicks = new NumberValue("Boost Ticks", 20, 1, 500, 1, () -> mode.is("Pulse"));
    private final BoolValue onMove = new BoolValue("On Move", false, () -> mode.is("Pulse"));

    private final NumberValue boostSpeedBoost = new NumberValue("Boost Speed", 1.3f, 0.1f, 20.0f, 0.05f, () -> mode.is("Boost"));
    private final NumberValue slowSpeed = new NumberValue("Slow Speed", 0.6f, 0.1f, 20.0f, 0.05f, () -> mode.is("Boost"));
    private final NumberValue timeBoostTicks = new NumberValue("Boost Ticks", 12, 1, 60, 1, () -> mode.is("Boost"));
    private final BoolValue accountTimerValue = new BoolValue("Account Timer", true, () -> mode.is("Boost"));
    private final BoolValue normalizeDuringCombat = new BoolValue("Normalize Combat", true, () -> mode.is("Boost"));
    private final BoolValue allowNegative = new BoolValue("Allow Negative", false, () -> mode.is("Boost"));

    private int tickCounter = 0;
    private boolean pulseState = false;
    private int pulseTicksLeft = 0;
    private int boostCapable = 0;
    private static float currentTimerSpeed = 1.0f;

    public Timer() {
        super("Timer", Category.World);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        tickCounter = 0;
        pulseState = false;
        pulseTicksLeft = 0;
        boostCapable = 0;
        currentTimerSpeed = 1.0f;
        TasManager.resetTimer();
        Util.log("[Timer] Enabled - Mode: " + mode.getValue());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTimerSpeed = 1.0f;
        TasManager.resetTimer();
        Util.log("[Timer] Disabled - Reset timer to 1.0x");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

        String currentMode = mode.getValue();
        float speed = 1.0f;

        switch (currentMode) {
            case "Classic" -> speed = handleClassic();
            case "Pulse" -> speed = handlePulse();
            case "Boost" -> speed = handleBoost();
            default -> {}
        }

        currentTimerSpeed = speed;
        applyTimerSpeed(speed);

        updateSuffix();
    }

    private float handleClassic() {
        return classicSpeed.getValue().floatValue();
    }

    private float handlePulse() {
        if (onMove.getValue() && !isMoving()) {
            return 1.0f;
        }

        if (pulseTicksLeft <= 0) {
            pulseState = !pulseState;

            if (pulseState) {
                pulseTicksLeft = boostSpeedTicks.getValue().intValue();
                return boostSpeed.getValue().floatValue();
            } else {
                pulseTicksLeft = normalSpeedTicks.getValue().intValue();
                return normalSpeed.getValue().floatValue();
            }
        }

        pulseTicksLeft--;
        return pulseState ? boostSpeed.getValue().floatValue() : normalSpeed.getValue().floatValue();
    }

    private float handleBoost() {
        if (normalizeDuringCombat.getValue() && isInCombat()) {
            return 1.0f;
        }

        if (boostCapable < 0) {
            float slow = slowSpeed.getValue().floatValue();
            boostCapable = 0;
            return slow;
        }

        if (!isMoving()) {
            if (mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof GenericContainerScreen) {
                boostCapable = 0;
                return 1.0f;
            }

            float slow = slowSpeed.getValue().floatValue();
            int addition = accountTimerValue.getValue() ? (int) (1.0f / slow) : 1;
            boostCapable = Math.min(boostCapable + addition, timeBoostTicks.getValue().intValue());
            return slow;
        }

        boolean speedUp = boostCapable > 0 || (allowNegative.getValue() && isInCombat());

        if (!speedUp) {
            return 1.0f;
        }

        int ticks = boostCapable > 0 ? boostCapable : timeBoostTicks.getValue().intValue();
        int speedUpTicks = accountTimerValue.getValue() ? (int) Math.ceil(ticks / boostSpeedBoost.getValue().floatValue()) : ticks;

        if (speedUpTicks == 0) {
            return 1.0f;
        }

        boostCapable -= ticks;
        return boostSpeedBoost.getValue().floatValue();
    }

    private void applyTimerSpeed(float speed) {
        if (speed <= 0.01f) {
            speed = 0.01f;
        }
        currentTimerSpeed = speed;
        TasManager.setTimerMultiplier(speed);
    }

    private boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().y != 0.0f || mc.player.input.getMovementInput().x != 0.0f;
    }

    private boolean isInCombat() {
        if (mc.player == null) return false;
        return mc.player.hurtTime > 0;
    }

    private void updateSuffix() {
        if (currentTimerSpeed == 1.0f) {
            setSuffix(mode.getValue());
        } else {
            setSuffix(mode.getValue() + " " + String.format("%.1f", currentTimerSpeed) + "x");
        }
    }

    public static float getTimerSpeed() {
        return TasManager.getTimerMultiplier();
    }

    public static boolean isTimerActive() {
        return TasManager.getTimerMultiplier() != 1.0f;
    }

}