package injection;

import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.impl.render.NoHurtCam;
import cn.remix.util.IMinecraft;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IMinecraft {

    @Shadow
    @Final
    private BufferBuilderStorage buffers;

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    private void renderWorld(RenderTickCounter renderTickCounter, CallbackInfo ci, @Local(ordinal = 0) Matrix4f projectionMatrix, @Local(ordinal = 1) Matrix4f modelViewMatrix) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.peek().getPositionMatrix().set(modelViewMatrix);
        VertexConsumerProvider consumers = this.buffers.getEntityVertexConsumers();
        Render3DEvent event = new Render3DEvent(matrixStack, consumers, renderTickCounter.getTickProgress(true), projectionMatrix, modelViewMatrix);
        instance.getEventManager().call(event);
    }

    @Inject(at = @At("HEAD"), method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", cancellable = true)
    private void tiltViewWhenHurt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (instance.getModuleManager().getModule(NoHurtCam.class).isEnabled()) {
            ci.cancel();
        }
    }
}