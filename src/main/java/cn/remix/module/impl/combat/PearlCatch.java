package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PearlCatch —— 珍珠 + 风弹连招（Full 版）
 *
 * <p>手持剑右键时自动投出末影珍珠，并按官方物理实时计算，让风弹在半空中精确命中它，
 * 风弹爆炸把珍珠顶得更高更远，玩家再传送上去。
 *
 * <p>物理常量全部由 1.21.11 官方源码反编译核对：
 * <ul>
 *   <li>{@code ThrownEntity.tick()} = applyGravity() → applyDrag() → 位移，即每 tick
 *       {@code v = (v + (0,-0.03,0)) * 0.99}（珍珠）</li>
 *   <li>{@code EnderPearlItem.POWER = 1.5F}，出膛速度 = 视线方向 * 1.5</li>
 *   <li>{@code WindChargeItem.POWER = 1.5F}；风弹继承 {@code ExplosiveProjectileEntity}，
 *       {@code getDrag() == 1.0F} 且 {@code accelerationPower == 0}，因此<b>无重力无阻力</b>，
 *       严格按 {@code p += v * 1.5} 直线飞行</li>
 *   <li>两者都用 {@code spawnWithVelocity(..., POWER, 1.0F)} 生成 —— uncertainty = 1.0，
 *       也就是出膛方向带 {@code nextTriangular(0, 0.0172275)} 的随机抖动，<b>珍珠和风弹都有</b></li>
 *   <li>{@code ProjectileEntity.setVelocity(shooter, ...)} 会把 shooter 的位移加到弹射物速度上
 *       （水平恒加、垂直仅离地时加），所以跑动时风弹会整体偏移，必须在瞄准里扣掉</li>
 *   <li>风弹爆炸：{@code EXPLOSION_POWER = 1.2F}（判定半径 = 2.4 格），
 *       {@code knockbackModifier = 1.22F}，击退方向 = 爆炸点 → 珍珠，也就是<b>风弹自己的飞行方向</b>；
 *       倍率 = {@code (1 - 距离/2.4) * 伤害系数 * 1.22}</li>
 * </ul>
 *
 * <p>最后一条解释了整套连招的物理：<b>风弹朝哪个方向飞，珍珠就被往哪个方向顶</b>。
 * 所以想让珍珠"往上窜"，命中点就必须尽量高。
 */
public final class PearlCatch extends Module {

    // ===== 官方物理常量（1.21.11，反编译核对）=====
    /** ThrownEntity.getGravity() */
    private static final double GRAVITY = 0.03;
    /** ThrownEntity.applyDrag() 空中阻力 */
    private static final double DRAG = 0.99;
    /** EnderPearlItem.POWER */
    private static final double PEARL_POWER = 1.5;
    /** WindChargeItem.POWER */
    private static final double WIND_POWER = 1.5;
    /** 模拟上限（tick） */
    private static final int MAX_TICKS = 400;
    /** 拦截解可接受的误差（格）：超过这个值就宁可不出手，也不打空 */
    private static final double INTERCEPT_TOLERANCE = 0.05;
    /** 连招结束后视觉/命中点还继续画多久（ms） */
    private static final long VISUAL_HOLD_MS = 2200L;
    /** 真实轨迹最长记录多少个点 */
    private static final int TRAIL_LIMIT = 80;

    // ===== 设置 =====
    private final BoolValue requireLookUp = new BoolValue("Require Look Up", false);
    private final NumberValue lookUpAngle = new NumberValue("Look Up Angle", 0, 0, 89, 1, requireLookUp::getValue);
    private final BoolValue lineOfSight = new BoolValue("Line Of Sight", true);
    private final ModeValue timing = new ModeValue("Timing", "Smart", "Smart", "Instant", "Apex");
    private final NumberValue maxFlight = new NumberValue("Max Flight", 12, 4, 30, 1, () -> timing.is("Smart"));
    private final NumberValue minDistance = new NumberValue("Min Distance", 8.0f, 2.0f, 30.0f, 0.5f, () -> !timing.is("Apex"));
    private final NumberValue switchBackDelay = new NumberValue("Switch Back Delay", 50, 0, 1000, 10);
    private final NumberValue retriggerDelay = new NumberValue("Retrigger Delay", 1500, 0, 5000, 50);
    private final BoolValue debug = new BoolValue("Debug", false);
    private final BoolValue debugChat = new BoolValue("Debug Chat", false, debug::getValue);
    private final BoolValue visual = new BoolValue("Visual", true);

