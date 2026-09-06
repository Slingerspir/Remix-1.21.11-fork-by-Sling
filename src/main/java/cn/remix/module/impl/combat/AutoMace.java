package cn.remix.module.impl.combat;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.ClickSlotUtil;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.ItemUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import lombok.Getter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public final class AutoMace extends Module {
    private final ModeValue priority = new ModeValue("Priority", "Distance", "Distance", "Health", "LivingTime", "Armor");
    private final NumberValue aimRange = new NumberValue("Aim Range", 8, 3, 20, 1);
    private final NumberValue attackRange = new NumberValue("Attack Range", 3.0f, 2, 6, 0.1f);
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 10, 1, 10, 1);
    private final BoolValue silentAim = new BoolValue("Silent Aim", true);
    private final ModeValue movementFixMode = new ModeValue("MovementFix Mode", "Silent", "None", "Silent", "Strict");
    private final NumberValue minFall = new NumberValue("Min Fall", 1.5f, 1.5f, 10, 0.5f);
    private final BoolValue autoSwitch = new BoolValue("Auto Switch Mace", true);
    private final BoolValue autoElytra = new BoolValue("Auto Elytra", true);
    private final BoolValue elytraInOffhand = new BoolValue("Elytra In Offhand", true);
    private final BoolValue reEquipElytra = new BoolValue("Re-equip Elytra", true);
    private final BoolValue renderLanding = new BoolValue("Render Landing", true);
    private final BoolValue breakShield = new BoolValue("Break Shield", true);
    private final BoolValue debug = new BoolValue("Debug", false);

    private final List<LivingEntity> targets = new ArrayList<>();
    private final TimerUtil postSmashTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil committedTimer = new TimerUtil();

    private LivingEntity target;
    private float[] rotations;
    private boolean committed;
    private boolean smashDone;
    private Vec3d predictedLanding;

    public AutoMace() {
        super("AutoMace", Category.Combat);
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
        targets.clear();
        target = null;
        rotations = null;
        committed = false;
        smashDone = false;
        predictedLanding = null;
        postSmashTimer.reset();
        attackTimer.reset();
        committedTimer.reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive() || mc.player.isSpectator()) {
            reset();
            return;
        }

        if (autoElytra.getValue() && !committed && !isWearingElytra()) {
            equipElytra();
        }

        updateTargets();
        if (target == null) {
            reset();
            return;
        }

        if (mc.player.isGliding() && isWearingElytra()) {
            if (!committed && shouldCommitSmash(target)) {
                unequipElytra();
                committed = true;
                smashDone = false;
                committedTimer.reset();
                debugLog("commit elytra smash");
            }
            return;
        }

        if (committed) {
            handleCommittedState();
        } else {
            tickCombat();
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) {
            rotations = null;
            return;
        }

        if (target != null && RotationUtil.getDistanceToEntity(target) <= aimRange.getValue()) {
            rotations = RotationUtil.nearestRotation(target.getBoundingBox());
        } else {
            rotations = null;
        }

        if (!silentAim.getValue() && rotations != null) {
            applyDirectRotation(rotations);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null || target == null || mc.player.isGliding()) return;

        if (RotationUtil.getDistanceToEntity(target) > attackRange.getValue()) {
            event.setForward(1);
        }
        event.setSneaking(false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!renderLanding.getValue() || predictedLanding == null) return;

        Vec3d p = predictedLanding;
        Render3D.drawBox(event.getMatrixStack(),
                new Box(p.x - 0.3, p.y - 0.3, p.z - 0.3, p.x + 0.3, p.y + 0.3, p.z + 0.3),
                ColorUtil.applyAlpha(Color.CYAN.getRGB(), 120),
                false);
    }

    private void tickCombat() {
        if (RotationUtil.getDistanceToEntity(target) > attackRange.getValue()) return;

        // 破盾同理：不需要等任何蓄力，能秒切就直接开始
        if (breakShield.getValue() && target.isBlocking()) {
            int axeSlot = ItemUtil.getBestToolSlot(ItemTags.AXES);
            if (axeSlot >= 36 && axeSlot <= 44 && attackTimer.hasTimeElapsed(80)) {
                doBurst(axeSlot - 36);
                attackTimer.reset();
                debugLog("shield broken (axe)");
            }
            return;
        }

        if (!attackTimer.hasTimeElapsed(getAttackInterval())) return;

        int maceSlot = findHotbarMaceSlot();
        if (maceSlot == -1) return;

        // 不等剑蓄力：能秒切就直接开始
        doBurst(maceSlot - 36);
        attackTimer.reset();
        debugLog("mace burst");
    }

    // 秒切：切到重锤，同一瞬间重锤攻击，然后切回剑
    // 触发即开始，不等待任何武器蓄力（攻击较晚的问题就是等蓄力造成的）
    private void doBurst(int hotbarIndex) {
        int swordSlot = mc.player.getInventory().getSelectedSlot();

        realSwitchTo(hotbarIndex);
        doAttack(target);
        realSwitchTo(swordSlot);
    }

    private void handleCommittedState() {
        if (committedTimer.hasTimeElapsed(3000)) {
            reEquipElytra();
            committed = false;
            smashDone = false;
            debugLog("committed timeout -> re-equip elytra");
            return;
        }

        if (RotationUtil.getDistanceToEntity(target) <= attackRange.getValue()) {
            if (attackTimer.hasTimeElapsed(getReactionTime())) {
                int maceSlot = findHotbarMaceSlot();
                if (maceSlot != -1) {
                    doBurst(maceSlot - 36);
                    attackTimer.reset();
                    smashDone = true;
                    postSmashTimer.reset();
                    debugLog("elytra smash");
                }
            }
        }
        if (smashDone && reEquipElytra.getValue() && postSmashTimer.hasTimeElapsed(400)) {
            reEquipElytra();
            committed = false;
            smashDone = false;
            debugLog("re-equipped elytra");
            return;
        }

        if (mc.player.isOnGround() || !target.isAlive() || target.isDead()) {
            committed = false;
            smashDone = false;
        }
    }

    private void doAttack(LivingEntity entity) {
        AttackEvent event = new AttackEvent(entity);
        instance.getEventManager().call(event);
        if (event.isCancelled()) return;

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
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
        Vec3d landing = null;

        for (int tick = 0; tick < 120; tick++) {
            vel = new Vec3d(vel.x * 0.91f, (vel.y - 0.08) * 0.98f, vel.z * 0.91f);
            pos = pos.add(vel);
            fall += Math.max(0, -vel.y);

            if (pos.squaredDistanceTo(center) <= reachSq && fall > minF) {
                passedNear = true;
            }
            if (pos.y <= groundY || pos.y < center.y - 8) {
                landing = pos;
                break;
            }
        }
        if (landing == null) landing = pos;
        predictedLanding = landing;

        if (passedNear) return true;

        double dx = landing.x - center.x;
        double dz = landing.z - center.z;
        return (dx * dx + dz * dz) <= aimRange.getValue() * aimRange.getValue() && fall > minF;
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

    private boolean isWearingElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void realSwitchTo(int hotbarIndex) {
        if (hotbarIndex >= 0 && hotbarIndex <= 8) {
            mc.player.getInventory().setSelectedSlot(hotbarIndex);
            PacketUtil.sendPacketNoEvent(new UpdateSelectedSlotC2SPacket(hotbarIndex));
        }
    }

    private int findHotbarMaceSlot() {
        int bestSlot = -1;
        float bestScore = -Float.MAX_VALUE;
        for (int i = 36; i <= 44; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.MACE)) {
                float score = scoreMace(stack, target);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private void debugLog(String msg) {
        if (!debug.getValue()) return;
        try {
            File dir = new File(Client.name, "debug");
            if (!dir.exists()) dir.mkdirs();
            try (PrintWriter writer = new PrintWriter(new FileWriter(new File(dir, "automace.log"), true))) {
                writer.println(System.currentTimeMillis() + " [AutoMace] " + msg);
            }
        } catch (Exception ignored) {}
    }

    private void unequipElytra() {
        if (!isWearingElytra()) return;

        ItemStack offhand = mc.player.getOffHandStack();
        boolean offhandFree = offhand.isEmpty() || offhand.isOf(Items.ELYTRA) || ItemUtil.isArmor(offhand);

        if (elytraInOffhand.getValue() && offhandFree) {
            ClickSlotUtil.swap(6, 40);
        } else {
            ClickSlotUtil.shiftClick(6);
        }
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

    private void reEquipElytra() {
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

    private long getReactionTime() {
        return 150 + (long) (Math.random() * 120);
    }

    private long getAttackInterval() {
        return 250 + (long) (Math.random() * 150);
    }

    private int findBestMaceSlot(LivingEntity entity) {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        float bestScore = -Float.MAX_VALUE;

        for (int i = 36; i <= 44; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.MACE)) {
                float score = scoreMace(stack, entity);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.MACE)) {
                float score = scoreMace(stack, entity);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private float scoreMace(ItemStack stack, LivingEntity entity) {
        int density = 0, breach = 0, smite = 0, bane = 0, windBurst = 0;
        ItemEnchantmentsComponent ench = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        if (ench != null && !ench.isEmpty()) {
            for (RegistryEntry<Enchantment> entry : ench.getEnchantments()) {
                int level = ench.getLevel(entry);
                if (entry.matchesKey(Enchantments.DENSITY)) density = level;
                else if (entry.matchesKey(Enchantments.BREACH)) breach = level;
                else if (entry.matchesKey(Enchantments.SMITE)) smite = level;
                else if (entry.matchesKey(Enchantments.BANE_OF_ARTHROPODS)) bane = level;
                else if (entry.matchesKey(Enchantments.WIND_BURST)) windBurst = level;
            }
        }

        double h = mc.player.fallDistance;
        if (h <= minFall.getValue()) h = 0;

        double raw = Math.min(6, 2 * h) + Math.min(8, h) + (1 + 0.5f * density) * h;
        if (entity != null && entity.getType().isIn(EntityTypeTags.UNDEAD)) raw += 2.5f * smite;
        if (entity != null && entity.getType().isIn(EntityTypeTags.ARTHROPOD)) raw += 2.5f * bane;
        if (h > 0) raw *= 1.5f;

        float reduction = entity == null ? 0 : MathHelper.clamp(entity.getArmor() * 0.04f, 0, 0.8f);
        reduction = Math.max(0, reduction - 0.15f * breach);
        double effective = raw * (1 - reduction);

        return (float) (effective + windBurst * 3.0f);
    }

    private void applyDirectRotation(float[] targetRotation) {
        float step = (float) getRotationSpeedDegrees();
        float yawDelta = MathHelper.wrapDegrees(targetRotation[0] - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(targetRotation[1] - mc.player.getPitch());
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDelta, -step, step));
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(pitchDelta, -step, step), -90, 90));
    }

    public double getRotationSpeedDegrees() {
        float v = rotationSpeed.getValue();
        return v >= 10 ? 180.0 : v * 18.0;
    }

    private void updateTargets() {
        targets.clear();
        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (EntityUtil.isSelected(entity)
                    && RotationUtil.getDistanceToEntity(entity) <= aimRange.getValue()
                    && !entity.isDead() && entity.isAlive() && entity.getHealth() > 0) {
                targets.add(entity);
            }
        }

        if (!targets.isEmpty()) {
            targets.sort(sortTargets(priority.getValue()));
        }
        target = targets.isEmpty() ? null : targets.getFirst();
        setSuffix(target == null ? "" : priority.getValue());
    }

    private Comparator<LivingEntity> sortTargets(String sort) {
        return switch (sort) {
            case "Health" -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case "LivingTime" -> Comparator.comparingInt((LivingEntity entity) -> entity.age).reversed();
            case "Armor" -> Comparator.comparingInt(LivingEntity::getArmor);
            default -> Comparator.comparingDouble(RotationUtil::getDistanceToEntity);
        };
    }
}
