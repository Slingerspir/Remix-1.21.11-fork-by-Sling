package injection;

import cn.remix.ui.screen.impl.MainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    private static final String FIRST_LAUNCH_KEY = "remix.first_launch";

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean firstLaunch = !"false".equals(System.getProperty(FIRST_LAUNCH_KEY, "true"));
        if (firstLaunch && mc.currentScreen instanceof TitleScreen) {
            System.setProperty(FIRST_LAUNCH_KEY, "false");
            mc.execute(() -> mc.setScreen(new MainMenu()));
        }
    }
}