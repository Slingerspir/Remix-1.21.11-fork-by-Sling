package injection;

import cn.remix.management.TasManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class MixinSoundSystem {

    @Inject(method = "getAdjustedPitch", at = @At("RETURN"), cancellable = true)
    private void onGetAdjustedPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float multiplier = TasManager.getSoundPitch();
        if (multiplier > 0.0f && multiplier != 1.0f) {
            cir.setReturnValue(MathHelper.clamp(cir.getReturnValue() * multiplier, 0.25f, 4.0f));
        }
    }
}