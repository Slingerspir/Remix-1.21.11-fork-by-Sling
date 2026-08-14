package injection;

import cn.remix.module.impl.move.KeepSprint;
import cn.remix.util.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class MixinPlayerEntity implements IMinecraft {

    @Inject(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V", shift = At.Shift.AFTER))
    public void onKnockbackTarget(Entity target, float strength, Vec3d playerTargetVelocity, CallbackInfo callbackInfo) {
        if (mc.player == null || mc.world == null || instance.getModuleManager() == null) return;

        KeepSprint ks = instance.getModuleManager().getModule(KeepSprint.class);
        if (ks.isEnabled()) {
            final float multiplier = 0.6f + 0.4f * ks.motion.getValue();
            mc.player.setVelocity(mc.player.getVelocity().x / 0.6 * multiplier, mc.player.getVelocity().y, mc.player.getVelocity().z / 0.6 * multiplier);
            mc.player.setSprinting(true);
        }
    }
}