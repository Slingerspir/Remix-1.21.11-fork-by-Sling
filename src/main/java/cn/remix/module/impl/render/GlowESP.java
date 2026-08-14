package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.TpAura;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;

import java.awt.*;

public final class GlowESP extends Module {
    private static final int PLAYER_DEFAULT = Color.WHITE.getRGB();
    private static final int HOSTILE_COLOR = new Color(230, 45, 45).getRGB();
    private static final int ANGERABLE_COLOR = new Color(245, 220, 70).getRGB();
    private static final int PASSIVE_COLOR = new Color(0, 105, 45).getRGB();
    private static final int BOSS_COLOR = new Color(255, 135, 135).getRGB();
    private static final int MISC_COLOR = new Color(145, 145, 145).getRGB();
    private static final int FRIEND_COLOR = new Color(125, 255, 150).getRGB();
    private static final int ENEMY_COLOR = new Color(255, 55, 55).getRGB();
    private static final int OTHER_PLAYER_COLOR = new Color(75, 145, 255).getRGB();
    private static final int HURT_COLOR = new Color(255, 45, 45).getRGB();

    private final BoolValue showPlayer = new BoolValue("Show Player", true);
    private final BoolValue showMob = new BoolValue("Show Mob", true);
    private final BoolValue showPassive = new BoolValue("Show Passive", false);
    private final BoolValue showAngerable = new BoolValue("Show Angerable", false);
    private final BoolValue showMisc = new BoolValue("Show Misc", false);
    private final ModeValue playerColor = new ModeValue("Player Color", "Single", showPlayer::getValue, "Single", "Team", "Highlight 1", "Highlight 2");
    private final ColorValue singleColor = new ColorValue("Single Color", Color.WHITE, () -> showPlayer.getValue() && playerColor.is("Single"));
    private final BoolValue highlightHurt = new BoolValue("Highlight Hurt", true);

    public GlowESP() {
        super("GlowESP", Category.Render);
    }

    public boolean shouldGlow(Entity entity) {
        if (entity == null || entity == mc.player || entity.isRemoved()) {
            return false;
        }

        TpAura tpAura = getModule(TpAura.class);
        if (tpAura.isEnabled() && tpAura.getHighlightTarget().is("Glow") && tpAura.getTarget() == entity) {
            return true;
        }

        if (entity instanceof LivingEntity living && (!living.isAlive() || living.isSpectator())) {
            return false;
        }

        if (entity instanceof PlayerEntity) {
            if (!showPlayer.getValue()) return false;
            return !playerColor.is("Highlight 2") || isFriendOrEnemy(entity);
        }

        if (entity instanceof LivingEntity living) {
            if (isBoss(living)) return showMob.getValue();
            if (isAngerable(living)) return showAngerable.getValue();
            if (isHostile(living)) return showMob.getValue();
            if (isPassive(living)) return showPassive.getValue();
            return false;
        }

        return showMisc.getValue();
    }

    public int getGlowColor(Entity entity) {
        TpAura tpAura = getModule(TpAura.class);
        if (tpAura.isEnabled() && tpAura.getHighlightTarget().is("Glow") && tpAura.getTarget() == entity) {
            return new Color(255, 105, 180).getRGB();
        }

        if (highlightHurt.getValue() && entity instanceof LivingEntity living && living.hurtTime > 0) {
            return HURT_COLOR;
        }

        if (entity instanceof PlayerEntity player) {
            return getPlayerColor(player);
        }

        if (entity instanceof LivingEntity living) {
            if (isBoss(living)) return BOSS_COLOR;
            if (isAngerable(living)) return ANGERABLE_COLOR;
            if (isHostile(living)) return HOSTILE_COLOR;
            if (isPassive(living)) return PASSIVE_COLOR;
        }

        return MISC_COLOR;
    }

    private int getPlayerColor(PlayerEntity player) {
        String name = player.getName().getString();
        if (playerColor.is("Team")) {
            Team team = player.getScoreboardTeam();
            Integer teamColor = team == null || team.getColor() == null ? null : team.getColor().getColorValue();
            return teamColor == null ? PLAYER_DEFAULT : teamColor;
        }

        if (playerColor.is("Highlight 1") || playerColor.is("Highlight 2")) {
            if (instance.getFriendManager().isFriend(name)) return FRIEND_COLOR;
            if (instance.getFriendManager().isEnemy(name)) return ENEMY_COLOR;
            return playerColor.is("Highlight 1") ? OTHER_PLAYER_COLOR : PLAYER_DEFAULT;
        }

        return singleColor.getValue().getRGB();
    }

    private boolean isFriendOrEnemy(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        String name = player.getName().getString();
        return instance.getFriendManager().isFriend(name) || instance.getFriendManager().isEnemy(name);
    }

    private boolean isBoss(LivingEntity entity) {
        return entity instanceof WardenEntity || entity instanceof WitherEntity || entity instanceof EnderDragonEntity;
    }

    private boolean isAngerable(LivingEntity entity) {
        return entity instanceof Angerable;
    }

    private boolean isHostile(LivingEntity entity) {
        return entity instanceof Monster || entity instanceof SlimeEntity || entity instanceof GhastEntity || entity instanceof ShulkerEntity;
    }

    private boolean isPassive(LivingEntity entity) {
        return entity instanceof PassiveEntity
                || entity instanceof AnimalEntity
                || entity instanceof SquidEntity
                || entity instanceof BatEntity
                || entity instanceof IronGolemEntity;
    }
}
