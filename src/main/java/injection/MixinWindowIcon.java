package injection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Mixin(Window.class)
public class MixinWindowIcon {

    private static final Logger LOGGER = LoggerFactory.getLogger("RemixClient");

    /**
     * 在 Window 初始化后设置自定义标题和图标
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onWindowInit(CallbackInfo ci) {
        try {
            Window window = (Window) (Object) this;

            // 设置窗口标题
            window.setTitle("Remix Client Fork By Sling v1.8.0");
            LOGGER.info("Window title set to: Remix Client v1.0.0");

            // 设置窗口图标
            setWindowIcon(window);
        } catch (Exception e) {
            LOGGER.error("Failed to set window title/icon", e);
        }
    }

    /**
     * 设置窗口图标
     */
    private void setWindowIcon(Window window) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<IconData> icons = new ArrayList<>();

            // 尝试加载不同尺寸的图标
            int[] sizes = {16, 32, 64, 128};
            for (int size : sizes) {
                Identifier id = Identifier.of("remix", "textures/icon/icon_" + size + "x" + size + ".png");
                try {
                    var resource = mc.getResourceManager().getResource(id).orElse(null);
                    if (resource != null) {
                        var image = net.minecraft.client.texture.NativeImage.read(resource.getInputStream());
                        ByteBuffer pixels = ByteBuffer.allocateDirect(image.getWidth() * image.getHeight() * 4);
                        int[] abgr = image.copyPixelsAbgr();
                        pixels.asIntBuffer().put(abgr);
                        pixels.rewind();
                        icons.add(new IconData(image.getWidth(), image.getHeight(), pixels));
                        image.close();
                        LOGGER.info("Loaded icon: {}x{}", size, size);
                    }
                } catch (Exception e) {
                    // 忽略缺失的尺寸
                }
            }

            // 如果找不到多尺寸图标，尝试加载单图标
            if (icons.isEmpty()) {
                try {
                    Identifier id = Identifier.of("remix", "icon.png");
                    var resource = mc.getResourceManager().getResource(id).orElse(null);
                    if (resource != null) {
                        var image = net.minecraft.client.texture.NativeImage.read(resource.getInputStream());
                        ByteBuffer pixels = ByteBuffer.allocateDirect(image.getWidth() * image.getHeight() * 4);
                        int[] abgr = image.copyPixelsAbgr();
                        pixels.asIntBuffer().put(abgr);
                        pixels.rewind();
                        icons.add(new IconData(image.getWidth(), image.getHeight(), pixels));
                        image.close();
                        LOGGER.info("Loaded single icon: {}x{}", image.getWidth(), image.getHeight());
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }

            if (icons.isEmpty()) {
                LOGGER.warn("No icons found in assets/remix/");
                return;
            }

            // 使用 LWJGL 设置图标
            long handle = window.getHandle();
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var buffer = org.lwjgl.glfw.GLFWImage.malloc(icons.size(), stack);

                for (int i = 0; i < icons.size(); i++) {
                    IconData data = icons.get(i);
                    ByteBuffer pixels = data.pixels();
                    pixels.rewind();

                    buffer.position(i);
                    buffer.width(data.width());
                    buffer.height(data.height());
                    buffer.pixels(pixels);
                }

                org.lwjgl.glfw.GLFW.glfwSetWindowIcon(handle, buffer.position(0));
                LOGGER.info("Window icon set successfully, {} icons", icons.size());
            }

        } catch (Exception e) {
            LOGGER.error("Failed to set window icon", e);
        }
    }

    /**
     * 图标数据记录
     */
    private record IconData(int width, int height, ByteBuffer pixels) {}
}