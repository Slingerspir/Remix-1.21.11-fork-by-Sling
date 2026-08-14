package injection;

import cn.remix.event.impl.RenderRotationEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> implements IMinecraft {
    @Shadow
    public abstract Identifier getTexture(S state);

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
    private void entity(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        RenderRotationEvent.currentEntity = livingEntity;
    }

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerpAngleDegrees(FFF)F", ordinal = 0))
    private float yaw(float delta, float start, float end) {
        RenderRotationEvent event = new RenderRotationEvent(new float[]{end, 0}, new float[]{start, 0});
        if (RenderRotationEvent.currentEntity == mc.player) {
            instance.getEventManager().call(event);
        }

        return MathHelper.lerpAngleDegrees(delta, event.getLastRotation()[0], event.getRotation()[0]);
    }

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F"))
    private float pitch(LivingEntity entity, float delta) {
        RenderRotationEvent event = new RenderRotationEvent(new float[]{0, entity.getPitch()}, new float[]{0, entity.lastPitch});
        if (entity == mc.player) {
            instance.getEventManager().call(event);
        }

        return MathHelper.lerp(delta, event.getLastRotation()[1], event.getRotation()[1]);
    }
}