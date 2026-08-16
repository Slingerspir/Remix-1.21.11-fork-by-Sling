package injection;

import cn.remix.management.TasManager;
import net.minecraft.entity.player.ItemCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldownManager.class)
public abstract class MixinItemCooldownManager {

    @Unique
    private int tasCooldownTicks;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(CallbackInfo ci) {
        if (TasManager.isTasActive()) {
            tasCooldownTicks++;
            if (!TasManager.shouldRunAction(tasCooldownTicks)) {
                ci.cancel();
            }
        }
    }
}