package injection;

import cn.remix.module.impl.render.MotionCamera;
import cn.remix.util.IMinecraft;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class MixinCamera implements IMinecraft {
    private float tickDelta;

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateHead(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        this.tickDelta = tickDelta;
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void onSetCameraPosition(Args args) {
        MotionCamera motionCamera = instance.getModuleManager().getModule(MotionCamera.class);
        if (motionCamera == null || !motionCamera.isEnabled() || mc.player == null) {
            return;
        }

        if (motionCamera.disableFirstPerspective.getValue() && motionCamera.isFirstPerson()) {
            return;
        }

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        motionCamera.update(playerPos);
        Vec3d cameraPos = motionCamera.getCameraPos();
        args.set(0, cameraPos.x);
        args.set(1, cameraPos.y);
        args.set(2, cameraPos.z);
    }
}
