package injection;

import cn.remix.module.impl.world.WorldTweaks;
import cn.remix.util.IMinecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class MixinBiome implements IMinecraft {

    @Inject(method = "hasPrecipitation", at = @At("HEAD"), cancellable = true)
    private void hasPrecipitation(CallbackInfoReturnable<Boolean> cir) {
        WorldTweaks module = instance.getModuleManager().getModule(WorldTweaks.class);
        
        if (module.isEnabled()) {
            if (module.weather.is("Rain") || module.weather.is("Snow")) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "isCold", at = @At("HEAD"), cancellable = true)
    private void isCold(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
        WorldTweaks module = instance.getModuleManager().getModule(WorldTweaks.class);
        
        if (module.isEnabled()) {
            if (module.weather.is("Snow")) {
                cir.setReturnValue(true);
            }
        }
    }
}