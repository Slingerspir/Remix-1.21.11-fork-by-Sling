package injection;

import cn.remix.ui.screen.impl.MainMenu;
import cn.remix.util.IMinecraft;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen implements IMinecraft {

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        mc.execute(() -> {
            if (mc.currentScreen instanceof TitleScreen) {
                mc.setScreen(new MainMenu());
            }
        });
    }
}
