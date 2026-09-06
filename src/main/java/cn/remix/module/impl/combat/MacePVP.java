package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.player.ClickSlotUtil;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.ItemUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import lombok.Getter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.*;

@Getter
public final class MacePVP extends Module {
    private final NumberValue targetRange = new NumberValue("Target Range", 14, 3, 40, 1);
    private final NumberValue attackRange = new NumberValue("Attack Range", 3.0f, 2, 5, 0.1f);
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 110, 20, 180, 5);
    private final NumberValue aimJitter = new NumberValue("Aim Jitter", 2, 0, 8, 0.5f);
    private final NumberValue minFall = new NumberValue("Min Fall", 1.5f, 1.5f, 10, 0.5f);
    private final NumberValue attackReaction = new NumberValue("Attack Reaction", 250, 100, 800, 25);

    private final BoolValue autoMace = new BoolValue("Auto Mace", true);
    private final BoolValue autoArmor = new BoolValue("Auto Armor", true);

    private final BoolValue autoFly = new BoolValue("Auto Fly", true);
    private final NumberValue flyRange = new NumberValue("Fly Range", 8, 3, 24, 1);
    private final NumberValue diveRange = new NumberValue("Dive Range", 10, 4, 24, 1);
    private final BoolValue autoElytra = new BoolValue("Auto Elytra", true);
    private final BoolValue elytraInOffhand = new BoolValue("Elytra In Offhand", true);
    private final BoolValue reEquipElytra = new BoolValue("Re-equip Elytra", true);
    private final BoolValue autoElytraBounce = new BoolValue("Elytra Bounce", true);
    private final NumberValue bounceHeight = new NumberValue("Bounce Height", 6, 2, 20, 1);

    private final BoolValue autoWindJump = new BoolValue("Auto Wind Jump", true);
    private final NumberValue windJumpCooldown = new NumberValue("Wind Jump Cooldown", 1500, 500, 5000, 100);
    private final BoolValue autoPearlCombo = new BoolValue("Auto Pearl Combo", true);
    private final NumberValue pearlComboHeight = new NumberValue("Pearl Combo Height", 6, 3, 20, 1);
    private final NumberValue pearlDelay = new NumberValue("Pearl Delay", 100, 0, 500, 10);

    private final BoolValue autoSwap = new BoolValue("Auto Swap", true);
    private final BoolValue breakShield = new BoolValue("Break Shield", true);

    private final BoolValue autoShield = new BoolValue("Auto Shield", true);
    private final NumberValue shieldRange = new NumberValue("Shield Range", 6, 3, 12, 1);

    private final BoolValue autoHeal = new BoolValue("Auto Heal", true);
    private final NumberValue healHealth = new NumberValue("Heal Health", 10, 2, 20, 1);
    private final BoolValue useGapple = new BoolValue("Use Golden Apple", true);
    private final BoolValue autoTotem = new BoolValue("Auto Totem", true);
    private final NumberValue totemHealth = new NumberValue("Totem Health", 3, 1, 10, 1);

    private final BoolValue dodge = new BoolValue("Dodge", true);
    private final NumberValue dodgeHealth = new NumberValue("Dodge Health", 6, 1, 20, 1);
    private final BoolValue autoStrafe = new BoolValue("Auto Strafe", true);
    private final BoolValue autoJump = new BoolValue("Auto Jump", true);

    private final BoolValue render = new BoolValue("Render", true);

    private final TimerUtil strafeTimer = new TimerUtil();
    private final TimerUtil jumpTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil eatTimer = new TimerUtil();
    private final TimerUtil healCooldownTimer = new TimerUtil();
    private final TimerUtil postSmashTimer = new TimerUtil();
    private final TimerUtil windJumpTimer = new TimerUtil();
    private final TimerUtil windJumpCooldownTimer = new TimerUtil();
    private final TimerUtil pearlComboTimer = new TimerUtil();
    private final TimerUtil elytraBounceTimer = new TimerUtil();
    private final TimerUtil flyTimer = new TimerUtil();
    private final TimerUtil jitterTimer = new TimerUtil();
    private final TimerUtil swapTimer = new TimerUtil();
    private final TimerUtil shieldTimer = new TimerUtil();

    private LivingEntity target;
    private int strafeDir = 1;
    private boolean committed;
    private boolean smashDone;
    private boolean eating;
    private int preEatSlot = -1;
    private boolean shieldActive;

    private int windJumpState;
    private int pearlComboState;
    private int elytraBounceState;
    private int flyState;
    private int pumpPhase;
    private int swapState;

    private float aimOffsetYaw;
    private float aimOffsetPitch;

    public MacePVP() {
        super("MacePVP", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        mc.options.useKey.setPressed(false);
        target = null;
        committed = false;
        smashDone = false;
        eating = false;
        preEatSlot = -1;
        shieldActive = false;
        windJumpState = 0;
        pearlComboState = 0;
        elytraBounceState = 0;
        flyState = 0;
        pumpPhase = 0;
        swapState = 0;
        strafeTimer.reset();
        jumpTimer.reset();
        attackTimer.reset();
        eatTimer.reset();
        healCooldownTimer.reset();
        postSmashTimer.reset();
        windJumpTimer.reset();
        windJumpCooldownTimer.reset();
        pearlComboTimer.reset();
        elytraBounceTimer.reset();
        flyTimer.reset();
        jitterTimer.reset();
        swapTimer.reset();
        shieldTimer.reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive() || mc.player.isSpectator()) {
            reset();
            return;
        }

        if (!eating && isSmashThreat()) {
            if (!shieldActive) startShieldBlock();
        } else if (shieldActive) {
            stopShieldBlock();
        }

        if (autoTotem.getValue() && shouldUseTotem()) {
            equipTotem();
        }

        if (shouldHeal()) {
            if (!eating) startEating();
            else continueEating();
            return;
        }
        if (eating) stopEating();

        updateTarget();
        if (target == null) {
            resetCombatStates();
            manageArmor();
            ensureSword();
            return;
        }

        if (strafeTimer.hasTimeElapsed(getStrafeInterval())) {
            strafeDir = -strafeDir;
            strafeTimer.reset();
        }

        if (swapState == 1 && mc.player.isOnGround()) {
            tickSwap();
        } else if (swapState == 2 && mc.player.isOnGround()) {
            tickSwapBack();
        }

        if (autoFly.getValue() && shouldFly()) {
            tickFly();
            manageArmor();
            return;
        }

        if (mc.player.isGliding() && isWearingElytra()) {
            if (!committed && shouldCommitSmash(target)) {
                ensureMace();
                unequipElytra();
                committed = true;
                smashDone = false;
            }
            tickElytraBounce();
            return;
        }

        if (committed) {
            handleCommitted();
        } else if (windJumpState == 1) {
            if (!mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
                switchToWindCharge();
            }
            if (windJumpTimer.hasTimeElapsed(80)) {
                throwHeldItem();
                windJumpState = 2;
                windJumpTimer.reset();
            }
        } else if (windJumpState == 2) {
            handleCombat();
            if (windJumpTimer.hasTimeElapsed(2500) || mc.player.isOnGround()) {
                windJumpState = 0;
                windJumpCooldownTimer.reset();
                ensureMace();
            }
        } else if (pearlComboState == 1) {
            if (pearlComboTimer.hasTimeElapsed(80)) {
                int slot = ItemUtil.findItemSlot(Items.ENDER_PEARL);
                if (slot != -1) {
                    ItemUtil.switchToSlot(slot);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }
                pearlComboState = 2;
                pearlComboTimer.reset();
            }
        } else if (pearlComboState == 2) {
            if (!mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
                switchToWindCharge();
            }
            if (pearlComboTimer.hasTimeElapsed(pearlDelay.getValue().longValue() + (long) (Math.random() * 40))) {
                throwHeldItem();
                pearlComboState = 3;
                pearlComboTimer.reset();
            }
        } else if (pearlComboState == 3) {
            ensureMace();
            handleCombat();
            if (mc.player.isOnGround() || pearlComboTimer.hasTimeElapsed(3000)) {
                pearlComboState = 0;
            }
        } else if (swapState == 0) {
            if (shouldStartPearlCombo()) {
                pearlComboState = 1;
                pearlComboTimer.reset();
            } else if (shouldStartWindJump()) {
                windJumpState = 1;
                windJumpTimer.reset();
            } else {
                handleCombat();
            }
        }

        manageArmor();
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) return;
        if (eating) return;

        if (windJumpState == 1 || elytraBounceState == 1) {
            rotateTowards(mc.player.getYaw(), 90, rotationSpeed.getValue());
            return;
        }
        if (pearlComboState == 1 || pearlComboState == 2) {
            rotateTowards(mc.player.getYaw(), -90, rotationSpeed.getValue());
            return;
        }
        if (flyState == 2) {
            if (pumpPhase == 0) {
                rotateTowards(mc.player.getYaw(), 90, rotationSpeed.getValue());
            } else {
                rotateTowards(mc.player.getYaw(), -50, rotationSpeed.getValue());
            }
            return;
        }
        if (flyState == 3 && target != null) {
            aimAt(target.getBoundingBox().getCenter());
            return;
        }
        if (target == null) return;

        if (RotationUtil.getDistanceToEntity(target) <= attackRange.getValue() + 1.0f) {
            aimAt(target.getBoundingBox().getCenter());
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        event.setSneaking(false);

        if (eating) {
            event.setForward(0);
            event.setStrafe(0);
            return;
        }
        if (flyState == 1 || flyState == 2 || flyState == 3) {
            event.setJumping(true);
            event.setForward(1);
            event.setStrafe(0);
            return;
        }
        if (flyState == 4) {
            event.setJumping(false);
            event.setForward(0);
            event.setStrafe(0);
            return;
        }
        if (windJumpState == 1) {
            event.setJumping(true);
            event.setSneaking(false);
            return;
        }
        if (windJumpState == 2 || pearlComboState != 0 || mc.player.isGliding()) return;

        if (target == null) return;

        float dist = RotationUtil.getDistanceToEntity(target);
        boolean lowHp = mc.player.getHealth() + mc.player.getAbsorptionAmount() < dodgeHealth.getValue();

        if (lowHp && dodge.getValue()) {
            event.setForward(-1);
            event.setStrafe(strafeDir);
            maybeJump(event);
        } else if (dist > attackRange.getValue()) {
            event.setForward(1);
            event.setStrafe(0);
            maybeJump(event);
        } else if (autoStrafe.getValue()) {
            event.setForward(0);
            event.setStrafe(strafeDir);
            maybeJump(event);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || mc.player == null) return;

        if (target != null) {
            Render3D.drawBox(event.getMatrixStack(), target.getBoundingBox(), ColorUtil.applyAlpha(Color.RED.getRGB(), 80), false);
        }
    }

    private void tickFly() {
        if (flyState > 0 && flyState < 4 && !committed && !isWearingElytra()) {
            equipElytra();
        }

        switch (flyState) {
            case 0 -> {
                if (autoElytra.getValue() && !isWearingElytra()) equipElytra();
                pumpPhase = 0;
                if (mc.player.isGliding()) {
                    flyState = 2;
                } else {
                    flyState = 1;
                }
                flyTimer.reset();
            }
            case 1 -> {
                if (mc.player.isGliding()) {
                    flyState = 2;
                    flyTimer.reset();
                } else if (flyTimer.hasTimeElapsed(2500)) {
                    flyState = 0;
                }
            }
            case 2 -> {
                if (pumpPhase == 0) {
                    if (!mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
                        switchToWindCharge();
                    }
                    if (flyTimer.hasTimeElapsed(60)) {
                        throwHeldItem();
                        pumpPhase = 1;
                        flyTimer.reset();
                    }
                } else {
                    if (target != null && mc.player.distanceTo(target) <= diveRange.getValue()) {
                        flyState = 3;
                        pumpPhase = 0;
                        flyTimer.reset();
                    } else if (isAboutToLand()) {
                        pumpPhase = 0;
                        flyTimer.reset();
                    }
                }
            }
            case 3 -> {
                if (target == null || mc.player.distanceTo(target) > targetRange.getValue() + 6) {
                    flyState = 4;
                    return;
                }
                if (shouldCommitSmash(target)) {
                    ensureMace();
                    unequipElytra();
                    committed = true;
                    smashDone = false;
                    flyState = 0;
                    return;
                }
                tickElytraBounce();
            }
            case 4 -> {
                if (mc.player.isOnGround()) {
                    flyState = 0;
                }
            }
        }
    }

    private boolean isAboutToLand() {
        if (mc.player.getVelocity().y >= 0) return false;
        double groundY = getGroundY(mc.player.getX(), mc.player.getZ(), mc.player.getY());
        double height = mc.player.getY() - groundY;
        return height < bounceHeight.getValue() && height > 1.0;
    }

    private boolean shouldFly() {
        if (!autoFly.getValue() || !autoElytra.getValue() || target == null) return false;
        if (!isWearingElytra() && ItemUtil.getBestElytraSlot() == -1 && !mc.player.getOffHandStack().isOf(Items.ELYTRA)) return false;
        if (ItemUtil.findItemSlot(Items.WIND_CHARGE) == -1) return false;
        if (mc.player.isGliding()) return true;
        if (flyState != 0) return true;
        if (committed || windJumpState != 0 || pearlComboState != 0 || swapState != 0) return false;
        if (eating || shieldActive) return false;
        return mc.player.distanceTo(target) > flyRange.getValue();
    }

    private void tickElytraBounce() {
        if (!autoElytraBounce.getValue() || !mc.player.isGliding() || !isWearingElytra()) return;
        if (committed) return;

        if (elytraBounceState == 1) {
            if (!mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
                switchToWindCharge();
            }
            if (elytraBounceTimer.hasTimeElapsed(80)) {
                throwHeldItem();
                elytraBounceState = 2;
                elytraBounceTimer.reset();
            }
            return;
        }
        if (elytraBounceState == 2) {
            if (elytraBounceTimer.hasTimeElapsed(1500) || !mc.player.isGliding()) {
                elytraBounceState = 0;
                ensureMace();
            }
            return;
        }

        if (mc.player.getVelocity().y < 0) {
            if (target != null && shouldCommitSmash(target)) return;
            double groundY = getGroundY(mc.player.getX(), mc.player.getZ(), mc.player.getY());
            double height = mc.player.getY() - groundY;
            if (height < bounceHeight.getValue() && height > 1.0) {
                if (ItemUtil.findItemSlot(Items.WIND_CHARGE) != -1) {
                    elytraBounceState = 1;
                    elytraBounceTimer.reset();
                }
            }
        }
    }

    private boolean shouldStartWindJump() {
        if (!autoWindJump.getValue() || target == null) return false;
        if (mc.player.isGliding() || pearlComboState != 0 || committed || swapState != 0) return false;
        if (!mc.player.isOnGround() || mc.player.fallDistance > 0.1f) return false;
        if (!windJumpCooldownTimer.hasTimeElapsed(windJumpCooldown.getValue().longValue() + (long) (Math.random() * 500))) return false;
        if (RotationUtil.getDistanceToEntity(target) > attackRange.getValue() + 0.5f) return false;
        return ItemUtil.findItemSlot(Items.WIND_CHARGE) != -1;
    }

    private boolean shouldStartPearlCombo() {
        if (!autoPearlCombo.getValue() || target == null) return false;
        if (mc.player.isGliding() || windJumpState != 0 || committed || swapState != 0) return false;
        if (!mc.player.isOnGround()) return false;
        if (target.getY() - mc.player.getY() < pearlComboHeight.getValue()) return false;
        return ItemUtil.findItemSlot(Items.ENDER_PEARL) != -1 && ItemUtil.findItemSlot(Items.WIND_CHARGE) != -1;
    }

    private void handleCombat() {
        if (breakShield.getValue() && target.isBlocking()) {
            int axeSlot = ItemUtil.getBestToolSlot(ItemTags.AXES);
            if (axeSlot != -1 && attackTimer.hasTimeElapsed(getReactionTime())) {
                ItemUtil.switchToSlot(axeSlot);
                doAttack(target);
                ensureSword();
                attackTimer.reset();
            }
            return;
        }

        if (RotationUtil.getDistanceToEntity(target) > attackRange.getValue()) return;

        boolean falling = mc.player.fallDistance > minFall.getValue();

        if (falling) {
            ensureMace();
            if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;
            if (!attackTimer.hasTimeElapsed(getReactionTime())) return;
            doAttack(target);
            attackTimer.reset();
            smashDone = true;
            postSmashTimer.reset();
            return;
        }

        boolean hasSword = ensureSword();
        if (!hasSword) {
            ensureMace();
            if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;
        }
        if (mc.player.getAttackCooldownProgress(0) < 0.6f) return;
        if (!attackTimer.hasTimeElapsed(getReactionTime())) return;

        doAttack(target);
        attackTimer.reset();
        if (hasSword && autoSwap.getValue()) {
            swapState = 1;
            swapTimer.reset();
        }
    }

    private void handleCommitted() {
        ensureMace();
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) {
            if (!autoMace.getValue() || ItemUtil.getBestMaceSlot(-1) == -1) {
                committed = false;
                return;
            }
            return;
        }

        if (RotationUtil.getDistanceToEntity(target) <= attackRange.getValue()) {
            boolean falling = mc.player.fallDistance > minFall.getValue();
            if (falling ? attackTimer.hasTimeElapsed(getReactionTime()) : mc.player.getAttackCooldownProgress(0) >= 0.6f && attackTimer.hasTimeElapsed(getReactionTime())) {
                doAttack(target);
                attackTimer.reset();
                smashDone = true;
                postSmashTimer.reset();
            }
        }

        if (smashDone && reEquipElytra.getValue() && postSmashTimer.hasTimeElapsed(400)) {
            reEquipElytra();
            committed = false;
            smashDone = false;
            return;
        }

        if (mc.player.isOnGround() || !target.isAlive() || target.isDead()) {
            committed = false;
            smashDone = false;
        }
    }

    private void tickSwap() {
        int slot = ItemUtil.getBestMaceSlot(-1);
        if (slot != -1) {
            ItemUtil.switchToSlot(slot);
        }

        if (swapTimer.hasTimeElapsed(220)) {
            if (target != null && RotationUtil.getDistanceToEntity(target) <= attackRange.getValue()) {
                doAttack(target);
                attackTimer.reset();
            }
            ensureSword();
            swapState = 2;
            swapTimer.reset();
        }
    }

    private void tickSwapBack() {
        if (swapTimer.hasTimeElapsed(220)) {
            swapState = 0;
        }
    }

    private long getReactionTime() {
        return attackReaction.getValue().longValue() + (long) (Math.random() * 150);
    }

    private void doAttack(LivingEntity entity) {
        AttackEvent attackEvent = new AttackEvent(entity);
        instance.getEventManager().call(attackEvent);
        if (attackEvent.isCancelled()) return;

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void switchToWindCharge() {
        int slot = ItemUtil.findItemSlot(Items.WIND_CHARGE);
        if (slot != -1) ItemUtil.switchToSlot(slot);
    }

    private void throwHeldItem() {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void aimAt(Vec3d point) {
        float[] rot = RotationUtil.getRotations(point);
        if (rot == null) return;

        if (jitterTimer.hasTimeElapsed(200 + (long) (Math.random() * 300))) {
            aimOffsetYaw = (float) ((Math.random() - 0.5) * 2 * aimJitter.getValue());
            aimOffsetPitch = (float) ((Math.random() - 0.5) * 2 * aimJitter.getValue());
            jitterTimer.reset();
        }

        rotateTowards(rot[0] + aimOffsetYaw, rot[1] + aimOffsetPitch, rotationSpeed.getValue());
    }

    private void rotateTowards(float targetYaw, float targetPitch, float speed) {
        float yawDelta = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - mc.player.getPitch());
        float distance = Math.max(Math.abs(yawDelta), Math.abs(pitchDelta));

        float currentSpeed = speed;
        if (distance < 25) {
            currentSpeed = Math.max(25, speed * (distance / 25));
        }

        float yawStep = MathHelper.clamp(yawDelta, -currentSpeed, currentSpeed);
        float pitchStep = MathHelper.clamp(pitchDelta, -currentSpeed, currentSpeed);
        mc.player.setYaw(mc.player.getYaw() + yawStep);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchStep, -90, 90));
    }

    private boolean shouldCommitSmash(LivingEntity entity) {
        double groundY = getGroundY(mc.player.getX(), mc.player.getZ(), mc.player.getY());
        Vec3d pos = mc.player.getEntityPos();
        Vec3d vel = mc.player.getVelocity();
        double fall = mc.player.fallDistance;
        Vec3d center = entity.getBoundingBox().getCenter();
        double reachSq = attackRange.getValue() * attackRange.getValue();
        float minF = minFall.getValue();
        boolean passedNear = false;

        for (int tick = 0; tick < 120; tick++) {
            vel = new Vec3d(vel.x * 0.91f, (vel.y - 0.08) * 0.98f, vel.z * 0.91f);
            pos = pos.add(vel);
            fall += Math.max(0, -vel.y);

            if (pos.squaredDistanceTo(center) <= reachSq && fall > minF) {
                passedNear = true;
            }
            if (pos.y <= groundY || pos.y < center.y - 8) break;
        }

        if (passedNear) return true;

        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        return (dx * dx + dz * dz) <= targetRange.getValue() * targetRange.getValue() && fall > minF;
    }

    private double getGroundY(double x, double z, double fromY) {
        if (mc.world == null) return mc.player.getY() - 100;
        RaycastContext ctx = new RaycastContext(
                new Vec3d(x, Math.max(fromY, mc.world.getBottomY() + 1), z),
                new Vec3d(x, mc.world.getBottomY(), z),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        HitResult result = mc.world.raycast(ctx);
        return result.getType() == HitResult.Type.MISS ? mc.world.getBottomY() : result.getPos().y;
    }

    private boolean isSmashThreat() {
        if (!autoShield.getValue() || mc.player == null) return false;

        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (entity == mc.player || !(entity instanceof PlayerEntity) || entity.isDead() || !entity.isAlive()) continue;
            if (!EntityUtil.isSelected(entity)) continue;

            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            double dy = entity.getY() - mc.player.getY();
            double distSq = dx * dx + dz * dz;
            double range = shieldRange.getValue();
            if (dy > 1.0 && distSq < range * range) {
                if (entity.getVelocity().y < -0.25 || entity.fallDistance > 2.0f) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startShieldBlock() {
        if (mc.player.getOffHandStack().isOf(Items.SHIELD)) {
            shieldActive = true;
            mc.options.useKey.setPressed(true);
            shieldTimer.reset();
            return;
        }

        int shieldSlot = ItemUtil.findItemSlot(Items.SHIELD);
        if (shieldSlot == -1) return;
        ClickSlotUtil.swap(shieldSlot, 40);
        shieldActive = true;
        shieldTimer.reset();
    }

    private void stopShieldBlock() {
        mc.options.useKey.setPressed(false);
        shieldActive = false;
    }

    private boolean shouldUseTotem() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp > totemHealth.getValue()) return false;
        if (isSmashThreat()) return true;
        if (mc.player.fallDistance > mc.player.getSafeFallDistance() + 2) return true;
        return hp <= 2;
    }

    private void equipTotem() {
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        int slot = ItemUtil.findItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) return;
        ClickSlotUtil.swap(slot, 40);
    }

    private void equipElytra() {
        if (isWearingElytra()) return;

        if (mc.player.getOffHandStack().isOf(Items.ELYTRA)) {
            ClickSlotUtil.swap(6, 40);
            return;
        }
        int slot = ItemUtil.getBestElytraSlot();
        if (slot != -1) {
            ClickSlotUtil.shiftClick(slot);
        }
    }

    private void ensureMace() {
        if (mc.player.getMainHandStack().isOf(Items.MACE)) return;
        if (!autoMace.getValue()) return;

        int slot = ItemUtil.getBestMaceSlot(-1);
        if (slot != -1) ItemUtil.switchToSlot(slot);
    }

    private boolean ensureSword() {
        if (mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) return true;
        int slot = ItemUtil.getBestWeaponSlot();
        if (slot == -1) return false;
        ItemUtil.switchToSlot(slot);
        return true;
    }

    private boolean isWearingElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void unequipElytra() {
        if (!autoElytra.getValue() || !isWearingElytra()) return;

        ItemStack offhand = mc.player.getOffHandStack();
        boolean offhandFree = offhand.isEmpty() || offhand.isOf(Items.ELYTRA) || ItemUtil.isArmor(offhand);

        if (elytraInOffhand.getValue() && offhandFree) {
            ClickSlotUtil.swap(6, 40);
        } else {
            ClickSlotUtil.shiftClick(6);
        }
    }

    private void reEquipElytra() {
        if (!autoElytra.getValue() || isWearingElytra()) return;

        if (mc.player.getOffHandStack().isOf(Items.ELYTRA)) {
            ClickSlotUtil.swap(6, 40);
            return;
        }
        int slot = ItemUtil.getBestElytraSlot();
        if (slot != -1) {
            ClickSlotUtil.shiftClick(slot);
        }
    }

    private void manageArmor() {
        if (!autoArmor.getValue() || mc.player.isGliding()) return;

        if (mc.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) tryEquip(EquipmentSlot.HEAD, 5);
        if (mc.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) tryEquip(EquipmentSlot.LEGS, 7);
        if (mc.player.getEquippedStack(EquipmentSlot.FEET).isEmpty()) tryEquip(EquipmentSlot.FEET, 8);
        if (flyState == 0 && !committed && mc.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty() && !isWearingElytra()) tryEquip(EquipmentSlot.CHEST, 6);
    }

    private void tryEquip(EquipmentSlot slot, int armorSlotId) {
        int best = ItemUtil.getBestArmorSlot(slot);
        if (best >= 9 && best <= 44) {
            ClickSlotUtil.shiftClick(best);
        }
    }

    private boolean shouldHeal() {
        if (!autoHeal.getValue() || mc.player == null) return false;
        if (shieldActive) return false;
        if (!healCooldownTimer.hasTimeElapsed(4000)) return false;
        return mc.player.getHealth() + mc.player.getAbsorptionAmount() < healHealth.getValue();
    }

    private void startEating() {
        int slot = findHealItemSlot();
        if (slot == -1) {
            eating = false;
            return;
        }
        preEatSlot = mc.player.getInventory().getSelectedSlot();
        ItemUtil.switchToSlot(slot);
        eating = true;
        eatTimer.reset();
        mc.options.useKey.setPressed(true);
    }

    private void continueEating() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp >= healHealth.getValue() || eatTimer.hasTimeElapsed(2500)) {
            stopEating();
        }
    }

    private void stopEating() {
        mc.options.useKey.setPressed(false);
        healCooldownTimer.reset();
        if (eating && preEatSlot != -1) {
            if (autoMace.getValue() && ItemUtil.getBestMaceSlot(-1) != -1) {
                ItemUtil.switchToSlot(ItemUtil.getBestMaceSlot(-1));
            } else if (preEatSlot >= 0 && preEatSlot <= 8) {
                ItemUtil.switchToSlot(36 + preEatSlot);
            }
        }
        eating = false;
        preEatSlot = -1;
    }

    private int findHealItemSlot() {
        if (useGapple.getValue()) {
            int slot = ItemUtil.findItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            if (slot != -1) return slot;
            slot = ItemUtil.findItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1) return slot;
        }
        for (int i = 36; i <= 44; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.contains(DataComponentTypes.FOOD)) return i;
        }
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.contains(DataComponentTypes.FOOD)) return i;
        }
        return -1;
    }

    private void maybeJump(MoveInputEvent event) {
        if (autoJump.getValue() && mc.player.isOnGround() && jumpTimer.hasTimeElapsed(getJumpInterval())) {
            event.setJumping(true);
            jumpTimer.reset();
        }
    }

    private long getStrafeInterval() {
        return 300 + (long) (Math.random() * 500);
    }

    private long getJumpInterval() {
        return 400 + (long) (Math.random() * 600);
    }

    private void resetCombatStates() {
        committed = false;
        smashDone = false;
        windJumpState = 0;
        pearlComboState = 0;
        swapState = 0;
        if (!mc.player.isGliding()) elytraBounceState = 0;
        if (flyState >= 1 && flyState <= 3) flyState = 4;
    }

    private void updateTarget() {
        target = instance.getTargetManager().getTargets().stream()
                .filter(EntityUtil::isSelected)
                .filter(entity -> !entity.isDead() && entity.isAlive() && entity.getHealth() > 0)
                .filter(entity -> mc.player.distanceTo(entity) <= targetRange.getValue())
                .min((a, b) -> Float.compare(RotationUtil.getDistanceToEntity(a), RotationUtil.getDistanceToEntity(b)))
                .orElse(null);

        setSuffix(target == null ? "" : target.getName().getString());
    }
}
