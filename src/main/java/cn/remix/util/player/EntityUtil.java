package cn.remix.util.player;

import cn.remix.module.impl.player.AntiBot;
import cn.remix.module.impl.player.Targets;
import cn.remix.module.impl.player.Teams;
import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

@UtilityClass
public class EntityUtil implements IMinecraft {

    public boolean isSelected(Entity entity) {
        return isSelected(entity, true, true,true, true);
    }

    public boolean isSelected(Entity entity, boolean checkBot, boolean checkTeams, boolean checkFriend, boolean checkSelf) {
        if (!(entity instanceof LivingEntity livingEntity)) return false;

        Targets targets = instance.getModuleManager().getModule(Targets.class);
        Teams teams = instance.getModuleManager().getModule(Teams.class);
        AntiBot antiBot = instance.getModuleManager().getModule(AntiBot.class);

        if (!targets.getTarget().isEnabled("Invisible") && livingEntity.isInvisible()) {
            return false;
        }

        if (!livingEntity.isAlive() || livingEntity.isSpectator()) {
            return false;
        }

        if (livingEntity instanceof PlayerEntity player) {
            if (checkSelf && player.equals(mc.player)) {
                return false;
            }

            if (checkFriend && instance.getFriendManager().isFriend(player.getName().getString())) {
                return false;
            }

            if (checkTeams && teams.isEnabled() && teams.isTeam(player)) {
                return false;
            }

            if (checkBot && antiBot.isEnabled() && antiBot.isBot(player)) {
                return false;
            }

            return targets.getTarget().isEnabled("Player");
        }

        boolean isMob = targets.getTarget().isEnabled("Mob") && isMob(livingEntity);
        boolean isAnimal = targets.getTarget().isEnabled("Animal") && isAnimal(livingEntity);
        boolean isVillager = targets.getTarget().isEnabled("Villager") && livingEntity instanceof VillagerEntity;

        return isMob || isAnimal || isVillager;
    }

    public boolean isAnimal(final Entity entity) {
        return entity instanceof AnimalEntity
                || entity instanceof SquidEntity
                || entity instanceof IronGolemEntity
                || entity instanceof BatEntity;
    }

    public boolean isMob(final Entity entity) {
        return entity instanceof Monster
                || entity instanceof SlimeEntity
                || entity instanceof GhastEntity
                || entity instanceof ShulkerEntity
                || entity instanceof EnderDragonEntity;
    }

    public boolean isOverVoid(double x, double y, double z) {
        if (mc.player == null || mc.world == null) return false;

        RaycastContext context = new RaycastContext(
                new Vec3d(x, y, z),
                new Vec3d(x, mc.world.getBottomY(), z),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockPos hitPos = mc.world.raycast(context).getBlockPos();
        return mc.world.getBlockState(hitPos).isOf(Blocks.AIR) || hitPos.getY() <= mc.world.getBottomY();
    }
}