    private enum State {IDLE, WAIT_PEARL, TRACKING, SWITCH_BACK}

    private final TimerUtil retriggerTimer = new TimerUtil();
    private final TimerUtil phaseTimer = new TimerUtil();
    private final Set<Integer> knownPearls = new HashSet<>();
    private final Set<Integer> knownCharges = new HashSet<>();

    private State state = State.IDLE;
    private int swordSlot;
    private int pearlHotbar = -1;
    private int windHotbar = -1;
    private int pearlId = -1;
    private int waitTicks;

    // ===== 实时解算状态 =====
    private int lastTicksToApex = -1;
    private int lastWindTicks = -1;
    private int lastHitTicks = -1;
    private int pearlAge;
    private double lastApexY;
    private double lastHitDistance;
    private double lastMovement;
    private double lastKnockback;
    private boolean lastBlocked;
    private boolean lastReady;
    private Vec3d lastTarget;
    private boolean comboFired;

    // ===== 视觉：与状态机解耦，连招结束后还会继续画一段时间 =====
    private final List<Vec3d> pearlTrail = new ArrayList<>();
    private final List<Vec3d> chargeTrail = new ArrayList<>();
    private int chargeId = -1;
    private long seekChargeUntil;
    private long firedAt;
    private long lastActive;

    public PearlCatch() {
        super("PearlCatch", Category.Combat);
    }

    @Override
    public void onDisable() {
        switchBack();
        reset();
        clearDisplay();
        pearlTrail.clear();
        chargeTrail.clear();
        lastActive = 0L;
    }

    /** 只清状态机；HUD / 视觉用的 last* 字段留着，好让调试信息多停一会儿。 */
    private void reset() {
        state = State.IDLE;
        pearlId = -1;
        waitTicks = 0;
        comboFired = false;
        knownPearls.clear();
    }

    /** 清掉上一轮连招留下的显示数据。 */
    private void clearDisplay() {
        lastTicksToApex = -1;
        lastWindTicks = -1;
        lastHitTicks = -1;
        lastHitDistance = 0.0;
        lastApexY = 0.0;
        lastMovement = 0.0;
        lastKnockback = 0.0;
        pearlAge = 0;
        lastReady = false;
        lastBlocked = false;
        lastTarget = null;
        firedAt = 0L;
    }

