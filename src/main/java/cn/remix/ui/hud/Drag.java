package cn.remix.ui.hud;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.misc.MouseUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;

public abstract class Drag extends Module {
    private final EasingAnimation xAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 300);
    private final EasingAnimation yAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 300);
    public float percentX;
    public float percentY;
    private float lastMouseX;
    private float lastMouseY;
    protected float renderX;
    protected float renderY;
    public float width;
    public float height;

    public boolean dragging;

    public Drag(String name) {
        super(name, Category.Render);
        this.width = 100;
        this.height = 100;
    }

    public void updatePos() {
        Window window = mc.getWindow();
        float scaledWidth = (float) window.getScaledWidth();
        float scaledHeight = (float) window.getScaledHeight();

        float targetX = this.percentX * scaledWidth;
        float targetY = this.percentY * scaledHeight;

        if (this.dragging) {
            this.xAnimation.setValue(targetX);
            this.yAnimation.setValue(targetY);
        } else {
            this.xAnimation.run(targetX);
            this.yAnimation.run(targetY);
        }

        this.renderX = this.xAnimation.getValue().floatValue();
        this.renderY = this.yAnimation.getValue().floatValue();
    }

    public boolean isRightAnchored() {
        return false;
    }

    public final void onChatGUI(double mouseX, double mouseY, boolean dragAllowed) {
        this.updatePos();

        float currentMouseX = (float) mouseX;
        float currentMouseY = (float) mouseY;

        if (!dragAllowed) {
            this.dragging = false;
        }

        if (dragAllowed && !this.dragging && this.isHovered(currentMouseX, currentMouseY)) {
            this.dragging = true;
            this.lastMouseX = currentMouseX;
            this.lastMouseY = currentMouseY;
        }

        if (this.dragging) {
            Window window = mc.getWindow();
            float scaledWidth = (float) window.getScaledWidth();
            float scaledHeight = (float) window.getScaledHeight();
            float deltaPercentX = ((float)mouseX - this.lastMouseX) / scaledWidth;
            float deltaPercentY = ((float)mouseY - this.lastMouseY) / scaledHeight;

            this.percentX += deltaPercentX;
            this.percentY += deltaPercentY;

            if (isRightAnchored()) {
                this.percentX = Math.max(this.width / scaledWidth, Math.min(1, this.percentX));
            } else {
                this.percentX = Math.max(0, Math.min(1 - (this.width / scaledWidth), this.percentX));
            }

            this.percentY = Math.max(0, Math.min(1 - (this.height / scaledHeight), this.percentY));
            this.lastMouseX = (float)mouseX;
            this.lastMouseY = (float)mouseY;
            this.updatePos();
        }
    }

    protected boolean isHovered(float mouseX, float mouseY) {
        float x = isRightAnchored() ? (this.renderX - this.width) : this.renderX;
        return MouseUtil.isHovered(mouseX, mouseY, x, this.renderY, this.width, this.height);
    }

    public abstract void render(DrawContext context);
}