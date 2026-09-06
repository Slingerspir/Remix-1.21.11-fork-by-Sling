package cn.remix.module.impl.render.targethud;

import cn.remix.module.impl.render.HUD;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Sight - 目标信息 HUD 样式（Trivia Client Sight 移植）
 */
public class Sight implements IMinecraft {

    private static final float HEIGHT = 80.0f;

    public static float getWidth(LivingEntity target) {
        if (target == null) return 200;
        float nameWidth = instance.getFontManager().getFont(16).getStringWidth(target.getName().getString());
        return Math.max(200, nameWidth + 60);
    }

    public static float getHeight() {
        return HEIGHT;
    }

    public static void render(DrawContext context, LivingEntity target, float x, float y, float alpha) {
        if (target == null) return;
        float old = Render2D.getGlobalAlpha();
        Render2D.setGlobalAlpha(Math.max(0f, Math.min(1f, alpha)));
        try {
            draw(context, target, x, y, alpha);
        } finally {
            Render2D.setGlobalAlpha(old);
        }
    }

    private static void draw(DrawContext context, LivingEntity target, float x, float y, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        if (a < 5) return;

        float width = getWidth(target);
        float health = target.getHealth();
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);

        // 1. 底（必画）
        Render2D.drawRect(context, x, y, width, HEIGHT, ColorUtil.applyAlpha(0xFF000000, (int) (a * 0.55f)));
        // 2. 边框
        Render2D.drawRect(context, x, y, width, 1, ColorUtil.applyAlpha(0xFF3AA0FF, (int) (a * 0.35f)));
        Render2D.drawRect(context, x, y + HEIGHT - 1, width, 1, ColorUtil.applyAlpha(0xFF3AA0FF, (int) (a * 0.35f)));

        // 3. 头像（失败也能继续）
        try {
            drawHead(context, target, x + 8, y + 8, a);
        } catch (Throwable ignored) {
        }

        // 4. 名称
        try {
            String name = target.getName().getString();
            TrueTypeFont f = instance.getFontManager().getFont(16);
            f.drawStringWithShadow(context, name, x + 52, y + 12, ColorUtil.applyAlpha(0xFFFFFFFF, a));
        } catch (Throwable ignored) {
        }

        // 5. Ping
        try {
            drawPing(context, target, x, y, width, a);
        } catch (Throwable ignored) {
        }

        // 6. 血量条
        float barX = x + 52;
        float barY = y + 60;
        float barWidth = width - 60;
        float barHeight = 4;
        Render2D.drawRect(context, barX, barY, barWidth, barHeight, ColorUtil.applyAlpha(0xFF000000, (int) (a * 0.8f)));
        Render2D.drawRect(context, barX, barY, barWidth * healthPercent, barHeight, ColorUtil.applyAlpha(healthColor(health, maxHealth), a));

        // 7. 装备
        try {
            drawItems(context, target, x + 52, y + 40, a);
        } catch (Throwable ignored) {
        }
    }

    private static void drawHead(DrawContext context, LivingEntity target, float hx, float hy, int a) {
        if (!(target instanceof AbstractClientPlayerEntity player)) {
            Render2D.drawRect(context, hx, hy, 40, 40, ColorUtil.applyAlpha(0xFF6B7682, a));
            return;
        }
        Identifier skin = player.getSkin().body().texturePath();
        Render2D.drawTexture(context, skin, hx, hy, 40, 40, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, -1);
        Render2D.drawTexture(context, skin, hx, hy, 40, 40, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, -1);
        Render2D.drawRect(context, hx, hy, 40, 1, ColorUtil.applyAlpha(0xFF3AA0FF, (int) (a * 0.3f)));
        Render2D.drawRect(context, hx, hy + 39, 40, 1, ColorUtil.applyAlpha(0xFF3AA0FF, (int) (a * 0.3f)));
        Render2D.drawRect(context, hx, hy, 1, 40, ColorUtil.applyAlpha(0xFF3AA0FF, (int) (a * 0.3f)));
        Render2D.drawRect(context, hx + 39, hy, 1, 40, ColorUtil.applyAlpha(0xFF3AA0FF, (int) (a * 0.3f)));
    }

    private static void drawPing(DrawContext context, LivingEntity target, float x, float y, float width, int a) {
        int ping = 0;
        if (target instanceof AbstractClientPlayerEntity player && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        String pingText = ping + "ms";
        TrueTypeFont f = instance.getFontManager().getFont(13);
        int color = ping < 50 ? 0xFF00FF00 : ping < 150 ? 0xFFFFFF00 : 0xFFFF4B4B;
        f.drawStringWithShadow(context, pingText, x + width - 8 - f.getStringWidth(pingText), y + 12, ColorUtil.applyAlpha(color, a));
    }

    private static void drawItems(DrawContext context, LivingEntity target, float ix, float iy, int a) {
        ItemStack[] items = {
                target.getEquippedStack(EquipmentSlot.MAINHAND),
                target.getEquippedStack(EquipmentSlot.HEAD),
                target.getEquippedStack(EquipmentSlot.CHEST),
                target.getEquippedStack(EquipmentSlot.LEGS),
                target.getEquippedStack(EquipmentSlot.FEET)
        };
        float cursor = ix;
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) {
                Render2D.drawRect(context, cursor - 1, iy - 1, 18, 18, ColorUtil.applyAlpha(0xFF000000, (int) (a * 0.3f)));
                Render2D.drawItem(context, stack, cursor, iy);
            }
            cursor += 20;
        }
    }

    private static int healthColor(float health, float maxHealth) {
        float p = MathHelper.clamp(health / maxHealth, 0f, 1f);
        if (p > 0.6f) {
            float k = (p - 0.6f) / 0.4f;
            return ColorUtil.interpolate(0xFFFFC800, 0xFF32DC32, k);
        } else if (p > 0.3f) {
            float k = (p - 0.3f) / 0.3f;
            return ColorUtil.interpolate(0xFFFF9600, 0xFFFFC800, k);
        } else {
            float k = p / 0.3f;
            return ColorUtil.interpolate(0xFFFF2828, 0xFFFF9600, k);
        }
    }
}
