package cn.remix.ui.clickgui.panel;

import cn.remix.module.impl.render.HUD;
import cn.remix.util.IMinecraft;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

public abstract class Panel implements IMinecraft {
    public static final float width = 110;
    public static final float headerHeight = 18;

    protected float x;
    protected float y;
    protected float dragOffsetX;
    protected float dragOffsetY;
    protected boolean dragging;
    protected final EasingAnimation scrollAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    protected double targetScrollY;

    public Panel(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public int getAccent() {
        return instance.getModuleManager().getModule(HUD.class).getColor();
    }

    protected boolean isHovered(double mx, double my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    protected void handleDrag(Click click) {
        if (isHovered(click.x(), click.y(), x, y, width, headerHeight) && click.button() == 0) {
            dragging = true;
            dragOffsetX = (float) (click.x() - x);
            dragOffsetY = (float) (click.y() - y);
        }
    }

    protected void updateScroll(float totalH, float maxH) {
        targetScrollY = totalH > maxH ? Math.max(maxH - totalH, Math.min(0, targetScrollY)) : 0;
        scrollAnimation.run(targetScrollY);
    }

    public abstract void render(DrawContext context, int mouseX, int mouseY, float globalAlpha);

    public abstract void mouseClicked(Click click);

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    public void mouseScroll(double mouseX, double mouseY, double amount) {
        if (isHovered(mouseX, mouseY, x, y, width, headerHeight + 350)) {
            targetScrollY += amount * 18;
        }
    }

    public boolean keyTyped(KeyInput input) {
        return false;
    }

    public boolean charTyped(CharInput input) {
        return false;
    }
}