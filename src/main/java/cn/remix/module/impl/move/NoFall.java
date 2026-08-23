package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;

public final class NoFall extends Module {

    private final NumberValue triggerHeight = new NumberValue("Trigger Height", 3.2f, 0.5f, 6.0f, 0.1f);

    private boolean triggered = false;

    public NoFall() {
        super("NoFall", Category.Move);
    }

    @Override
    public void onEnable() {
        triggered = false;
    }

    @Override
    public void onDisable() {
        triggered = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;
        if (mc.player.getAbilities().flying) return;
        if (mc.player.getVelocity().y >= 0) return;

        if (mc.player.isOnGround()) {
            triggered = false;
            return;
        }

        if (triggered) {
            return;
        }

        double distance = getDistanceToGround();
        float height = triggerHeight.getValue();

        if (distance <= height && distance > 0.1) {
            doNoFall();
            triggered = true;
        }
    }

    private double getDistanceToGround() {
        if (mc.player == null || mc.world == null) return Double.MAX_VALUE;

        double playerY = mc.player.getY();
        int blockY = mc.player.getBlockY();

        for (int y = blockY; y >= mc.world.getBottomY(); y--) {
            var pos = mc.player.getBlockPos().withY(y);
            var state = mc.world.getBlockState(pos);

            if (!state.isAir()) {
                double blockTop = y + state.getCollisionShape(mc.world, pos)
                        .getMax(Direction.Axis.Y);
                return playerY - blockTop;
            }
        }

        return Double.MAX_VALUE;
    }

    private void doNoFall() {
        if (mc.player == null) return;

        
        mc.player.fallDistance = 0.0f;

        float height = triggerHeight.getValue();
        double bounce = Math.min(height * 0.15, 0.5);
        mc.player.setVelocity(
                mc.player.getVelocity().x * 0.95,
                bounce,
                mc.player.getVelocity().z * 0.95
        );

        
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Send) return;
        if (!(event.getPacket() instanceof PlayerMoveC2SPacket)) return;

        if (triggered && !mc.player.isOnGround()) {
            PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) event.getPacket();
            accessor.setOnGround(true);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.isPost()) return;

        if (triggered && !mc.player.isOnGround()) {
            event.setOnGround(true);
        }
    }

    @Override
    public String getSuffix() {
        return String.format("%.1f", triggerHeight.getValue());
    }
}