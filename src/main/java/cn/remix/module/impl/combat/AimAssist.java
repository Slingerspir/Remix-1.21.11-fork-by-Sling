package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.management.RotationManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.RotationUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AimAssist extends Module {
    private final BoolValue attackPlayer = new BoolValue("Attack Player", true);
    private final BoolValue attackInvisible = new BoolValue("Attack Invisible", false);
    private final BoolValue attackAnimals = new BoolValue("Attack Animals", false);
    private final BoolValue attackMobs = new BoolValue("Attack Mobs", false);
    private final BoolValue clickOnly = new BoolValue("Click Only", true);
    private final BoolValue silent = new BoolValue("Silent Aim", true);
    private final NumberValue rotateSpeed = new NumberValue("Rotation Speed", 10, 1, 90, 1);
    private final NumberValue aimRange = new NumberValue("Aim Range", 5.0f, 1, 6, 0.1f);
    private final NumberValue fov = new NumberValue("FoV", 360, 10, 360, 1);
    private final ModeValue priority = new ModeValue("Priority", "Range", "Health", "FoV", "Range", "None");

    private LivingEntity target;

    public AimAssist() {
        super("AimAssist", Category.Combat);
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (clickOnly.getValue() && !mc.options.attackKey.isPressed()) {
            target = null;
            return;
        }

        target = findTarget();
        if (target == null) return;

        float[] rotations = RotationUtil.getRotations(target.getEyePos());
        if (rotations == null) return;

        if (silent.getValue()) {
            RotationManager.setRotations(rotations, rotateSpeed.getValue(), cn.remix.management.movement.MovementCorrection.Silent);
        } else {
            mc.player.setYaw(rotations[0]);
            mc.player.setPitch(rotations[1]);
        }
    }

    private LivingEntity findTarget() {
        List<LivingEntity> candidates = new ArrayList<>();

        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (!isValidTarget(entity)) continue;
            if (RotationUtil.getDistanceToEntity(entity) > aimRange.getValue()) continue;
            if (!isInFov(entity)) continue;
            candidates.add(entity);
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(switch (priority.getValue()) {
            case "Health" -> Comparator.comparingDouble(LivingEntity::getHealth);
            case "FoV" -> Comparator.comparingDouble(e -> fovDifference(e));
            default -> Comparator.comparingDouble(RotationUtil::getDistanceToEntity);
        });

        return candidates.getFirst();
    }

    private boolean isInFov(LivingEntity entity) {
        float[] rot = RotationUtil.getRotations(entity.getEyePos());
        if (rot == null) return true;
        return Math.abs(MathHelper.wrapDegrees(rot[0] - mc.player.getYaw())) <= fov.getValue() / 2.0f;
    }

    private float fovDifference(LivingEntity entity) {
        float[] rot = RotationUtil.getRotations(entity.getEyePos());
        if (rot == null) return 360.0f;
        return Math.abs(MathHelper.wrapDegrees(rot[0] - mc.player.getYaw()));
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isDead()) return false;
        if (!EntityUtil.isSelected(entity)) return false;
        if (entity.isInvisible() && !attackInvisible.getValue()) return false;
        if (entity instanceof PlayerEntity && !attackPlayer.getValue()) return false;
        if (entity instanceof AnimalEntity && !attackAnimals.getValue()) return false;
        if (entity instanceof MobEntity && !(entity instanceof AnimalEntity) && !attackMobs.getValue()) return false;
        return true;
    }
}
