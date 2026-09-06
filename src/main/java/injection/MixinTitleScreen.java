package injection;

import cn.remix.ui.screen.impl.MainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    private static final String CUSTOM_MENU_KEY = "remix.custom_menu";

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean customMenu = !"false".equals(System.getProperty(CUSTOM_MENU_KEY, "true"));
        if (customMenu && mc.currentScreen instanceof TitleScreen) {
            mc.execute(() -> mc.setScreen(new MainMenu()));
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean customMenu = !"false".equals(System.getProperty(CUSTOM_MENU_KEY, "true"));
        if (customMenu) return;

        TitleScreen screen = (TitleScreen) (Object) this;
        ButtonWidget button = ButtonWidget.builder(Text.literal("Remix Menu"), btn -> {
                    System.setProperty(CUSTOM_MENU_KEY, "true");
                    mc.setScreen(new MainMenu());
                })
                .dimensions(10, screen.height - 36, 110, 20)
                .build();

        // addDrawableChild 是 protected 泛型方法，mixin @Shadow 泛型签名难匹配，改用反射
        try {
            Method m = Screen.class.getDeclaredMethod("addDrawableChild", Element.class);
            m.setAccessible(true);
            m.invoke(this, button);
        } catch (Exception ignored) {
        }
    }
}
