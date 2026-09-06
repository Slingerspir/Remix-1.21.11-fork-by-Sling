package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class ItemTags extends Module {
    private final NumberValue scale = new NumberValue("Scale", 0.25f, 0.1f, 0.5f, 0.01f);
    private final BoolValue allItems = new BoolValue("All Items", false);
    private final BoolValue godItems = new BoolValue("God Items", true);
    private final BoolValue diamond = new BoolValue("Diamond", true);
    private final BoolValue gold = new BoolValue("Gold", true);
    private final BoolValue iron = new BoolValue("Iron", true);
    private final BoolValue enderPearl = new BoolValue("Ender Pearl", true);
    private final BoolValue goldenApple = new BoolValue("Golden Apple", true);

    private final Map<ItemEntity, Vec3d> screenPositions = new HashMap<>();

    public ItemTags() {
        super("ItemTags", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        screenPositions.clear();
        for (ItemEntity entity : mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(32, 32, 32), e -> true)) {
            Vec3d pos = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() + 0.3, entity.getZ());
            Vec3d screen = ProjectUtil.worldSpaceToScreenSpace(pos, event.getProjectionMatrix(), event.getModelViewMatrix());
            if (screen.x != 0 || screen.y != 0 || screen.z != 0) {
                screenPositions.put(entity, screen);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        TrueTypeFont font = instance.getFontManager().getFont((int) (14 * scale.getValue()));

        for (Map.Entry<ItemEntity, Vec3d> entry : screenPositions.entrySet()) {
            ItemEntity entity = entry.getKey();
            if (entity == null || entity.isRemoved()) continue;

            Item item = entity.getStack().getItem();
            if (!shouldShow(item)) continue;

            String name = entity.getStack().getName().getString();
            int count = entity.getStack().getCount();
            String text = count > 1 ? name + " x" + count : name;
            int color = isGodItem(item) ? 0xFFFF5555 : 0xFFFFFFFF;

            Vec3d screen = entry.getValue();
            float x = (float) screen.x;
            float y = (float) screen.y;
            float w = font.getStringWidth(text);

            Render2D.drawRect(event.getContext(), x - w / 2 - 2, y - 4, w + 4, 9, 0x80000000);
            font.drawStringWithShadow(event.getContext(), text, x - w / 2, y - 2, color);
        }
    }

    private boolean shouldShow(Item item) {
        if (allItems.getValue()) return true;
        if (godItems.getValue() && isGodItem(item)) return true;
        if (diamond.getValue() && item == Items.DIAMOND) return true;
        if (gold.getValue() && (item == Items.GOLD_INGOT || item == Items.GOLD_NUGGET)) return true;
        if (iron.getValue() && item == Items.IRON_INGOT) return true;
        if (enderPearl.getValue() && item == Items.ENDER_PEARL) return true;
        if (goldenApple.getValue() && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return true;
        return false;
    }

    private boolean isGodItem(Item item) {
        return item == Items.NETHERITE_INGOT
                || item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.TOTEM_OF_UNDYING;
    }
}
