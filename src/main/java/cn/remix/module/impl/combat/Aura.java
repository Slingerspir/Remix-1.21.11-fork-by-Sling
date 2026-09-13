package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.management.RotationManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.player.AntiBot;
import cn.remix.module.impl.player.Teams;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.MathUtil;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.HitRotationUtil;
import cn.remix.util.player.RayCastUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.util.render.Render3D;
import lombok.Getter;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Getter
public class Aura extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Heypixel2");
    private final ModeValue targetMode = new ModeValue("Target Mode", "Single", "Single", "Switch");
    private final NumberValue switchDelay = new NumberValue("Switch Delay", 200, 0, 1000, 50, () -> targetMode.is("Switch"));
    private final ModeValue priority = new ModeValue("Priority", "Distance", "Distance", "Health", "Fov", "LivingTime", "Armor");
    private final ModeValue attackMode = new ModeValue("Combat Mode", "1.8", "1.8", "1.9+");
    private final NumberValue maxCps = new NumberValue("Max CPS", 10, 1, 20, 1, () -> attackMode.is("1.8"));
    private final NumberValue minCps = new NumberValue("Min CPS", 7, 1, 20, 1, () -> attackMode.is("1.8"));
    private final BoolValue keepSwing = new BoolValue("Keep Swing", false, () -> attackMode.is("1.9+"));
    private final NumberValue attackRange = new NumberValue("Range", 3, 3, 8, .1);
    private final NumberValue blockRange = new NumberValue("Block Range", 4, 3, 8, .1);
    private final NumberValue wallRange = new NumberValue("Wall Range", 0, 0, 8, .1);
    private final NumberValue rotationRange = new NumberValue("Rotation Range", 4, 3, 8, .1);
    private final ModeValue rotationMode = new ModeValue("Rotation Mode", "Normal", "None", "Normal", "Cubecraft", "Derp");
    private final NumberValue derpSpinSpeed = new NumberValue("Derp Spin Speed", 30.0f, -40.0f, 40.0f, 1.0f, () -> rotationMode.is("Derp"));
    private final NumberValue derpPitch = new NumberValue("Derp Pitch", 30.0f, -40.0f, 40.0f, 1.0f, () -> rotationMode.is("Derp"));
    private final ModeValue autoBlockMode = new ModeValue("AutoBlock Mode", "None", "None", "Fake", "Use Item", "Vanilla");
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 0, 180, 5);
    private final ModeValue movementFixMode = new ModeValue("MovementFix Mode", "Silent", "None", "Silent", "Strict");
    private final BoolValue rayCast = new BoolValue("Ray Cast", false);
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);
    private final BoolValue multiAttack = new BoolValue("Multi Attack", false);
    private final BoolValue preferBaby = new BoolValue("Prefer Baby", false);
    private final BoolValue moreParticles = new BoolValue("More Particles", false);
    private final BoolValue targetEsp = new BoolValue("Target ESP", true);

    // ===================== Heypixel2（KillAura 移植）设置 =====================
    private final ModeValue h2Delay = new ModeValue("H2 Delay Mode", "1.8", () -> mode.is("Heypixel2"), "1.8", "1.9");
    private final NumberValue h2MinAps = new NumberValue("H2 Min APS", 9, 1, 20, 1, () -> mode.is("Heypixel2"));
    private final NumberValue h2MaxAps = new NumberValue("H2 Max APS", 12, 1, 20, 1, () -> mode.is("Heypixel2"));
    private final NumberValue h2AimRange = new NumberValue("H2 Aim Range", 3, 1, 6, .1f, () -> mode.is("Heypixel2"));
    private final BoolValue h2ThroughWalls = new BoolValue("H2 Through Walls", false, () -> mode.is("Heypixel2"));
    private final NumberValue h2ThroughWallsRange = new NumberValue("H2 Wall Range", 3, 1, 6, .1f, () -> mode.is("Heypixel2") && h2ThroughWalls.getValue());
    private final BoolValue h2Prediction = new BoolValue("H2 Prediction", true, () -> mode.is("Heypixel2"));
    private final NumberValue h2EnemyDelay = new NumberValue("H2 Enemy Delay", 4, 1, 5, 1, () -> mode.is("Heypixel2") && h2Prediction.getValue());
    private final NumberValue h2SelfDelay = new NumberValue("H2 Self Delay", 2, 1, 5, 1, () -> mode.is("Heypixel2") && h2Prediction.getValue());
    private final BoolValue h2InfSwitch = new BoolValue("H2 Infinity Switch", false, () -> mode.is("Heypixel2"));
    private final NumberValue h2SwitchSize = new NumberValue("H2 Switch Size", 1, 1, 5, 1, () -> mode.is("Heypixel2") && !h2InfSwitch.getValue());
    private final NumberValue h2SwitchDelay = new NumberValue("H2 Switch Delay", 1, 1, 10, 1, () -> mode.is("Heypixel2"));
    private final NumberValue h2HurtTime = new NumberValue("H2 Hurt Time", 10, 0, 10, 1, () -> mode.is("Heypixel2"));
    private final NumberValue h2Fov = new NumberValue("H2 FoV", 360, 10, 360, 1, () -> mode.is("Heypixel2"));
    private final NumberValue h2Drift = new NumberValue("H2 Drift", 0.1f, 0, 5, .1f, () -> mode.is("Heypixel2"));
    private final NumberValue h2Jitter = new NumberValue("H2 Jitter", 0.02f, 0, 1, .01f, () -> mode.is("Heypixel2"));
    private final BoolValue h2AttackPlayer = new BoolValue("H2 Attack Player", true, () -> mode.is("Heypixel2"));
    private final BoolValue h2AttackInvisible = new BoolValue("H2 Attack Invisible", true, () -> mode.is("Heypixel2"));
    private final BoolValue h2AttackMobs = new BoolValue("H2 Attack Mobs", false, () -> mode.is("Heypixel2"));
    private final BoolValue h2AttackAnimals = new BoolValue("H2 Attack Animals", false, () -> mode.is("Heypixel2"));

    private final Heypixel2 heypixel2 = new Heypixel2();

    private final List<LivingEntity> targets = new ArrayList<>();
    private final TimerUtil switchTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private LivingEntity target = null;
    private float[] rotations = null;
    private float derpYaw;

    private boolean renderBlock = false;
    private boolean blocking = false;

    public Aura() {
        super("Aura", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        switchTimer.reset();
        attackTimer.reset();
        rotations = null;
        derpYaw = 0.0f;
        targets.clear();
        target = null;
        heypixel2.reset();

        unBlock();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || check()) return;

        if (mode.is("Heypixel2")) {
            heypixel2.update();
            return;
        }

        updateTargets();
        if (targets.isEmpty()) {
            reset();
            return;
        }

        selectTarget();

        if (target != null) {
            if (canAttack(target)) {
                if (keepSwing.getValue()) {
                    mc.player.handSwinging = true;
                }

                if (mc.player.getAttackCooldownProgress(.5f) < 1 && attackMode.is("1.9+")) return;

                if (attackTimer.hasTimeElapsed(700L / getCps())) {
                    doAttack(target);
                    attackTimer.reset();
                }

                if (multiAttack.getValue()) {
                    int attacked = 0;
                    for (LivingEntity extra : targets) {
                        if (extra == target || attacked >= 2) break;
                        if (canAttack(extra)) {
                            doAttack(extra);
                            attacked++;
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!targetEsp.getValue() || mc.player == null || mc.world == null) return;
        if (target == null || !target.isAlive() || target.isDead()) return;

        Render3D.drawOutlinedBox(event.getMatrixStack(), target.getBoundingBox(), 2.0, 0x80FF3333, false);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || check()) return;
        if (mode.is("Heypixel2")) return;

        if (event.isPost()) {
            if (target != null) {
                if (canBlock(target)) {
                    doBlock();
                } else {
                    unBlock();
                }
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || check()) return;

        if (mode.is("Heypixel2")) {
            heypixel2.computeRotations();
            return;
        }

        if (target != null) {
            if (RotationUtil.getDistanceToEntity(target) <= rotationRange.getValue()) {
                rotations = getRotations(target);
            } else {
                rotations = null;
            }
        } else {
            rotations = null;
        }
    }

    private float[] getRotations(LivingEntity target) {
        if (rotationMode.is("None")) {
            return null;
        }

        if (rotationMode.is("Derp")) {
            if (derpYaw >= 360.0f || derpYaw <= -360.0f) {
                derpYaw = 0.0f;
            }
            derpYaw += derpSpinSpeed.getValue();
            return new float[]{derpYaw, derpPitch.getValue()};
        }

        float[] targetRotations = RotationUtil.nearestRotation(target.getBoundingBox());
        if (targetRotations == null) return null;

        if (rotationMode.is("Cubecraft")) {
            targetRotations[0] += 180.0f;
        }

        return targetRotations;
    }

    private void selectTarget() {
        if (target == null || targets.isEmpty()) {
            switchTimer.setTime(0);
        }

        if (targetMode.is("Switch")) {
            if (switchTimer.hasTimeElapsed(switchDelay.getValue().longValue())) {
                int index = 0;
                if (targets.size() > 1) {
                    index = (int) (Math.random() * targets.size());
                }
                target = targets.isEmpty() ? null : targets.get(index);
                switchTimer.reset();
            }
        } else {
            target = targets.isEmpty() ? null : targets.getFirst();
        }

        setSuffix(targetMode.getValue());
    }

    private boolean canAttack(LivingEntity target) {
        Vec3d bestPoint = RotationUtil.getNearestPointBB(target.getBoundingBox());
        boolean canSee = RotationUtil.isVisible(bestPoint);
        float range = canSee ? attackRange.getValue() : wallRange.getValue();

        if (rayCast.getValue() && !RayCastUtil.overEntity(target)) {
            return false;
        }

        return !(RotationUtil.getDistanceToEntity(target) > range);
    }

    public boolean canBlock(LivingEntity target) {
        if (autoBlockMode.is("None") || target == null) return false;

        if (!isHoldingSword()) return false;

        return RotationUtil.getDistanceToEntity(target) <= blockRange.getValue();
    }

    private void doAttack(LivingEntity entity) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        AttackEvent event = new AttackEvent(target);
        instance.getEventManager().call(event);
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (keepSprint.getValue()) {
            mc.player.setSprinting(true);
        }

        if (moreParticles.getValue() && mc.world != null) {
            mc.world.addParticleClient(net.minecraft.particle.ParticleTypes.CRIT, false, false,
                    entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(),
                    (Math.random() - 0.5) * 0.5, (Math.random() - 0.5) * 0.5, (Math.random() - 0.5) * 0.5);
        }
    }

    private void doBlock() {
        if (mc.player == null || mc.world == null || autoBlockMode.is("None") || target == null) return;

        switch (autoBlockMode.getValue()) {
            case "Use Item" :
                mc.options.useKey.setPressed(true);
                blocking = true;
                break;

            case "Vanilla" :
                PacketUtil.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, RotationManager.currentRotations[0], RotationManager.currentRotations[1]));
                blocking = true;
                break;
        }

        renderBlock = true;
    }

    private void unBlock() {
        if (autoBlockMode.is("None")) return;

        if (blocking) {
            switch (autoBlockMode.getValue()) {
                case "Use Item":
                    mc.options.useKey.setPressed(false);
                    blocking = false;
                    break;

                case "Vanilla":
                    PacketUtil.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                    blocking = false;
                    break;
            }
        }

        renderBlock = false;
    }

    private void updateTargets() {
        if (mc.player == null || mc.world == null) return;

        targets.clear();
        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (filter(entity)) {
                targets.add(entity);
            }
        }

        if (!targets.isEmpty()) {
            targets.sort(sortTargets(priority.getValue()));
        }

        setSuffix(targetMode.getValue());
    }

    public Comparator<LivingEntity> sortTargets(final String priority) {
        Comparator<LivingEntity> base = switch (priority) {
            case "Health" -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case "Fov" -> Comparator.comparingDouble(RotationUtil::getRotationDifference);
            case "LivingTime" -> Comparator.comparingInt((LivingEntity entity) -> entity.age).reversed();
            case "Armor" -> Comparator.comparingInt(LivingEntity::getArmor);
            default -> Comparator.comparingDouble(RotationUtil::getDistanceToEntity);
        };

        if (preferBaby.getValue()) {
            return base.thenComparing(e -> e.isBaby() ? 0 : 1);
        }
        return base;
    }

    public boolean filter(LivingEntity entity) {
        if (mc.player == null || mc.world == null) return false;

        if (!EntityUtil.isSelected(entity)) {
            return false;
        }

        if (RotationUtil.getDistanceToEntity(entity) > rotationRange.getValue()) {
            return false;
        }

        return !entity.isDead() && entity.isAlive() && entity.getHealth() > 0;
    }

    private boolean check() {
        if (mc.player == null || mc.world == null) return true;

        if (getModule(Scaffold.class).isEnabled() && getModule(Scaffold.class).isCanRotation()) {
            return true;
        }

        return !mc.player.isAlive() || mc.player.isSpectator();
    }

    private long getCps() {
        long min = minCps.getValue().longValue();
        long max = maxCps.getValue().longValue();
        Velocity velocity = getModule(Velocity.class);

        if (velocity.isAttacking()) {
            return MathUtil.getRandomInRange(min - 5, max - 5);
        }

        return MathUtil.getRandomInRange(min, max);
    }

    private boolean isHoldingSword() {
        if (mc.player == null) return false;

        return mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
    }

    // =====================================================================
    // Heypixel2 —— KillAura 行为移植（目标筛选/预测/切换/APS/有机旋转）
    // =====================================================================
    private final class Heypixel2 {
        private final List<LivingEntity> h2Targets = new ArrayList<>();
        private final Random random = new Random();
        private final double[] driftFreq = new double[4];
        private final double[] driftPhase = new double[4];

        private LivingEntity h2Target;
        private HitRotationUtil.BestHit bestHit;
        private float[] rotation;
        private float attacks;
        private int attackTimes;
        private int targetIndex;
        private boolean seeded;

        void reset() {
            h2Targets.clear();
            h2Target = null;
            bestHit = null;
            rotation = null;
            attacks = 0.0f;
            attackTimes = 0;
            targetIndex = 0;
            seeded = false;
        }

        void update() {
            if (mc.player == null || mc.world == null) return;

            // 打开容器界面时挂起
            if (mc.currentScreen instanceof HandledScreen) {
                target = null;
                rotation = null;
                rotations = null;
                return;
            }

            updateTargets();
            if (h2Targets.isEmpty()) {
                target = null;
                rotation = null;
                rotations = null;
                attacks = 0.0f;
                return;
            }

            switchTarget();
            target = h2Target;
            if (h2Target == null) {
                rotation = null;
                rotations = null;
                return;
            }

            // 1.9 模式等待攻击冷却
            if (h2Delay.is("1.9") && mc.player.getAttackCooldownProgress(0.0f) < 0.95f) {
                attacks = 0.0f;
                return;
            }

            double min = h2MinAps.getValue().doubleValue();
            double max = h2MaxAps.getValue().doubleValue();
            float add = (float) (MathUtil.getRandomInRange((float) min, (float) max) / 20.0f);
            attacks += add;

            if (keepSprint.getValue() && mc.player.isSprinting() && shouldStopSprint(h2Target)) {
                mc.player.setSprinting(false);
            }

            while (attacks >= 1.0f) {
                attack(h2Target);
                attacks -= 1.0f;
            }

            if (multiAttack.getValue()) {
                int done = 0;
                for (LivingEntity extra : h2Targets) {
                    if (extra == h2Target || done >= 2) break;
                    if (isValidAttack(extra)) {
                        attack(extra);
                        done++;
                    }
                }
            }
        }

        void computeRotations() {
            if (mc.player == null || h2Target == null || h2Target.isDead()) {
                rotation = null;
                rotations = null;
                return;
            }

            bestHit = HitRotationUtil.getBestHit(h2Target);
            if (bestHit == null) {
                rotation = null;
                rotations = null;
                return;
            }

            float[] from = RotationManager.currentRotations != null
                    ? RotationManager.currentRotations
                    : new float[]{mc.player.getYaw(), mc.player.getPitch()};
            rotation = applyOrganicRotation(from, bestHit.rotation());
            rotations = rotation;
        }

        private void updateTargets() {
            h2Targets.clear();
            for (LivingEntity entity : instance.getTargetManager().getTargets()) {
                if (isValidTarget(entity) && isValidAttack(entity)) {
                    h2Targets.add(entity);
                }
            }

            if (h2Targets.isEmpty()) return;

            h2Targets.sort(sortTargets(priority.getValue()));

            if (preferBaby.getValue() && h2Targets.stream().anyMatch(LivingEntity::isBaby)) {
                h2Targets.removeIf(entity -> !entity.isBaby());
            }

            int limit = h2InfSwitch.getValue() ? h2Targets.size()
                    : Math.max(1, h2SwitchSize.getValue().intValue());
            if (h2Targets.size() > limit) {
                h2Targets.subList(limit, h2Targets.size()).clear();
            }

            switchTarget();
            setSuffix("Heypixel2");
        }

        private void switchTarget() {
            boolean switching = h2SwitchSize.getValue().intValue() > 1 || h2InfSwitch.getValue() || multiAttack.getValue();

            if (h2Targets.size() > 1) {
                boolean shouldSwitch = attackTimes >= h2SwitchDelay.getValue().intValue()
                        || bestHit == null || bestHit.distance() > 3.0;
                if (shouldSwitch) {
                    attackTimes = 0;
                    for (int i = 0; i < h2Targets.size(); i++) {
                        targetIndex = (targetIndex + 1) % h2Targets.size();
                        LivingEntity candidate = h2Targets.get(targetIndex);
                        HitRotationUtil.BestHit hit = HitRotationUtil.getBestHit(candidate);
                        if (hit != null && hit.distance() < 3.0) {
                            bestHit = hit;
                            break;
                        }
                    }
                }
            } else {
                targetIndex = 0;
            }

            if (switching) {
                h2Target = h2Targets.get(Math.min(targetIndex, h2Targets.size() - 1));
            } else {
                h2Target = h2Targets.getFirst();
            }
        }

        private boolean isValidTarget(LivingEntity entity) {
            if (entity == mc.player) return false;
            if (!EntityUtil.isSelected(entity)) return false;
            if (entity.isDead() || !entity.isAlive() || entity.getHealth() <= 0) return false;
            if (entity instanceof ArmorStandEntity) return false;

            AntiBot antiBot = getModule(AntiBot.class);
            if (antiBot.isEnabled() && antiBot.isBot(entity)) return false;

            Teams teams = getModule(Teams.class);
            if (teams.isEnabled() && teams.isTeam(entity)) return false;

            if (entity.isInvisible() && !h2AttackInvisible.getValue()) return false;

            if (entity instanceof PlayerEntity player) {
                if (!h2AttackPlayer.getValue()) return false;
                if (player.isSpectator()) return false;
                if (player.getWidth() < 0.5f || player.isSleeping()) return false;
            } else if (entity instanceof AnimalEntity || entity instanceof VillagerEntity) {
                if (!h2AttackAnimals.getValue()) return false;
            } else if (entity instanceof MobEntity) {
                if (!h2AttackMobs.getValue()) return false;
            }

            if (entity.hurtTime > h2HurtTime.getValue().intValue()) return false;

            return true;
        }

        private boolean isValidAttack(LivingEntity entity) {
            Vec3d eye = mc.player.getEyePos();
            Box box = entity.getBoundingBox();
            Vec3d closest = RotationUtil.getNearestPointBB(box);
            double distance = eye.distanceTo(closest);
            double aim = h2AimRange.getValue();

            if (distance > aim) {
                if (distance > 5.0) return false;
                if (!(h2Prediction.getValue() && predictDistance(entity) < aim)) return false;
            }

            boolean throughOk = h2ThroughWalls.getValue() && distance <= h2ThroughWallsRange.getValue();
            if (!throughOk && mc.world != null) {
                BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, closest,
                        RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) return false;
            }

            return HitRotationUtil.isEntityInFov(entity, h2Fov.getValue().floatValue());
        }

        private double predictDistance(LivingEntity entity) {
            double ticks = 2.0;
            if (entity instanceof PlayerEntity enemy) {
                ticks += Math.min(ping(mc.player) / 50.0, h2SelfDelay.getValue().doubleValue());
                ticks += Math.min(ping(enemy) / 50.0, h2EnemyDelay.getValue().doubleValue());
            }
            double ex = entity.getX() + (entity.getX() - entity.lastRenderX) * ticks;
            double ez = entity.getZ() + (entity.getZ() - entity.lastRenderZ) * ticks;
            double px = mc.player.getX() + (mc.player.getX() - mc.player.lastRenderX) * ticks;
            double pz = mc.player.getZ() + (mc.player.getZ() - mc.player.lastRenderZ) * ticks;
            return Math.hypot(ex - px, ez - pz);
        }

        private int ping(PlayerEntity player) {
            if (mc.getNetworkHandler() == null || player == null) return 0;
            var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            return entry == null ? 0 : entry.getLatency();
        }

        private boolean shouldStopSprint(LivingEntity entity) {
            return entity.getBoundingBox().getCenter().squaredDistanceTo(mc.player.getEyePos()) <= 12.25
                    && mc.player.getActiveItem().isEmpty()
                    && (!mc.player.isOnGround() || !mc.options.jumpKey.isPressed());
        }

        private void attack(LivingEntity entity) {
            if (mc.interactionManager == null || entity == null) return;

            AttackEvent event = new AttackEvent(entity);
            instance.getEventManager().call(event);
            if (event.isCancelled()) return;

            mc.interactionManager.attackEntity(mc.player, entity);
            mc.player.swingHand(Hand.MAIN_HAND);
            attackTimes++;

            if (keepSprint.getValue()) {
                mc.player.setSprinting(true);
            }

            if (moreParticles.getValue() && mc.world != null) {
                mc.world.addParticleClient(net.minecraft.particle.ParticleTypes.CRIT, false, false,
                        entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(),
                        (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
            }
        }

        private float[] applyOrganicRotation(float[] from, float[] to) {
            float speed = rotationSpeed.getValue().floatValue();
            float drift = h2Drift.getValue().floatValue();
            float jitter = h2Jitter.getValue().floatValue();

            float[] out = new float[]{from[0], from[1]};

            if (speed <= 0.0f) {
                out[0] = to[0];
                out[1] = to[1];
            } else {
                if (!seeded) {
                    seeded = true;
                    for (int i = 0; i < 4; i++) {
                        driftFreq[i] = 0.05 + random.nextDouble() * 0.35;
                        driftPhase[i] = random.nextDouble() * Math.PI * 2.0;
                    }
                }

                float dYaw = MathHelper.wrapDegrees(to[0] - from[0]);
                float dPitch = to[1] - from[1];
                double total = Math.hypot(dYaw, dPitch);

                if (total <= speed) {
                    out[0] = to[0];
                    out[1] = to[1];
                } else {
                    double scale = speed / total;
                    out[0] = (float) (from[0] + dYaw * scale);
                    out[1] = (float) (from[1] + dPitch * scale);
                }

                double t = System.currentTimeMillis() / 1000.0;
                double dx = Math.sin(t * driftFreq[0] + driftPhase[0]) * drift
                        + Math.cos(t * driftFreq[1] + driftPhase[1]) * drift * 0.5;
                double dy = Math.sin(t * driftFreq[2] + driftPhase[2]) * drift
                        + Math.cos(t * driftFreq[3] + driftPhase[3]) * drift * 0.5;
                out[0] += (float) (dx + (random.nextDouble() * 2.0 - 1.0) * jitter);
                out[1] += (float) (dy + (random.nextDouble() * 2.0 - 1.0) * jitter);
            }

            out[1] = MathHelper.clamp(out[1], -90.0f, 90.0f);
            return RotationUtil.applySensitivityPatch(out, from);
        }
    }
}
