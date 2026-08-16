package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.ui.screen.util.AdaptiveButton;

import java.util.ArrayList;
import java.util.List;

public class ButtonRenderer extends Module {

    private final List<AdaptiveButton> buttons = new ArrayList<>();

    public ButtonRenderer() {
        super("ButtonRenderer", Category.Render);
        setEnabled(true);
    }

    public void addButton(AdaptiveButton button) {
        buttons.add(button);
    }

    public void clearButtons() {
        buttons.clear();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null && mc.currentScreen == null) return;

        int mouseX = (int) (event.getContext().getScaledWindowWidth() / 2);
        int mouseY = (int) (event.getContext().getScaledWindowHeight() / 2);

        for (AdaptiveButton button : buttons) {
            button.render(event.getContext(), mouseX, mouseY, 1.0f);
        }
    }
}