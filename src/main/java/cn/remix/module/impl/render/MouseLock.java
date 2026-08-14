package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public final class MouseLock extends Module {

    private final BoolValue lockInInventory = new BoolValue("Lock In Inventory", false);
    private final BoolValue lockInChat = new BoolValue("Lock In Chat", false);

    public MouseLock() {
        super("MouseLock", Category.Render);
        setEnabled(false);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.getWindow() != null) {
            GLFW.glfwSetInputMode(mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.getWindow() == null) return;
        if (mc.player == null || mc.world == null) return;

        long window = mc.getWindow().getHandle();

        boolean showCursor = shouldShowCursor();

        if (showCursor) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        } else {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
    }

    private boolean shouldShowCursor() {
        if (!isEnabled()) return true;

        var screen = mc.currentScreen;

        if (screen == null) {
            return false; 
        }

        if (screen instanceof ChatScreen) {
            return lockInChat.getValue();
        }

        if (screen instanceof GenericContainerScreen) {
            return lockInInventory.getValue();
        }

        return true;
    }


}