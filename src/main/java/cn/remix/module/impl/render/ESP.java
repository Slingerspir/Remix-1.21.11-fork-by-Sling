package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class ESP extends Module {
    public final BoolValue esp2d = new BoolValue("2D ESP", true);
    public final BoolValue healthBar = new BoolValue("Health Bar", true);
    public final BoolValue armorBar = new BoolValue("Armor Bar", true);
    public final BoolValue nametags = new BoolValue("Name Tags", true);
    private final List<RenderData> targets = new ArrayList<>();

    public ESP() {
        super("ESP", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent e) {
        if (mc.player == null || mc.world == null) return;

        targets.clear();
        for (Entity entity : instance.getTargetManager().getTargets()) {
            if (EntityUtil.isSelected(entity, true, true, false, true)) {
                Vector4f pos = getEntityPos(entity, e.getProjectionMatrix(), e.getModelViewMatrix());
                if (pos != null) {
                    targets.add(new RenderData(entity, pos));
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent e) {
        if (targets.isEmpty()) return;

        for (RenderData data : targets) {
            if (!(data.entity instanceof LivingEntity living)) continue;
            Vector4f pos = data.pos;

            if (esp2d.getValue()) {
                drawBox(e.getContext(), pos.x, pos.y, pos.z, pos.w);
            }

            if (healthBar.getValue()) {
                drawHealthBar(e.getContext(), living, pos.x, pos.y, pos.w);
            }

            if (armorBar.getValue()) {
                drawArmorBar(e.getContext(), living, pos.z, pos.y, pos.w);
            }

            if (nametags.getValue()) {
                drawNametag(e.getContext(), living, pos.x, pos.y, pos.z);
            }
        }
    }

    private Vector4f getEntityPos(Entity entity, Matrix4f projMat, Matrix4f modMat) {
        Vec3d[] vectors = ProjectUtil.getVectors(entity);
        if (vectors.length == 0) return null;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean visible = false;

        for (Vec3d vector : vectors) {
            vector = ProjectUtil.worldSpaceToScreenSpace(vector, projMat, modMat);
            if (vector.z > 0 && vector.z < 1) {
                minX = Math.min((float) vector.x, minX);
                minY = Math.min((float) vector.y, minY);
                maxX = Math.max((float) vector.x, maxX);
                maxY = Math.max((float) vector.y, maxY);
                visible = true;
            }
        }

        return visible ? new Vector4f(minX, minY, maxX, maxY) : null;
    }

    private void drawNametag(DrawContext context, Entity entity, float x, float y, float right) {
        if (!(entity instanceof LivingEntity living) || mc.player == null) return;
        TrueTypeFont font = instance.getFontManager().getFont(12);
        String text = living.getName().getString() + String.format(" §7[§a%.1f§7]", living.getHealth()) + String.format(" §f%dm", (int) mc.player.distanceTo(living));
        float width = font.getStringWidth(text);
        float height = font.getHeight();
        float middle = x + (right - x) / 2;
        float distance = mc.player.distanceTo(living);
        float alpha = 1.0f - Math.clamp((distance - 30.0f) / 30.0f, 0.0f, 1.0f);
        float boxX = middle - (width / 2) - 2;
        float boxY = y - height - 4;
        float boxW = width + 4;
        float boxH = height + 2;
        Render2D.drawRect(context, boxX, boxY, boxW, boxH, ColorUtil.applyAlpha(Color.BLACK.getRGB(), Math.round(120.0f * alpha)));
        int relationColor = getRelationColor(entity);
        if (relationColor != 0) {
            Render2D.drawOutline(context, boxX - 1.0f, boxY - 1.0f, boxW + 2.0f, boxH + 2.0f, 1.0f, ColorUtil.applyAlpha(relationColor, Math.round(220.0f * alpha)));
        }
        font.drawStringWithShadow(context, text, middle - (width / 2), y - height - 3, -1);
    }

    private int getRelationColor(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return 0;
        }

        String name = player.getName().getString();
        if (instance.getFriendManager().isFriend(name)) {
            return new Color(95, 235, 130).getRGB();
        }
        if (instance.getFriendManager().isEnemy(name)) {
            return new Color(255, 75, 75).getRGB();
        }
        return 0;
    }

    private void drawBox(DrawContext context, float x, float y, float right, float bottom) {
        float width = right - x;
        float height = bottom - y;
        Render2D.drawRect(context, x - .5f, y - .5f, width + 1f, 1.5f, Color.BLACK.getRGB());
        Render2D.drawRect(context, x - .5f, bottom - 1f, width + 1f, 1.5f, Color.BLACK.getRGB());
        Render2D.drawRect(context, x - .5f, y + 1f, 1.5f, height - 2f, Color.BLACK.getRGB());
        Render2D.drawRect(context, right - 1f, y + 1f, 1.5f, height - 2f, Color.BLACK.getRGB());
        Render2D.drawRect(context, x, y, width, .5f, getModule(HUD.class).getColor(1));
        Render2D.drawRect(context, x, y, .5f, height, getModule(HUD.class).getColor(2));
        Render2D.drawRect(context, x, bottom - .5f, width, .5f, getModule(HUD.class).getColor(3));
        Render2D.drawRect(context, right - .5f, y, .5f, height, getModule(HUD.class).getColor(4));
    }

    private void drawHealthBar(DrawContext context, @NotNull LivingEntity entity, float x, float y, float bottom) {
        float healthValue = entity.getHealth() / entity.getMaxHealth();
        float height = (bottom - y) + 1;
        Render2D.drawRect(context, x - 3.5f, y - .5f, 2, height + 1, new Color(0, 0, 0, 180).getRGB());
        Render2D.drawRect(context, x - 3f, y, 1, height, ColorUtil.applyAlpha(getHealthColor(healthValue * 100).getRGB(), .3f));
        Render2D.drawRect(context, x - 3f, y + (height - height * healthValue), 1, height * healthValue, getHealthColor(healthValue * 100).getRGB());
    }

    private void drawArmorBar(DrawContext context, @NotNull LivingEntity entity, float right, float y, float bottom) {
        float armorValue = entity.getArmor() / 20f;
        float height = (bottom - y) + 1;
        Render2D.drawRect(context, right + 1.5f, y - .5f, 2, height + 1, new Color(0, 0, 0, 180).getRGB());
        Render2D.drawRect(context, right + 2, y, 1, height, ColorUtil.applyAlpha(new Color(135, 206, 250).getRGB(), .3f));
        Render2D.drawRect(context, right + 2, y + (height - height * armorValue), 1, height * armorValue, new Color(135, 206, 250).getRGB());
    }

    private Color getHealthColor(float healthPercent) {
        return healthPercent > 75 ? new Color(66, 246, 123) : healthPercent > 50 ? new Color(228, 255, 105) : healthPercent > 35 ? new Color(236, 100, 64) : new Color(255, 65, 68);
    }

    private record RenderData(Entity entity, Vector4f pos) {}
}
