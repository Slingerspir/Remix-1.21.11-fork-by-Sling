package cn.remix.ui.screen.util;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

@Getter @Setter
public class AdaptiveTextBox implements IMinecraft {
    private float x, y, width, height;
    private String text = "";
    private final String placeholder;
    private boolean focused;
    private boolean passwordMode;
    private boolean isSelectedAll = false;
    private int cursorIndex = 0;
    private final EasingAnimation focusAnimation;
    private final EasingAnimation cursorAnimation;

    public AdaptiveTextBox(String placeholder) {
        this.placeholder = placeholder;
        this.focusAnimation = new EasingAnimation(Easing.EASE_OUT_EXPO, 300);
        this.cursorAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 120);
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context) {
        focusAnimation.run(focused ? 1 : 0);
        float a = focusAnimation.getValue().floatValue();

        TrueTypeFont font = instance.getFontManager().getFont(19);

        int bgAlpha = (int) (80 + 40 * a);
        Render2D.drawRect(context, x, y, width, height, new Color(0, 0, 0, bgAlpha).getRGB());

        String displayText = text.isEmpty() ? placeholder : (passwordMode ? "*".repeat(text.length()) : text);
        int textColor = text.isEmpty() ? new Color(170, 170, 170).getRGB() : -1;

        float textY = y + (height - font.getHeight()) / 2f;
        float textWidth = font.getStringWidth(displayText);
        float innerWidth = width - 12f;

        String textBeforeCursor = text.isEmpty() ? "" : (passwordMode ? "*".repeat(cursorIndex) : text.substring(0, cursorIndex));
        float cursorTargetOffset = font.getStringWidth(textBeforeCursor);
        float scrollOffset = Math.max(0, cursorTargetOffset - innerWidth);
        float drawX = x + 6 - scrollOffset;

        cursorAnimation.run(drawX + cursorTargetOffset);
        float animCursorX = cursorAnimation.getValue().floatValue();

        Render2D.beginScissor(context, x + 1, y, width - 2, height);

        if (isSelectedAll && !text.isEmpty()) {
            Render2D.drawRect(context, drawX - 1, textY - 1, textWidth + 2, font.getHeight() + 2, new Color(0, 120, 215, 120).getRGB());
        }

        font.drawString(context, displayText, drawX, textY, textColor, false);

        if (focused) {
            float cursorAlpha = (float) Math.abs(Math.sin(System.currentTimeMillis() / 250.0));
            Render2D.drawRect(context, animCursorX + 1, textY + 2, 1, font.getHeight() - 4, new Color(255, 255, 255, (int)(255 * cursorAlpha)).getRGB());
        }

        Render2D.endScissor(context);

        if (a > 0.01f) {
            float lineWidth = width * a;
            float lineX = x + (width - lineWidth) / 2f;
            Render2D.drawRect(context, lineX, y + height - 1, lineWidth, 1, new Color(255, 255, 255, (int)(255 * a)).getRGB());
        }
    }

    public boolean mouseClicked(Click click) {
        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();
            this.focused = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            this.isSelectedAll = false;
            return this.focused;
        }
        return false;
    }

    public boolean keyPressed(KeyInput input) {
        if (!focused) return false;

        if (input.hasCtrl() && input.key() == GLFW.GLFW_KEY_A) {
            if (!text.isEmpty()) {
                this.isSelectedAll = true;
                cursorIndex = text.length();
            }
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            if (cursorIndex > 0) cursorIndex--;
            isSelectedAll = false;
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            if (cursorIndex < text.length()) cursorIndex++;
            isSelectedAll = false;
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
            if (isSelectedAll) {
                text = "";
                cursorIndex = 0;
                isSelectedAll = false;
            } else if (cursorIndex > 0) {
                text = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
                cursorIndex--;
            }
            return true;
        }

        if (input.hasCtrl() && input.key() == GLFW.GLFW_KEY_V) {
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null) {
                String clean = clipboard.replaceAll("[\\p{Cntrl}§]", "");
                if (isSelectedAll) {
                    text = clean;
                    cursorIndex = clean.length();
                    isSelectedAll = false;
                } else {
                    text = text.substring(0, cursorIndex) + clean + text.substring(cursorIndex);
                    cursorIndex += clean.length();
                }
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(CharInput input) {
        if (!focused) return false;

        int codepoint = input.codepoint();
        if (!Character.isISOControl(codepoint) && codepoint != 167 && input.isValidChar()) {
            String ch = input.asString();
            if (isSelectedAll) {
                text = ch;
                cursorIndex = 1;
                isSelectedAll = false;
            } else {
                text = text.substring(0, cursorIndex) + ch + text.substring(cursorIndex);
                cursorIndex++;
            }
            return true;
        }
        return false;
    }
}