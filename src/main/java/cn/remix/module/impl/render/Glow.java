package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.util.player.EntityUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;

public final class Glow extends Module {
    private final BoolValue players = new BoolValue("Player", true);
    private final BoolValue items = new BoolValue("Items", false);
    private final BoolValue mobs = new BoolValue("Mobs", false);
    private final BoolValue animals = new BoolValue("Animals", false);
    private final BoolValue arrows = new BoolValue("Arrows", false);

    public Glow() {
        super("Glow", Category.Render);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) {
                continue;
            }

            boolean shouldGlow = shouldGlowEntity(entity);
            if (entity.isGlowing() != shouldGlow) {
                entity.setGlowing(shouldGlow);
            }
        }
    }

    private boolean shouldGlowEntity(Entity entity) {
        if (entity instanceof PlayerEntity && players.getValue() && EntityUtil.isSelected(entity, true, true, false, true)) {
            return true;
        }
        if (entity instanceof ItemEntity && items.getValue()) return true;
        if (entity instanceof AnimalEntity && animals.getValue()) return true;
        if (entity instanceof MobEntity && mobs.getValue() && !(entity instanceof AnimalEntity)) return true;
        if (entity instanceof ArrowEntity && arrows.getValue()) return true;
        return false;
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            if (entity.isGlowing()) {
                entity.setGlowing(false);
            }
        }
    }
}
