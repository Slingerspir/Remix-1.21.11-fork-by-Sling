package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import lombok.Getter;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

@Getter
public class GuiMove extends Module {

    public GuiMove() {
        super("GuiMove", Category.Move);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen) && !(mc.currentScreen instanceof DeathScreen)) {
            mc.options.forwardKey.setPressed(isPhysicallyDown(mc.options.forwardKey));
            mc.options.backKey.setPressed(isPhysicallyDown(mc.options.backKey));
            mc.options.leftKey.setPressed(isPhysicallyDown(mc.options.leftKey));
            mc.options.rightKey.setPressed(isPhysicallyDown(mc.options.rightKey));
            mc.options.jumpKey.setPressed(isPhysicallyDown(mc.options.jumpKey));
        }
    }

    private boolean isPhysicallyDown(KeyBinding key) {
        int code = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()).getCode();
        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }
}