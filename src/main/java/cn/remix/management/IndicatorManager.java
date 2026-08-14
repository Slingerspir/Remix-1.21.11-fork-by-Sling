package cn.remix.management;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.impl.render.HUD;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;

import java.util.concurrent.CopyOnWriteArrayList;

public class IndicatorManager implements IMinecraft {
    private static final CopyOnWriteArrayList<Indicator> indicators = new CopyOnWriteArrayList<>();

    public IndicatorManager() {
        instance.getEventManager().register(this);
    }

    public static void register(Indicator indicator) {
        if (!indicators.contains(indicator)) {
            indicators.add(indicator);
        }
    }

    public static void unregister(Indicator indicator) {
        indicators.remove(indicator);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        DrawContext context = event.getContext();
        TrueTypeFont font = instance.getFontManager().getFont(16);
        HUD hud = instance.getModuleManager().getModule(HUD.class);

        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;

        float x = centerX + 10f;
        float y = centerY - (font.getHeight() / 2f);
        float spacing = font.getHeight() + 2f;

        for (Indicator indicator : indicators) {
            if (indicator.isVisible()) {
                String text = indicator.getText();
                if (text == null || text.isEmpty()) continue;

                int color = indicator.getColor();
                if (color == 0 && hud != null) {
                    color = hud.getColor();
                }

                font.drawStringWithShadow(context, text, x, y, color);
                y += spacing;
            }
        }
    }

    public interface Indicator {
        String getText();
        boolean isVisible();
        int getColor();
    }
}