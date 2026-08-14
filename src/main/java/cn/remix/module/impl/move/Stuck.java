package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.MoveMathEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;

public class Stuck extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Freeze", "Freeze", "Cancel");
    private final NumberValue freezeTick = new NumberValue("Freeze Tick", 20, 1, 20, 1, () -> mode.is("Freeze"));
    private final BoolValue noMove = new BoolValue("No Move", true);
    private final BoolValue hud = new BoolValue("HUD", true);
    private final NumberValue hudOpacity = new NumberValue("HUD Opacity", 220, 0, 255, 5, hud::getValue);
    private int stuckTick;
    private float pulseProgress;

    public Stuck() {
        super("Stuck", Category.Move);
    }

    @Override
    public void onEnable() {
        stuckTick = 0;
    }

    @Override
    public void onDisable() {
        stuckTick = 0;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!hud.getValue()) return;

        TrueTypeFont font = instance.getFontManager().getBoldFont(18);
        String text = "Game Paused";
        float x = (mc.getWindow().getScaledWidth() - font.getStringWidth(text)) / 2.0f;
        float y = mc.getWindow().getScaledHeight() / 2.0f + 16.0f;
        font.drawStringWithShadow(event.getContext(), text, x, y, ColorUtil.applyAlpha(0xFFFF7AC8, hudOpacity.getValue().intValue()));

        float targetProgress = getPulseProgress();
        pulseProgress += (targetProgress - pulseProgress) * 0.18f;

        float barWidth = Math.max(54.0f, font.getStringWidth(text));
        float barHeight = 3.0f;
        float barX = (mc.getWindow().getScaledWidth() - barWidth) / 2.0f;
        float barY = y + font.getHeight() + 5.0f;
        int alpha = Math.min(hudOpacity.getValue().intValue(), 255);
        Render2D.drawRect(event.getContext(), barX, barY, barWidth, barHeight, ColorUtil.applyAlpha(0xFFFFFFFF, Math.round(alpha * 0.2f)));
        Render2D.drawRect(event.getContext(), barX, barY, barWidth * pulseProgress, barHeight, ColorUtil.applyAlpha(0xFFFFFFFF, Math.round(alpha * 0.8f)));
    }

    private float getPulseProgress() {
        if (!mode.is("Freeze")) {
            return 1.0f;
        }

        int ticks = Math.max(1, freezeTick.getValue().intValue());
        int clamped = Math.min(stuckTick, ticks);
        return 1.0f - (clamped / (float) ticks);
    }

    @EventTarget
    private void onWorld(WorldEvent event) {
        toggle();
    }

    @EventTarget
    private void onMathEvent(MoveMathEvent event) {
        if (mc.player == null || mc.world == null) return;

        switch (mode.getValue()) {
            case "Freeze" -> {
                if (stuckTick >= freezeTick.getValue().intValue()) {
                    stuckTick = 0;
                    event.setCancelled(false);
                } else {
                    event.setCancelled(true);
                }
            }

            case "Cancel" -> event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (noMove.getValue()) {
            event.setForward(0);
            event.setStrafe(0);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        setSuffix(mode.getValue());
        stuckTick++;
    }
}
