package cn.remix.module.impl.render;

import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.combat.TpAura;
import cn.remix.module.impl.render.targethud.Exhibition;
import cn.remix.module.impl.render.targethud.Novoline;
import cn.remix.module.impl.render.targethud.Remix;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.ui.hud.Drag;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.LivingEntity;

public class TargetHUD extends Drag {
    private final ModeValue mode = new ModeValue("Mode", "Novoline", "Novoline", "Remix", "Exhibition");
    private final EasingAnimation visibilityAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 220);
    private final EasingAnimation scaleAnimation = new EasingAnimation(Easing.EASE_OUT_BACK, 280);
    private LivingEntity lastTarget;

    public TargetHUD() {
        super("TargetHUD");
        this.percentX = .5f;
        this.percentY = .8f;
    }

    @Override
    public void render(DrawContext context) {
        if (mc.player == null || mc.world == null) return;
        setSuffix(mode.getValue());

        LivingEntity target = getTarget();
        if (target != null) {
            lastTarget = target;
        }

        visibilityAnimation.run(target != null ? 1 : 0);
        scaleAnimation.run(target != null ? 1 : 0);
        float alpha = visibilityAnimation.getValue().floatValue();
        if (alpha <= 0.01f || lastTarget == null) {
            return;
        }

        LivingEntity renderTarget = lastTarget;
        width = switch (mode.getValue()) {
            case "Exhibition" -> Exhibition.getWidth(renderTarget);
            case "Remix" -> Remix.getWidth(renderTarget);
            default -> Novoline.getWidth(renderTarget);
        };

        height = switch (mode.getValue()) {
            case "Exhibition" -> Exhibition.getHeight();
            case "Remix" -> Remix.getHeight();
            default -> Novoline.getHeight();
        };

        float scale = 0.82f + scaleAnimation.getValue().floatValue() * 0.18f;
        float originX = renderX + width / 2.0f;
        float originY = renderY + height / 2.0f;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(originX, originY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-originX, -originY);
        switch (mode.getValue()) {
            case "Exhibition" -> Exhibition.render(context, renderTarget, renderX, renderY, alpha);
            case "Remix" -> Remix.render(context, renderTarget, renderX, renderY, alpha);
            default -> Novoline.render(context, renderTarget, renderX, renderY, alpha);
        }
        context.getMatrices().popMatrix();
    }

    public boolean shouldRenderAnimated() {
        return isEnabled() && (getTarget() != null || visibilityAnimation.getValue() > 0.01);
    }

    public LivingEntity getTarget() {
        if (mc.currentScreen instanceof ChatScreen) return mc.player;

        TpAura tpAura = getModule(TpAura.class);
        if (tpAura.isEnabled() && tpAura.getTarget() != null) {
            return tpAura.getTarget();
        }

        Aura aura = getModule(Aura.class);
        return aura.isEnabled() ? aura.getTarget() : null;
    }
}
