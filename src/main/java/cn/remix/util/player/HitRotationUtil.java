package cn.remix.util.player;

import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 命中点旋转工具（移植自 nilore KillAura 的最佳命中点逻辑）：
 * 在目标 AABB 上采样 + 射线检测，取最近可命中点，并做鼠标灵敏度 GCD 对齐。
 */
@UtilityClass
public class HitRotationUtil implements IMinecraft {

    public record BestHit(Vec3d hitPoint, Vec3d closestPoint, double distance, float[] rotation) {
    }

    private static final double SAMPLE_STEP = 0.1;
    private static final int MAX_SAMPLES = 280;

    public BestHit getBestHit(Entity target) {
        if (target == null || mc.player == null || mc.world == null) return null;

        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();
        Vec3d closest = RotationUtil.getNearestPointBB(box);

        Vec3d bestPoint = null;
        double bestDistance = Double.MAX_VALUE;

        double sx = Math.max(SAMPLE_STEP, (box.maxX - box.minX) / 4.0);
        double sy = Math.max(SAMPLE_STEP, (box.maxY - box.minY) / 5.0);
        double sz = Math.max(SAMPLE_STEP, (box.maxZ - box.minZ) / 4.0);

        int samples = 0;
        outer:
        for (double x = box.minX; x <= box.maxX + 1.0E-4; x += sx) {
            for (double y = box.minY; y <= box.maxY + 1.0E-4; y += sy) {
                for (double z = box.minZ; z <= box.maxZ + 1.0E-4; z += sz) {
                    if (++samples > MAX_SAMPLES) break outer;
                    Vec3d point = new Vec3d(x, y, z);
                    EntityHitResult hit = ProjectileUtil.raycast(mc.player, eye, point, box.expand(0.05),
                            entity -> entity == target, 6.0);
                    if (hit == null) continue;
                    double distance = eye.distanceTo(hit.getPos());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPoint = hit.getPos();
                    }
                }
            }
        }

        if (bestPoint == null) {
            float[] rot = RotationUtil.getRotations(eye, closest);
            rot = RotationUtil.applySensitivityPatch(rot);
            return new BestHit(closest, closest, eye.distanceTo(closest), rot);
        }

        float[] rot = RotationUtil.getRotations(eye, bestPoint);
        rot = RotationUtil.applySensitivityPatch(rot);
        return new BestHit(bestPoint, closest, bestDistance, rot);
    }

    /** 沿给定旋转方向射线，返回到底目标表面的距离；不可命中返回 MAX_VALUE。 */
    public double getHitDistance(Entity target, Vec3d eye, float[] rotation) {
        if (target == null || rotation == null) return Double.MAX_VALUE;
        Vec3d dir = RotationUtil.getVectorForRotation(rotation[0], rotation[1]);
        Vec3d end = eye.add(dir.multiply(6.0));
        EntityHitResult hit = ProjectileUtil.raycast(mc.player, eye, end, target.getBoundingBox().expand(0.05),
                entity -> entity == target, 6.0);
        return hit == null ? Double.MAX_VALUE : eye.distanceTo(hit.getPos());
    }

    /** 仅按 Yaw 判断目标是否在 FoV 内。 */
    public boolean isEntityInFov(Entity entity, float fov) {
        if (entity == null || mc.player == null) return false;
        Vec3d eye = mc.player.getEyePos();
        float[] rot = RotationUtil.getRotations(eye, entity.getBoundingBox().getCenter());
        return Math.abs(angleDifference(rot[0], mc.player.getYaw())) <= fov / 2.0f;
    }

    public float angleDifference(float a, float b) {
        float diff = (a - b) % 360.0f;
        if (diff > 180.0f) diff -= 360.0f;
        if (diff < -180.0f) diff += 360.0f;
        return diff;
    }
}
