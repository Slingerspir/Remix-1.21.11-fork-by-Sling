package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.IMinecraft;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public final class Fullbright extends Module implements IMinecraft {

    private final ModeValue mode = new ModeValue("Mode", "NightVision", "NightVision", "Gamma");
    private final NumberValue gammaValue = new NumberValue("Gamma", 1.0f, 0.0f, 1.0f, 0.05f);
    private final BoolValue permanent = new BoolValue("Permanent", true);

    private double originalGamma = 0.0;

    public Fullbright() {
        super("Fullbright", Category.Render);
    }

    @Override
    public void onEnable() {
        originalGamma = mc.options.getGamma().getValue();
        applyFullbright();
    }

    @Override
    public void onDisable() {
        mc.options.getGamma().setValue(originalGamma);
        if (mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        applyFullbright();
    }

    private void applyFullbright() {
        String currentMode = mode.getValue();
        switch (currentMode) {
            case "NightVision" -> applyNightVision();
            case "Gamma" -> applyGamma();
            default -> applyGamma();
        }
    }

    private void applyNightVision() {
        if (mc.player == null) return;
        boolean hasEffect = mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION);
        if (permanent.getValue()) {
            if (!hasEffect) {
                StatusEffectInstance effect = new StatusEffectInstance(
                        StatusEffects.NIGHT_VISION,
                        1000,
                        0,
                        false,
                        false
                );
                mc.player.addStatusEffect(effect);
            }
        } else {
            if (hasEffect) {
                mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        }
    }

    private void applyGamma() {
        double value = gammaValue.getValue().doubleValue();
        double clamped = Math.max(0.0, Math.min(1.0, value));
        mc.options.getGamma().setValue(clamped);
    }

}