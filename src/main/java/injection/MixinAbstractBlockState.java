package injection;

import cn.remix.Client;
import cn.remix.event.impl.BlockShapeEvent;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class MixinAbstractBlockState {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void remix$getCollisionShape(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Client client = Client.instance;
        if (mc == null || client == null || client.getEventManager() == null
                || mc.world == null || mc.player == null || world != mc.world) return;

        BlockShapeEvent event = new BlockShapeEvent((BlockState) (Object) this, pos, cir.getReturnValue());
        client.getEventManager().call(event);
        cir.setReturnValue(event.getShape());
    }
}
