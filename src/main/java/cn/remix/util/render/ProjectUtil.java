package cn.remix.util.render;

import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * @author DSJ
 * create 2026/7/1
 */
@UtilityClass
public class ProjectUtil implements IMinecraft {
    private final Vector4f vec4f = new Vector4f();

    public @NotNull Vec3d worldSpaceToScreenSpace(@NotNull Vec3d pos, @NotNull Matrix4f projMat, @NotNull Matrix4f modMat) {
        var camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null || camera.getCameraPos() == null) return Vec3d.ZERO;
        Vec3d cam = camera.getCameraPos();

        Vector4f vector = vec4f.set((float)(pos.x - cam.x), (float)(pos.y - cam.y), (float)(pos.z - cam.z), 1);
        if (vector.mul(modMat).mul(projMat).w <= 0) {
            return Vec3d.ZERO;
        }

        vector.div(vector.w);
        return new Vec3d(
                (vector.x * .5 + .5) * mc.getWindow().getScaledWidth(),
                (.5 - vector.y * .5) * mc.getWindow().getScaledHeight(),
                vector.z
        );
    }

    public @NotNull Vec3d[] getVectors(@NotNull Entity ent) {
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        Box axisAlignedBB = createInterpolatedBoundingBox(ent, tickDelta);

        return new Vec3d[]{
                new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ),
                new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ),
                new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ),
                new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ),
                new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ),
                new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ),
                new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ),
                new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        };
    }

    private Box createInterpolatedBoundingBox(@NotNull Entity ent, float tickDelta) {
        Vec3d renderPosition = ent.getLerpedPos(tickDelta);
        return ent.getBoundingBox().offset(renderPosition.x - ent.getX(), renderPosition.y - ent.getY(), renderPosition.z - ent.getZ());
    }
}