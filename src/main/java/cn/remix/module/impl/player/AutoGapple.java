package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.SlowEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public class AutoGapple extends Module {
    private final NumberValue health = new NumberValue("Health", 10, 1, 20, .5);
    private static final int EAT_TICKS = 32;
    private static final int GAPPLE_COLOR = 0xFFFFA500;
    public boolean eating = false;
    public int eatTick = 0;
    private float hudProgress;

    public AutoGapple() {
        super("AutoGapple", Category.Player);
    }

    @Override
    public void onEnable() {
        eating = false;
        eatTick = 0;
    }

    @EventTarget
    public void onSlow(SlowEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (eating && mc.player.isUsingItem()) {
            event.setCancelled(true);
            mc.player.setSprinting(true);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        if (eating) {
            eatTick++;
        }

        if (eatTick >= EAT_TICKS) {
            reset();
        }

        ItemStack offHandStack = mc.player.getOffHandStack();
        if (isGapple(offHandStack)) {
            if (mc.player.getHealth() <= health.getValue() && !eating) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        ItemStack offHandStack = mc.player.getOffHandStack();
        if (isGapple(offHandStack)) {

            if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
                if (packet.getHand() == Hand.OFF_HAND) {
                    eating = true;
                }
            }

            if (event.getPacket() instanceof PlayerActionC2SPacket packet) {
                if (packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
                    ItemStack currentOffStack = mc.player.getOffHandStack();
                    if (isGapple(currentOffStack)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || !eating) return;

        TrueTypeFont titleFont = instance.getFontManager().getBoldFont(18);
        TrueTypeFont fractionFont = instance.getFontManager().getBoldFont(6);
        String title = "Gapple";
        String fraction = Math.min(eatTick, EAT_TICKS) + "/" + EAT_TICKS;

        float x = (mc.getWindow().getScaledWidth() - titleFont.getStringWidth(title)) / 2.0f;
        float y = mc.getWindow().getScaledHeight() / 2.0f + 42.0f;
        int alpha = 220;
        titleFont.drawStringWithShadow(event.getContext(), title, x, y, ColorUtil.applyAlpha(GAPPLE_COLOR, alpha));

        float targetProgress = Math.min(eatTick, EAT_TICKS) / (float) EAT_TICKS;
        hudProgress += (targetProgress - hudProgress) * 0.18f;

        float barWidth = Math.max(54.0f, titleFont.getStringWidth(title));
        float barHeight = 3.0f;
        float barX = (mc.getWindow().getScaledWidth() - barWidth) / 2.0f;
        float barY = y + titleFont.getHeight() + 8.0f;
        float fractionX = barX + barWidth - fractionFont.getStringWidth(fraction);
        float fractionY = barY - fractionFont.getHeight() - 2.0f;

        fractionFont.drawStringWithShadow(event.getContext(), fraction, fractionX, fractionY, ColorUtil.applyAlpha(0xFFFFFFFF, alpha));
        Render2D.drawRect(event.getContext(), barX, barY, barWidth, barHeight, ColorUtil.applyAlpha(0xFFFFFFFF, Math.round(alpha * 0.2f)));
        Render2D.drawRect(event.getContext(), barX, barY, barWidth * hudProgress, barHeight, ColorUtil.applyAlpha(GAPPLE_COLOR, Math.round(alpha * 0.85f)));
    }

    private boolean isGapple(ItemStack stack) {
        return stack != null && (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private void reset() {
        eatTick = 0;
        eating = false;
        hudProgress = 0.0f;
    }
}
