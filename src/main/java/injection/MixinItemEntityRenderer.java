package injection;

import cn.remix.module.impl.render.ItemPhysics;
import cn.remix.util.IMinecraft;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer extends EntityRenderer<ItemEntity, ItemEntityRenderState> implements IMinecraft {

    @Shadow
    @Final
    private Random random;

    protected MixinItemEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onRender(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        ItemPhysics itemPhysics = instance.getModuleManager().getModule(ItemPhysics.class);
        if (!itemPhysics.isEnabled()) return;
        ci.cancel();

        if (!state.itemRenderState.isEmpty()) {
            matrices.push();
            this.random.setSeed(state.seed);
            boolean block = state.itemRenderState.isSideLit();

            matrices.peek().getPositionMatrix().setRowColumn(3, 1, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

            if (block) {
                matrices.translate(0.0, -0.2, -0.01);
            } else {
                matrices.translate(0.0, 0.0, -0.05);
            }

            for (int i = 0; i < this.getModelCount(state.renderedAmount); i++) {
                matrices.push();
                if (i > 0 && block) {
                    matrices.translate(
                            (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F,
                            (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F,
                            (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F
                    );
                }

                state.itemRenderState.render(matrices, orderedRenderCommandQueue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
                matrices.pop();

                if (!block) {
                    matrices.translate(0.0F, 0.0F, 0.09375F);
                }
            }

            matrices.pop();
            super.render(state, matrices, orderedRenderCommandQueue, cameraRenderState);
        }
    }

    @Unique
    private int getModelCount(int count) {
        if (count > 48) return 5;
        if (count > 32) return 4;
        if (count > 16) return 3;
        return count > 1 ? 2 : 1;
    }
}