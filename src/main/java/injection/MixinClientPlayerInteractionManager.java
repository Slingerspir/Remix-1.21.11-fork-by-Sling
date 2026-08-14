package injection;

import cn.remix.event.impl.AttackEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager implements IMinecraft {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        AttackEvent event = new AttackEvent(target);
        instance.getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