    // =====================================================================
    // 触发：主手持剑右键
    // =====================================================================

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Send) return;
        if (!(event.getPacket() instanceof PlayerInteractItemC2SPacket packet)) return;

        // 只有"主手持剑"的用包才是触发源；本模块自己投珍珠/风弹时主手不是剑，不会被这里拦到
        if (packet.getHand() != Hand.MAIN_HAND) return;
        if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) return;

        // 看向角度要求（默认关闭：连招不该被视角角度限制住）
        if (requireLookUp.getValue() && mc.player.getPitch() > -lookUpAngle.getValue()) return;

        // 快捷栏必须有珍珠和风弹，否则连招做不出来
        int pearl = findHotbar(Items.ENDER_PEARL);
        int wind = findHotbar(Items.WIND_CHARGE);
        if (pearl == -1 || wind == -1) return;

        // 正在连招中：吞掉这次用包，避免重复触发
        if (state != State.IDLE) {
            event.setCancelled();
            return;
        }

        if (!retriggerTimer.hasTimeElapsed(retriggerDelay.getValue().longValue())) {
            event.setCancelled();
            return;
        }
        retriggerTimer.reset();

        event.setCancelled();
        startCombo(pearl, wind);
    }

    private void startCombo(int pearlSlot, int windSlot) {
        swordSlot = mc.player.getInventory().getSelectedSlot();
        pearlHotbar = pearlSlot;
        windHotbar = windSlot;

        // 记录已经存在的珍珠/风弹，方便认出我们自己新投出去的那一颗
        knownPearls.clear();
        knownCharges.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EnderPearlEntity) {
                knownPearls.add(entity.getId());
            } else if (entity instanceof WindChargeEntity) {
                knownCharges.add(entity.getId());
            }
        }

        pearlTrail.clear();
        chargeTrail.clear();
        chargeId = -1;
        clearDisplay();
        pearlAge = 0;
        comboFired = false;
        seekChargeUntil = 0L;
        lastActive = System.currentTimeMillis();

        state = State.WAIT_PEARL;
        waitTicks = 0;
        pearlId = -1;
        phaseTimer.reset();

        // 珍珠沿玩家自己视线投出，不需要改角度
        throwItem(pearlHotbar, null);
    }

    // =====================================================================
    // 状态机（每 tick 实时计算）
    // =====================================================================

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            if (state != State.IDLE) reset();
            return;
        }

        sampleTrails();
        checkChargeImpact();

        switch (state) {
            case WAIT_PEARL -> tickWaitPearl();
            case TRACKING -> tickTracking();
            case SWITCH_BACK -> {
                if (phaseTimer.hasTimeElapsed(switchBackDelay.getValue().longValue())) {
                    switchBack();
                    // 珍珠还在空中就继续盯它，让轨迹线和命中点画完整（comboFired 已经置位，不会二次开火）
                    EnderPearlEntity pearl = findTrackedPearl();
                    if (pearl != null && !pearl.isRemoved()) {
                        state = State.TRACKING;
                    } else {
                        reset();
                    }
                }
            }
            default -> {
            }
        }
    }

    /** 每 tick 把珍珠/风弹的真实位置记进轨迹，供渲染用。 */
    private void sampleTrails() {
        EnderPearlEntity pearl = findTrackedPearl();
        if (pearl != null && !pearl.isRemoved()) {
            addTrail(pearlTrail, pearl.getEntityPos());
        }
        if (state == State.TRACKING || state == State.SWITCH_BACK || chargeId != -1
                || System.currentTimeMillis() < seekChargeUntil) {
            WindChargeEntity charge = findThrownCharge();
            if (charge != null) addTrail(chargeTrail, charge.getEntityPos());
        }
    }

    /**
     * 风弹消失时，用它最后的位置和珍珠比一比，给聊天框一个"中没中"的反馈。
     * 风弹爆炸判定半径是 2.4 格（EXPLOSION_POWER 1.2 × 2），所以 1.5 格内基本必中。
     */
    private void checkChargeImpact() {
        if (chargeId == -1 || !debugChat.getValue()) return;
        Entity entity = mc.world == null ? null : mc.world.getEntityById(chargeId);
        if (entity != null) return;

        chargeId = -1;
        if (chargeTrail.isEmpty()) return;
        Vec3d last = chargeTrail.get(chargeTrail.size() - 1);

        // 命中时珍珠会被立刻消费掉，所以珍珠没了就用轨迹上最后一点来比
        EnderPearlEntity pearl = findTrackedPearl();
        Vec3d pearlPos = pearl != null && !pearl.isRemoved()
                ? pearl.getEntityPos()
                : (pearlTrail.isEmpty() ? null : pearlTrail.get(pearlTrail.size() - 1));
        if (pearlPos == null) return;

        double gap = last.distanceTo(pearlPos);
        chat(gap < 1.5 ? "命中！爆炸点离珍珠 " + String.format("%.2f", gap) + " 格"
                : "没打中，最后差距 " + String.format("%.2f", gap) + " 格");
    }

    private static void addTrail(List<Vec3d> trail, Vec3d pos) {
        trail.add(pos);
        while (trail.size() > TRAIL_LIMIT) trail.remove(0);
    }

    /** 等待服务端把珍珠同步下来。 */
    private void tickWaitPearl() {
        waitTicks++;
        EnderPearlEntity pearl = findNewPearl();
        if (pearl == null) {
            if (waitTicks > 40) reset();  // 一直没同步出来，放弃
            return;
        }
        pearlId = pearl.getId();
        state = State.TRACKING;
    }

    /**
     * 核心：每 tick 用珍珠的真实状态解算"现在出手能不能命中、命中最早点在哪"。
     *
     * <p>三种出手时机：
     * <ul>
     *   <li>{@code Smart}（默认）—— 在风弹飞行 tick 不超过 {@code Max Flight} 的前提下，
     *       尽量往高处打。风弹和珍珠的出膛方向都带 ±0.0172275 的随机抖动，
     *       风弹飞得越久误差越大（误差 ≈ 0.0258 × 飞行 tick），所以飞行时间是精度的硬预算：
     *       实测飞行 ≤ 12 tick 命中率 100%、命中点约 76% 顶点高度；飞行 19 tick
     *       命中率只剩 ~80%，但能吃满顶点。<b>而且它不会因为角度太平而永远等不到出手时机。</b></li>
     *   <li>{@code Instant} —— 一解出合法命中点就出手（最早、最准，但命中点最低）。</li>
     *   <li>{@code Apex} —— 死等珍珠飞到最高点出手（最高，但风弹飞得远、抖动误差大）。</li>
     * </ul>
     */
    private void tickTracking() {
        EnderPearlEntity pearl = findTrackedPearl();
        if (pearl == null || pearl.isRemoved()) {
            reset();
            return;
        }
        pearlAge++;
        lastActive = System.currentTimeMillis();

        // 这一轮已经出过手了：只维持轨迹/命中点的显示，不再解算也不再开火
        if (comboFired) return;

        Vec3d pearlPos = pearl.getEntityPos();
        Vec3d pearlVel = pearl.getVelocity();

        // 风弹生成点 = (x, eyeY, z)（WindChargeItem 官方源码）
        Vec3d windOrigin = new Vec3d(mc.player.getX(), mc.player.getEyePos().y, mc.player.getZ());
        Vec3d movement = shooterMovement();

        List<Vec3d> path = simulatePath(pearlPos, pearlVel, MAX_TICKS);
        Apex apex = simulatePearl(pearlPos, pearlVel);

        lastTicksToApex = apex.ticks();
        lastApexY = apex.pos().y;
        lastMovement = movement.length();

        Intercept intercept = solveIntercept(windOrigin, movement, path, apex.ticks());
        if (intercept == null) {
            lastReady = false;
            return;
        }

        lastWindTicks = intercept.ticks();
        lastHitTicks = pearlAge + intercept.ticks();
        lastHitDistance = intercept.distance();
        lastTarget = intercept.point();
        lastKnockback = knockback(intercept.distance());

        boolean blocked = lineOfSight.getValue() && isPathBlocked(windOrigin, intercept.point());
        lastBlocked = blocked;
        if (blocked) {
            lastReady = false;
            return;
        }

        boolean ready = switch (timing.getValue()) {
            case "Instant" -> intercept.ticks() >= 2 && intercept.distance() >= minDistance.getValue();
            case "Apex" -> apex.ticks() <= intercept.ticks();
            // Smart：飞行预算快用完，或者已经到顶点，就该出手了
            default -> intercept.ticks() >= 2
                    && intercept.distance() >= minDistance.getValue()
                    && (intercept.ticks() >= maxFlight.getValue() || apex.ticks() <= intercept.ticks());
        };
        lastReady = ready;

        if (ready) fire(intercept, windOrigin, movement);
    }

    /** 玩家位移项：官方 {@code setVelocity(shooter, ...)} 会把它加到弹射物速度里（水平恒加，垂直仅离地时加）。 */
    private Vec3d shooterMovement() {
        Vec3d velocity = mc.player.getVelocity();
        return new Vec3d(velocity.x, mc.player.isOnGround() ? 0.0 : velocity.y, velocity.z);
    }

    /**
     * 风弹爆炸给珍珠的击退速度（格/tick），按官方 ExplosionImpl.damageEntities()：
     * {@code (1 - 距离/2.4) * 伤害系数 * 1.22}，伤害系数取 1 作上限估计，用来在 HUD 上比对。
     */
    private static double knockback(double distance) {
        double d = Math.min(1.0, distance / 2.4);
        return (1.0 - d) * 1.22;
    }

    /**
     * 解算风弹拦截。
     *
     * <p>风弹速度 = 单位方向 * 1.5 + 玩家位移。若想让风弹在第 {@code t} tick 命中珍珠轨迹上的
     * {@code P(t)}，需要 {@code origin + t * (1.5 * dir + movement) = P(t)}，即
     * {@code |P(t) - origin - t * movement| = 1.5t}。记
     * {@code err(t) = |P(t) - origin - t * movement| - 1.5t}：
     * <ul>
     *   <li>{@code err > 0}：珍珠跑得比风弹能追的还快，这一 tick 够不着；</li>
     *   <li>{@code err < 0}：风弹会提前越过该点（穿过去，不算命中）；</li>
     *   <li>{@code err == 0}：正好命中。</li>
     * </ul>
     * 所以 err 由正转负的第一次穿越就是"最早可命中 tick" —— 此时瞄准 {@code P(t)}，
     * 风弹的速度方向就是精确解，不需要任何迭代。
     *
     * <p>注意命中点的时刻必须严格对齐：风弹第 t tick 到达的点，珍珠也必须在第 t tick 到达，
     * 所以瞄准点只能是 {@code P(t)} 本身，任何额外的提前/滞后都会变成擦肩而过。
     *
     * <p>两次物理裁剪：只搜到顶点之后 4 tick（再往后就是下坠，顶不动），
     * 且一旦轨迹点落到眼睛高度以下就停止 —— 否则近乎垂直的抛投会解出几百 tick 外的荒谬解。
     */
    private Intercept solveIntercept(Vec3d origin, Vec3d movement, List<Vec3d> path, int apexTicks) {
        int limit = Math.min(MAX_TICKS, Math.max(apexTicks + 4, 8));

        int crossing = -1;
        double prevError = 0.0;
        int bestTick = -1;
        double bestAbs = Double.MAX_VALUE;

        for (int t = 1; t <= limit && t < path.size(); t++) {
            Vec3d point = path.get(t);
            if (point.y < origin.y) break;  // 已经掉到眼睛以下，后面的点没有意义

            Vec3d rel = point.subtract(origin).subtract(movement.multiply(t));
            double error = rel.length() - WIND_POWER * t;

            if (Math.abs(error) < bestAbs) {
                bestAbs = Math.abs(error);
                bestTick = t;
            }
            // 首个 正 → 负 的穿越：取相邻两 tick 里误差更小的那个整数 tick
            if (t > 1 && prevError > 0.0 && error <= 0.0) {
                crossing = Math.abs(prevError) <= Math.abs(error) ? t - 1 : t;
                break;
            }
            prevError = error;
        }

        // 没有穿越就只接受"此刻误差已经小到可以视为命中"的解，绝不用近似解乱开火
        int t = crossing;
        if (t < 0 && bestAbs <= INTERCEPT_TOLERANCE) t = bestTick;
        if (t < 1) return null;

        Vec3d target = path.get(t);
        Vec3d rel = target.subtract(origin).subtract(movement.multiply(t));
        double length = rel.length();
        if (length < 1.0E-4) return null;

        return new Intercept(target, t, target.distanceTo(origin), rel.multiply(1.0 / length));
    }

    /** 按官方物理推演珍珠轨迹点（用于画预测线 / 解算命中点）。 */
    private List<Vec3d> simulatePath(Vec3d pos, Vec3d velocity, int maxTicks) {
        List<Vec3d> path = new ArrayList<>();
        path.add(pos);
        Vec3d p = pos;
        Vec3d v = velocity;
        int bottom = mc.world == null ? -64 : mc.world.getBottomY();
        for (int i = 1; i <= maxTicks; i++) {
            v = v.add(0.0, -GRAVITY, 0.0).multiply(DRAG);
            p = p.add(v);
            path.add(p);
            if (p.y < bottom) break;
        }
        return path;
    }

    /**
     * 按官方 {@code ThrownEntity.tick()} 顺序推演珍珠，返回最高点。
     * 顺序必须是：先加重力，再乘阻力，最后位移。
     */
    private Apex simulatePearl(Vec3d pos, Vec3d velocity) {
        Vec3d p = pos;
        Vec3d v = velocity;
        Vec3d apexPos = pos;
        int apexTick = 0;

        for (int i = 1; i <= MAX_TICKS; i++) {
            v = v.add(0.0, -GRAVITY, 0.0).multiply(DRAG);
            p = p.add(v);
            if (v.y > 0.0) {
                apexPos = p;
                apexTick = i;
            } else {
                break;
            }
        }
        return new Apex(apexPos, apexTick);
    }

    private boolean isPathBlocked(Vec3d from, Vec3d to) {
        try {
            var hit = mc.world.raycast(new RaycastContext(from, to,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            return hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =====================================================================
    // 投掷
    // =====================================================================

    /** 按拦截解把风弹投出去。 */
    private void fire(Intercept intercept, Vec3d origin, Vec3d movement) {
        Vec3d direction = intercept.direction();
        float[] rotation = RotationUtil.getRotations(origin, origin.add(direction));
        throwItem(windHotbar, rotation);
        comboFired = true;
        lastTarget = intercept.point();
        firedAt = System.currentTimeMillis();
        seekChargeUntil = firedAt + 1500L;
        lastActive = firedAt;
        if (debugChat.getValue()) {
            chat("风弹出手 · 飞行 " + intercept.ticks() + "t · 命中点 "
                    + String.format("%.1f", intercept.distance()) + "格");
        }
        state = State.SWITCH_BACK;
        phaseTimer.reset();
    }

    /**
     * 切到指定快捷栏槽位并投出。
     *
     * <p>角度处理依据官方源码：服务端 {@code ServerPlayNetworkHandler.onPlayerInteractItem}
     * 会先读<b>用包里的 yaw/pitch</b> 并 {@code player.setAngles(f, g)}，然后才调用
     * {@code interactionManager.interactItem(...)}。所以只要把角度写进用包，
     * 弹射物的出膛方向就是精确的 —— 既不需要额外发转视包，也不用动客户端视角。
     *
     * <p>不指定角度时走原版 {@code interactItem}（保留音效与客户端预测）。
     */
    private void throwItem(int hotbarSlot, float[] rotation) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        if (mc.player == null || mc.interactionManager == null) return;

        var inventory = mc.player.getInventory();
        if (inventory.getSelectedSlot() != hotbarSlot) {
            inventory.setSelectedSlot(hotbarSlot);
            PacketUtil.sendPacketNoEvent(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        }

        if (rotation == null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        final float yaw = rotation[0];
        final float pitch = rotation[1];
        PacketUtil.sendSequencedPacketNoEvent(sequence ->
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));
    }

    private void switchBack() {
        if (mc.player == null) return;
        var inventory = mc.player.getInventory();
        if (inventory.getSelectedSlot() != swordSlot) {
            inventory.setSelectedSlot(swordSlot);
            PacketUtil.sendPacketNoEvent(new UpdateSelectedSlotC2SPacket(swordSlot));
        }
    }

    private void chat(String message) {
        try {
            if (mc.player != null) mc.player.sendMessage(Text.literal("§b[PearlCatch] §f" + message), false);
        } catch (Throwable ignored) {
        }
    }

    // =====================================================================
    // 工具
    // =====================================================================

    /** 只在快捷栏（0-8）里找。 */
    private int findHotbar(Item item) {
        if (mc.player == null) return -1;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    /** 找刚投出去的那颗珍珠（投掷前不存在的）。 */
    private EnderPearlEntity findNewPearl() {
        EnderPearlEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            if (knownPearls.contains(pearl.getId())) continue;
            double distance = mc.player.squaredDistanceTo(pearl);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pearl;
            }
        }
        return best;
    }

    private EnderPearlEntity findTrackedPearl() {
        if (pearlId == -1) return null;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EnderPearlEntity pearl && pearl.getId() == pearlId) return pearl;
        }
        return null;
    }

    /** 找我们刚投出去的那颗风弹（投掷前不存在的那一颗）。 */
    private WindChargeEntity findThrownCharge() {
        if (chargeId != -1) {
            // 注意这里不把 chargeId 清掉：风弹炸掉以后 checkChargeImpact 还要靠它给出命中反馈
            Entity entity = mc.world.getEntityById(chargeId);
            return entity instanceof WindChargeEntity charge ? charge : null;
        }
        if (System.currentTimeMillis() > seekChargeUntil || mc.player == null) return null;

        WindChargeEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof WindChargeEntity charge)) continue;
            if (knownCharges.contains(charge.getId())) continue;
            double distance = mc.player.squaredDistanceTo(charge);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = charge;
            }
        }
        if (best != null) chargeId = best.getId();
        return best;
    }

    private record Apex(Vec3d pos, int ticks) {
    }

    /**
     * 拦截解。
     *
     * @param point     命中点（珍珠轨迹上的点）
     * @param ticks     从现在起算，风弹飞到这个点需要的 tick 数（= 轨迹下标）
     * @param distance  命中点到眼睛的距离
     * @param direction 风弹该有的单位瞄准方向（已扣掉玩家位移）
     */
    private record Intercept(Vec3d point, int ticks, double distance, Vec3d direction) {
    }

    // =====================================================================
    // 视觉：珍珠预测轨迹 / 珍珠真实轨迹 / 风弹真实轨迹 / 命中点 / 出手闪光
    // =====================================================================

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!visual.getValue() || mc.player == null || mc.world == null) return;

        // 连招结束后仍然多画 VISUAL_HOLD_MS，否则整套特效只存在一两个 tick，根本看不到
        boolean holding = lastActive > 0L && System.currentTimeMillis() - lastActive < VISUAL_HOLD_MS;
        if (state == State.IDLE && !holding) return;

        MatrixStack stack = event.getMatrixStack();
        Vec3d origin = new Vec3d(mc.player.getX(), mc.player.getEyePos().y, mc.player.getZ());
        EnderPearlEntity pearl = findTrackedPearl();

        // 1) 珍珠真实轨迹：亮青色，越旧越淡
        drawTrail(stack, pearlTrail, 0xFF22D3EE, 235);

        // 2) 珍珠预测轨迹：青 → 品红渐变（珍珠还在飞的时候才画）
        if (pearl != null && !pearl.isRemoved()) {
            List<Vec3d> path = simulatePath(pearl.getEntityPos(), pearl.getVelocity(), 90);
            for (int i = 1; i < path.size(); i++) {
                float t = (float) i / path.size();
                int color = ColorUtil.applyAlpha(ColorUtil.interpolate(0xFF22D3EE, 0xFFFF3D9A, t),
                        (int) (150.0f * (1.0f - t * 0.7f)));
                Render3D.drawLine(stack, path.get(i - 1), path.get(i), color, false);
            }
        }

        // 3) 风弹真实轨迹：命中后画出来，白色 → 金色
        drawTrail(stack, chargeTrail, 0xFFFFF3B0, 255);

        if (lastTarget == null) return;

        // 4) 命中点标记：呼吸环 + 十字轴线，可出手时转绿
        long now = System.currentTimeMillis();
        boolean fired = firedAt > 0L && now - firedAt < VISUAL_HOLD_MS;
        double pulse = 0.34 + 0.10 * Math.sin(now / 170.0);
        int ringColor = ColorUtil.applyAlpha(lastReady ? 0xFF6BFF8F : (fired ? 0xFFFFD166 : 0xFF7AA2FF),
                fired ? 245 : 205);
        drawRing(stack, lastTarget, pulse, ringColor);
        Render3D.drawLine(stack, lastTarget.add(0.0, -1.6, 0.0), lastTarget.add(0.0, 1.6, 0.0),
                ColorUtil.applyAlpha(ringColor, 130), false);
        Render3D.drawLine(stack, lastTarget.add(-pulse, 0.0, 0.0), lastTarget.add(pulse, 0.0, 0.0),
                ColorUtil.applyAlpha(ringColor, 150), false);
        Render3D.drawLine(stack, lastTarget.add(0.0, 0.0, -pulse), lastTarget.add(0.0, 0.0, pulse),
                ColorUtil.applyAlpha(ringColor, 150), false);

        // 5) 出手前：眼睛 → 命中点的虚线瞄准路径
        if (!fired && (state == State.TRACKING || state == State.WAIT_PEARL)) {
            int pathColor = ColorUtil.applyAlpha(lastReady ? 0xFF6BFF8F : 0xFF7AA2FF, lastReady ? 215 : 125);
            int dashes = 26;
            for (int i = 0; i < dashes; i += 2) {
                Render3D.drawLine(stack, lerp(origin, lastTarget, i / (double) dashes),
                        lerp(origin, lastTarget, (i + 1) / (double) dashes), pathColor, false);
            }
        }

        // 6) 出手后的扩散冲击环
        renderThrowFlash(event, now, fired);
    }

    private void drawTrail(MatrixStack stack, List<Vec3d> trail, int rgb, int alpha) {
        if (trail.size() < 2) return;
        for (int i = 1; i < trail.size(); i++) {
            float t = (float) i / trail.size();
            Render3D.drawLine(stack, trail.get(i - 1), trail.get(i),
                    ColorUtil.applyAlpha(rgb, (int) (alpha * (0.25f + 0.75f * t))), false);
        }
    }

    private void drawRing(MatrixStack stack, Vec3d center, double radius, int color) {
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double a1 = i * Math.PI * 2.0 / segments;
            double a2 = (i + 1) * Math.PI * 2.0 / segments;
            Vec3d p1 = center.add(Math.cos(a1) * radius, 0.0, Math.sin(a1) * radius);
            Vec3d p2 = center.add(Math.cos(a2) * radius, 0.0, Math.sin(a2) * radius);
            Render3D.drawLine(stack, p1, p2, color, false);
        }
    }

    /** 风弹出手后的扩散闪光。 */
    private void renderThrowFlash(Render3DEvent event, long now, boolean fired) {
        if (!fired || lastTarget == null) return;
        float progress = (now - firedAt) / (float) VISUAL_HOLD_MS;
        if (progress > 1.0f) return;

        double radius = 0.3 + progress * 2.6;
        int color = ColorUtil.applyAlpha(0xFFFFFFFF, (int) (200.0f * (1.0f - progress)));
        drawRing(event.getMatrixStack(), lastTarget, radius, color);
    }

    private static Vec3d lerp(Vec3d from, Vec3d to, double t) {
        return new Vec3d(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t);
    }

    // =====================================================================
    // 调试 HUD
    // =====================================================================

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!debug.getValue() || mc.player == null || mc.world == null) return;
        // 只要模块开着就画（连招结束后继续显示最后一次的数据），不再一闪而过
        if (state == State.IDLE && lastTicksToApex < 0) return;

        TrueTypeFont font = instance.getFontManager().getFont(14);
        float x = 6;
        float y = 60;

        String[] lines = {
                "PearlCatch: " + state + "  [" + timing.getValue() + "]",
                "顶点=" + lastTicksToApex + "t  apexY=" + String.format("%.2f", lastApexY)
                        + "  珍珠已飞=" + pearlAge + "t",
                "命中=珍珠第 " + lastHitTicks + "t  风弹飞=" + lastWindTicks + "t  距离="
                        + String.format("%.2f", lastHitDistance),
                "位移=" + String.format("%.2f", lastMovement)
                        + "  预计击退=" + String.format("%.2f", lastKnockback) + "/t",
                lastBlocked ? "视线被阻挡" : "视线通畅",
                lastReady ? ">>> 可以出手 <<<" : "等待时机"
        };

        float width = 0;
        for (String line : lines) width = Math.max(width, font.getStringWidth(line));
        cn.remix.util.render.Render2D.drawRect(event.getContext(), x - 4, y - 4, width + 8,
                lines.length * (font.getHeight() + 2) + 8, 0x99000000);
        for (int i = 0; i < lines.length; i++) {
            font.drawStringWithShadow(event.getContext(), lines[i], x, y + i * (font.getHeight() + 2),
                    i == lines.length - 1 && lastReady ? 0xFF6BFF8F : 0xFFFFFFFF);
        }
    }
}
