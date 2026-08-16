package injection;

import cn.remix.management.TasManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class MixinServerPlayerInteractionManager {

    @Redirect(method = "continueMining", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F"))
    private float scaleContinueMiningDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        float delta = state.calcBlockBreakingDelta(player, world, pos);
        float multiplier = TasManager.getTasMultiplier();
        if (TasManager.isTasActive() && multiplier > 0.0f) {
            delta /= multiplier;
        }
        return delta;
    }
}