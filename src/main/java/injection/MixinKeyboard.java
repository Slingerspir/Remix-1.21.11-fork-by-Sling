package injection;

import cn.remix.event.impl.KeyInputEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard implements IMinecraft {

    @Inject(method = "onKey", at = @At(value = "HEAD"))
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action == 1) {
            KeyInputEvent event = new KeyInputEvent(input.key());
            instance.getEventManager().call(event);
        }
    }
}