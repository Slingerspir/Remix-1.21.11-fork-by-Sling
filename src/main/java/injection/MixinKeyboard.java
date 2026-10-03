package injection;

import cn.remix.event.impl.CharInputEvent;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard implements IMinecraft {

    @Inject(method = "onKey", at = @At(value = "HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action == 1) {
            KeyInputEvent event = new KeyInputEvent(input.key());
            instance.getEventManager().call(event);
            // 音乐界面搜索框正在接收输入时，别让按键漏进游戏（否则打字会走动、e 会开背包）
            if (cn.remix.ui.music.MusicHud.isTyping()) ci.cancel();
        }
    }

    @Inject(method = "onChar", at = @At(value = "HEAD"))
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        if (input != null && input.isValidChar()) {
            instance.getEventManager().call(new CharInputEvent(input.codepoint()));
        }
    }
}