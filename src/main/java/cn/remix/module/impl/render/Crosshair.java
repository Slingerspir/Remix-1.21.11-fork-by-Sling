package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.MovementUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class Crosshair extends Module {
    private final NumberValue gap = new NumberValue("Gap", 1.0f, 0.25f, 15.0f, 0.25f);
    private final NumberValue width = new NumberValue("Width", 0, 0.0f, 10.0f, 0.25f);
    private final NumberValue size = new NumberValue("Size", 2.5f, 0.25f, 15.0f, 0.25f);

    public Crosshair() {
        super("Crosshair", Category.Render);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        DrawContext context = event.getContext();
        HUD hud = getModule(HUD.class);

        float x = context.getScaledWindowWidth() / 2f;
        float y = context.getScaledWindowHeight() / 2f;
        float gap = this.gap.getValue();

        if (MovementUtil.isMoving()) {
            gap += 2;
        }

        drawCrosshairPart(context, x, y, gap, width.getValue(), size.getValue(), new Color(0, 0, 0, 255).getRGB(), 0.5F);
        drawCrosshairPart(context, x, y, gap, width.getValue(), size.getValue(), hud.getColor(), 0.0F);
    }

    private void drawCrosshairPart(DrawContext context, float x, float y, float gap, float width, float size, int color, float offset) {
        Render2D.drawRect(context, x - width - offset, y - gap - size - offset, width * 2 + 1 + offset * 2, size + offset * 2, color);
        Render2D.drawRect(context, x - width - offset, y + gap + 1 - offset, width * 2 + 1 + offset * 2, size + offset * 2, color);
        Render2D.drawRect(context, x - gap - size - offset, y - width - offset, size + offset * 2, width * 2 + 1 + offset * 2, color);
        Render2D.drawRect(context, x + gap + 1 - offset, y - width - offset, size + offset * 2, width * 2 + 1 + offset * 2, color);
    }
}