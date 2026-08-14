package injection;

import cn.remix.event.impl.CobwebEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CobwebBlock.class)
public class MixinCobwebBlock implements IMinecraft {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
        if (entity == mc.player) {
            CobwebEvent event = new CobwebEvent(state, pos);
            instance.getEventManager().call(event);

            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }
}