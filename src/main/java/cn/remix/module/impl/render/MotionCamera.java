package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class MotionCamera extends Module {
    public final BoolValue disableFirstPerspective = new BoolValue("Disable First Perspective", true);
    public final NumberValue smoothness = new NumberValue("Smoothness", 0.3, 0.1, 0.95, 0.01);
    public final NumberValue maxDistance = new NumberValue("Max Distance", 20.0, 1.0, 50.0, 0.5);

    private Vec3d cameraPos;

    public MotionCamera() {
        super("MotionCamera", Category.Render);
        setKey(GLFW.GLFW_KEY_F6);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            cameraPos = getPlayerEyePos();
        }
    }

    @Override
    public void onDisable() {
        cameraPos = null;
    }

    public boolean isFirstPerson() {
        return mc.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    public Vec3d getCameraPos() {
        if (mc.player == null || cameraPos == null) {
            return getPlayerEyePos();
        }
        return cameraPos;
    }

    public void update(Vec3d playerPos) {
        if (mc.player == null) return;

        Vec3d targetPos = playerPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
        if (cameraPos == null) {
            cameraPos = targetPos;
            return;
        }

        double distance = cameraPos.distanceTo(targetPos);
        double maxDist = maxDistance.getValue();
        if (distance > maxDist) {
            cameraPos = targetPos;
            return;
        }

        double smoothFactor = smoothness.getValue();
        double dynamicFactor = smoothFactor * (1.0 - Math.exp(-distance / maxDist));
        cameraPos = cameraPos.lerp(targetPos, dynamicFactor);
    }

    private Vec3d getPlayerEyePos() {
        if (mc.player == null) {
            return Vec3d.ZERO;
        }
        return new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
    }
}
