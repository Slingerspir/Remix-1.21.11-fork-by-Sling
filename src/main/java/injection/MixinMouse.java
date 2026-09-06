package injection;

import cn.remix.event.impl.MouseClickEvent;
import cn.remix.event.impl.MouseScrollEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MixinMouse implements IMinecraft {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MouseScrollEvent event = new MouseScrollEvent(vertical);
        instance.getEventManager().call(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput button, int action, CallbackInfo ci) {
        MouseClickEvent event = new MouseClickEvent(button.getKeycode(), action);
        instance.getEventManager().call(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
