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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

/**
 * Sight - 目标信息 HUD
 * 基于 Trivia Client 的 Sight 模式
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
        if (!(target instanceof AbstractClientPlayerEntity player)) return;

        int alphaInt = (int) (alpha * 255);
        if (alphaInt < 5) return;

        HUD hud = instance.getModuleManager().getModule(HUD.class);
        int accentColor = hud != null ? hud.getColor() : new Color(100, 150, 255).getRGB();

        float width = getWidth(target);
        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);

        int healthColor = getHealthColor(health, maxHealth);

        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }

        // ===== 1. 背景（直角矩形，四个角完全填充） =====
        int bgColor = new Color(0, 0, 0, (int) (alphaInt * 0.4f)).getRGB();
        Render2D.drawRect(context, x, y, width, HEIGHT, bgColor);

        // ===== 2. 边框（直角） =====
        int borderColor = ColorUtil.applyAlpha(accentColor, (int) (alphaInt * 0.2f));
        Render2D.drawOutline(context, x, y, width, HEIGHT, 1.0f, borderColor);

        // ===== 3. 头像 =====
        float headX = x + 8;
        float headY = y + 8;
        float headSize = 40;

        Identifier skinTexture = player.getSkin().body().texturePath();
        Render2D.drawTexture(context, skinTexture, headX, headY, headSize, headSize, 8/64f, 8/64f, 16/64f, 16/64f, -1);
        Render2D.drawTexture(context, skinTexture, headX, headY, headSize, headSize, 40/64f, 8/64f, 48/64f, 16/64f, -1);

        int avatarBorderColor = ColorUtil.applyAlpha(accentColor, (int) (alphaInt * 0.3f));
        Render2D.drawOutline(context, headX, headY, headSize, headSize, 1.0f, avatarBorderColor);

        // ===== 4. 名称 =====
        TrueTypeFont nameFont = instance.getFontManager().getFont(16);
        if (nameFont == null) nameFont = instance.getFontManager().getFont(16);
        String name = target.getName().getString();
        float nameX = x + 52;
        float nameY = y + 12;
        nameFont.drawStringWithShadow(context, name, nameX, nameY, 0xFFFFFFFF);

        // ===== 5. Ping =====
        String pingText = ping + "ms";
        TrueTypeFont pingFont = instance.getFontManager().getFont(13);
        if (pingFont == null) pingFont = nameFont;
        float pingX = x + width - 12 - pingFont.getStringWidth(pingText);
        float pingY = y + 12;
        int pingColor = ping < 50 ? 0xFF00FF00 : ping < 150 ? 0xFFFFFF00 : 0xFFFF0000;
        pingFont.drawStringWithShadow(context, pingText, pingX, pingY, pingColor);

        // ===== 6. 血量条 =====
        float barX = x + 52;
        float barY = y + 58;
        float barWidth = width - 60;
        float barHeight = 4;

        int barBg = new Color(0, 0, 0, (int) (alphaInt * 0.8f)).getRGB();
        Render2D.drawRect(context, barX, barY, barWidth, barHeight, barBg);

        if (healthPercent > 0.01f) {
            int fillColor = ColorUtil.applyAlpha(healthColor, alphaInt);
            float fillWidth = barWidth * healthPercent;
            Render2D.drawRect(context, barX, barY, fillWidth, barHeight, fillColor);
        }

        // ===== 7. 装备 =====
        float itemX = x + 52;
        float itemY = y + 40;

        ItemStack[] items = {
                player.getMainHandStack(),
                player.getEquippedStack(EquipmentSlot.HEAD),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.FEET)
        };

        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack != null && !stack.isEmpty()) {
                int itemBg = new Color(0, 0, 0, (int) (alphaInt * 0.3f)).getRGB();
                Render2D.drawRect(context, itemX - 1, itemY - 1, 18, 18, itemBg);
                Render2D.drawItem(context, stack, itemX, itemY);
            }
            itemX += 20;
        }
    }

    private static int getHealthColor(float health, float maxHealth) {
        float percentage = MathHelper.clamp(health / maxHealth, 0f, 1f);
        if (percentage > 0.6f) {
            float p = (percentage - 0.6f) / 0.4f;
            return ColorUtil.interpolate(new Color(255, 200, 0).getRGB(), new Color(50, 220, 50).getRGB(), p);
        } else if (percentage > 0.3f) {
            float p = (percentage - 0.3f) / 0.3f;
            return ColorUtil.interpolate(new Color(255, 150, 0).getRGB(), new Color(255, 200, 0).getRGB(), p);
        } else {
            float p = percentage / 0.3f;
            return ColorUtil.interpolate(new Color(255, 40, 40).getRGB(), new Color(255, 150, 0).getRGB(), p);
        }
    }
}