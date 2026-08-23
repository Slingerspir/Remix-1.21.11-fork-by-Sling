package cn.remix.module.impl.render.targethud;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.LiquidGlassUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import java.awt.*;

public class Exhibition implements IMinecraft {

    public static void render(DrawContext context, LivingEntity target, float x, float y) {
        float health = Math.max(0.0f, Math.min(1.0f, target.getHealth() / target.getMaxHealth()));

        float width = getWidth(target);
        float height = getHeight();

        int blackBorder = Color.BLACK.getRGB();
        int darkGray = new Color(60, 60, 60).getRGB();
        int midGray = new Color(45, 45, 45).getRGB();
        int bgColor = new Color(15, 15, 15).getRGB();

        if (LiquidGlassUtil.isGlass()) {
            LiquidGlassUtil.drawGlass(context, x, y, width, height);
        } else {
            Render2D.drawRect(context, x, y, width, height, blackBorder);
            Render2D.drawRect(context, x + .5f, y + .5f, width - 1, height - 1, darkGray);
            Render2D.drawRect(context, x + 1, y + 1, width - 2, height - 2, midGray);
            Render2D.drawRect(context, x + 2.5f, y + 2.5f, width - 5, height - 5, darkGray);
            Render2D.drawRect(context, x + 3, y + 3, width - 6, height - 6, midGray);
            Render2D.drawRect(context, x + 3, y + 3, width - 6, height - 6, bgColor);
        }

        Render2D.drawOutline(context, x + 5.5f, y + 5.5f, 34, 34, .5f, darkGray);
        Render2D.drawModel(context, target, x, y);

        TrueTypeFont nameFont = instance.getFontManager().getTahomaFont(16);
        TrueTypeFont smallFont = instance.getFontManager().getFont(10);

        String nameText = target.getName().getString();
        nameFont.drawStringWithShadow(context, nameText, x + 43, y + 4, Color.WHITE.getRGB());

        float barWidth = Math.max(60, nameFont.getStringWidth(nameText) - 5);
        Render2D.drawRect(context, x + 43, y + 15, barWidth, 4f, Color.BLACK.getRGB());

        Color healthColor = Color.getHSBColor(Math.max(0.0F, Math.min(1.0F, health)) / 3.0F, 1.0F, 1.0F);
        Render2D.drawRect(context, x + 43.5f, y + 15.5f, Math.max(0, (barWidth - 1f) * health), 3f, healthColor.getRGB());

        for (int i = 1; i < 10; i++) {
            float step = (barWidth - 1f) / 10f * i;
            Render2D.drawRect(context, x + 43.5f + step, y + 15.5f, 0.5f, 3f, Color.BLACK.getRGB());
        }

        int dist = mc.player != null ? (int) mc.player.distanceTo(target) : 0;
        String textInfo = "HP: " + (int) (target.getHealth() + target.getAbsorptionAmount()) + " | Dist: " + dist;
        smallFont.drawStringWithShadow(context, textInfo, x + 43, y + 19, Color.WHITE.getRGB());

        float itemX = x + 42;
        float itemY = y + 25;

        ItemStack[] displayItems = new ItemStack[] {
                target.getEquippedStack(EquipmentSlot.HEAD),
                target.getEquippedStack(EquipmentSlot.CHEST),
                target.getEquippedStack(EquipmentSlot.LEGS),
                target.getEquippedStack(EquipmentSlot.FEET),
                target.getMainHandStack(),
                target.getOffHandStack()
        };

        for (ItemStack items : displayItems) {
            if (items != null && !items.isEmpty()) {
                Render2D.drawItem(context, items, itemX, itemY);
                itemX += 15;
            }
        }
    }

    public static void render(DrawContext context, LivingEntity target, float x, float y, float alpha) {
        Render2D.setGlobalAlpha(alpha);
        try {
            render(context, target, x, y);
        } finally {
            Render2D.setGlobalAlpha(1.0f);
        }
    }

    public static float getWidth(LivingEntity target) {
        if (mc.player == null) return 115;

        float nameWidth = instance.getFontManager().getTahomaFont(16).getStringWidth(target.getName().getString()) + 48;
        String textInfo = "HP: " + (int) (target.getHealth() + target.getAbsorptionAmount()) + " | Dist: " + mc.player.distanceTo(target);
        float infoWidth = instance.getFontManager().getFont(10).getStringWidth(textInfo) + 48;
        long itemCount = java.util.Arrays.stream(new ItemStack[] {
                target.getEquippedStack(EquipmentSlot.HEAD),
                target.getEquippedStack(EquipmentSlot.CHEST),
                target.getEquippedStack(EquipmentSlot.LEGS),
                target.getEquippedStack(EquipmentSlot.FEET),
                target.getMainHandStack(),
                target.getOffHandStack()
        }).filter(item -> item != null && !item.isEmpty()).count();

        float itemsWidth = itemCount > 0 ? (itemCount * 15) + 48 : 0;
        return Math.max(115, Math.max(nameWidth, Math.max(infoWidth, itemsWidth)));
    }

    public static float getHeight() {
        return 45f;
    }
}
