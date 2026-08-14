package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.Render3D;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public class ClickTP extends Module {
    private final NumberValue maxDistance = new NumberValue("Max Distance", 50, 5, 200, 5);
    private final NumberValue stepDistance = new NumberValue("Step Distance", 8, 1, 20, 1);
    private final BoolValue renderBox = new BoolValue("Render Box", true);
    private Vec3d targetVec = null;

    public ClickTP() {
        super("ClickTP", Category.Player);
    }

    @Override
    public void onEnable() {
        targetVec = null;
    }

    @Override
    public void onDisable() {
        targetVec = null;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!renderBox.getValue() || mc.player == null || mc.world == null) return;

        HitResult hitResult = mc.player.raycast(maxDistance.getValue().doubleValue(), 1.0f, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos targetPos = blockHitResult.getBlockPos();
            int highlightColor = new Color(0, 255, 200, 100).getRGB();
            Render3D.drawBox(event.getMatrixStack(), targetPos, highlightColor);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.isPre()) {
            if (targetVec == null) {
                if (mc.options.pickItemKey.isPressed()) {
                    HitResult hitResult = mc.player.raycast(maxDistance.getValue().doubleValue(), 1.0f, false);
                    if (hitResult.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                        BlockPos targetPos = blockHitResult.getBlockPos();
                        targetVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
                        mc.options.pickItemKey.setPressed(false);
                    }
                }
            } else {
                Vec3d currentPos = mc.player.getEntityPos();
                mc.player.setVelocity(0, 0, 0);
                double step = stepDistance.getValue().doubleValue();
                if (currentPos.distanceTo(targetVec) <= step) {
                    mc.player.setPosition(targetVec.x, targetVec.y, targetVec.z);
                    targetVec = null;
                } else {
                    Vec3d dir = targetVec.subtract(currentPos).normalize();
                    Vec3d nextPos = currentPos.add(dir.multiply(step));
                    mc.player.setPosition(nextPos.x, nextPos.y, nextPos.z);
                }
            }
        }
    }
}