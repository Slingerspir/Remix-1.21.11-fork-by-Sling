package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class Indicator extends Module {
    private final ModeValue style = new ModeValue("Style", "Greater Than", "Triangle", "Greater Than");
    private final NumberValue maxOpacity = new NumberValue("Max Opacity", 210, 0, 255, 5);
    private final NumberValue radius = new NumberValue("Radius", 56, 20, 160, 2);
    private final NumberValue scale = new NumberValue("Scale", 1.0f, 0.4f, 2.5f, 0.05f);
    private final BoolValue hideFov = new BoolValue("Hide FOV", false);
    private final NumberValue hiddenFov = new NumberValue("Hidden FOV", 70, 0, 180, 5, hideFov::getValue);

    public Indicator() {
        super("Indicator", Category.Render);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Aura aura = getModule(Aura.class);
        LivingEntity locked = aura.isEnabled() ? aura.getTarget() : null;
        float cx = mc.getWindow().getScaledWidth() / 2.0f;
        float cy = mc.getWindow().getScaledHeight() / 2.0f;
        float r = radius.getValue();
        float size = 8.0f * scale.getValue();

        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (entity == mc.player || !EntityUtil.isSelected(entity, true, true, false, true) || !entity.isAlive() || entity.isDead()) {
                continue;
            }

            float distance = mc.player.distanceTo(entity);
            if (distance > 80.0f) {
                continue;
            }

            float[] rotations = RotationUtil.getRotations(entity.getEyePos());
            if (rotations == null) {
                continue;
            }

            float relativeYaw = MathHelper.wrapDegrees(rotations[0] - mc.player.getYaw());
            if (hideFov.getValue() && Math.abs(relativeYaw) <= hiddenFov.getValue() / 2.0f) {
                continue;
            }

            float alphaFactor = 1.0f - Math.clamp((distance - 30.0f) / 50.0f, 0.0f, 1.0f);
            int alpha = Math.round(maxOpacity.getValue() * alphaFactor);
            int color = getIndicatorColor(entity, locked, alpha);

            double radians = Math.toRadians(relativeYaw);
            float ux = (float) Math.sin(radians);
            float uy = (float) -Math.cos(radians);
            float px = -uy;
            float py = ux;
            float tipX = cx + ux * r;
            float tipY = cy + uy * r;
            float baseX = cx + ux * (r - size * 1.6f);
            float baseY = cy + uy * (r - size * 1.6f);
            float rearX = cx + ux * (r - size * 2.35f);
            float rearY = cy + uy * (r - size * 2.35f);

            if (style.is("Greater Than")) {
                drawRotatedText(event, ">", tipX, tipY, radians - Math.PI / 2.0, size, color);
            } else {
                drawRotatedText(event, "▲", tipX, tipY, radians, size, color);
            }
        }
    }

    private void drawRotatedText(Render2DEvent event, String text, float x, float y, double radians, float size, int color) {
        TrueTypeFont font = instance.getFontManager().getBoldFont(22);
        float textScale = size / 8.0f;
        float width = font.getStringWidth(text);
        float height = font.getHeight();

        event.getContext().getMatrices().pushMatrix();
        event.getContext().getMatrices().translate(x, y);
        event.getContext().getMatrices().rotate((float) radians);
        event.getContext().getMatrices().scale(textScale, textScale);
        font.drawStringWithShadow(event.getContext(), text, -width / 2.0f, -height / 2.0f, color);
        event.getContext().getMatrices().popMatrix();
    }

    private int getIndicatorColor(LivingEntity entity, LivingEntity locked, int alpha) {
        if (entity == locked) {
            return ColorUtil.applyAlpha(0xFFFF3333, alpha);
        }

        if (entity instanceof PlayerEntity player && instance.getFriendManager().isFriend(player.getName().getString())) {
            return ColorUtil.applyAlpha(0xFF0E5F38, alpha);
        }

        return ColorUtil.applyAlpha(0xFFFFFFFF, alpha);
    }
}
