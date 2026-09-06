package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EffectDisplay extends Module {
    private final NumberValue scale = new NumberValue("Scale", 0.6f, 0.3f, 1.5f, 0.05f);
    private final NumberValue xOffset = new NumberValue("X Offset", 2, 0, 100, 1);
    private final NumberValue yOffset = new NumberValue("Y Offset", 2, 0, 100, 1);
    private final BoolValue progressBar = new BoolValue("Progress Bar", true);

    private final List<EffectEntry> entries = new ArrayList<>();
    private final TimerUtil timer = new TimerUtil();

    public EffectDisplay() {
        super("EffectDisplay", Category.Render);
    }

    @Override
    public void onEnable() {
        entries.clear();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (!timer.hasTimeElapsed(200)) return;
        timer.reset();

        // refresh active effects
        Iterator<EffectEntry> it = entries.iterator();
        while (it.hasNext()) {
            StatusEffectInstance instance = it.next().instance;
            if (instance == null || instance.getDuration() <= 0 || mc.player.getStatusEffect(instance.getEffectType()) != instance) {
                it.remove();
            }
        }

        List<StatusEffectInstance> active = new ArrayList<>(mc.player.getStatusEffects());
        for (StatusEffectInstance instance : active) {
            boolean found = false;
            for (EffectEntry entry : entries) {
                if (entry.instance.getEffectType() == instance.getEffectType()) {
                    entry.instance = instance;
                    entry.startTime = System.currentTimeMillis();
                    found = true;
                    break;
                }
            }
            if (!found) {
                entries.add(new EffectEntry(instance, System.currentTimeMillis()));
            }
        }

        TrueTypeFont font = instance.getFontManager().getFont((int) (14 * scale.getValue()));
        float x = xOffset.getValue();
        float y = yOffset.getValue();

        for (EffectEntry entry : entries) {
            StatusEffectInstance effect = entry.instance;
            if (effect == null || effect.getDuration() <= 0) continue;

            String name = effect.getEffectType().value().getName().getString();
            String level = effect.getAmplifier() > 0 ? " " + toRoman(effect.getAmplifier() + 1) : "";
            String duration = formatDuration(effect.getDuration());

            float width = Math.max(font.getStringWidth(name + level + " " + duration) + 8, 60) * scale.getValue();
            float height = 18 * scale.getValue();

            Render2D.drawRoundedRect(event.getContext(), x, y, width, height, 4, 0xA0000000);

            font.drawStringWithShadow(event.getContext(), name + level, x + 4, y + 2, 0xFFFF5555);
            font.drawStringWithShadow(event.getContext(), duration, x + width - font.getStringWidth(duration) - 4, y + 2, 0xFFFFFFFF);

            if (progressBar.getValue() && effect.getDuration() >= 0) {
                float progress = effect.getDuration() / 600.0f;
                progress = Math.max(0, Math.min(1, progress));
                float barWidth = (width - 8) * progress;
                Render2D.drawRect(event.getContext(), x + 4, y + height - 3, barWidth, 2, 0xFFFFFFFF);
            }

            y += height + 2;
        }
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(n);
        };
    }

    private String formatDuration(int ticks) {
        if (ticks < 0) return "**:**";
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static final class EffectEntry {
        StatusEffectInstance instance;
        long startTime;

        EffectEntry(StatusEffectInstance instance, long startTime) {
            this.instance = instance;
            this.startTime = startTime;
        }
    }
}
