package cn.remix.ui.clickgui.panel.impl;

import cn.remix.Client;
import cn.remix.config.impl.ModuleConfig;
import cn.remix.ui.clickgui.panel.Panel;
import cn.remix.ui.screen.util.AdaptiveButton;
import cn.remix.ui.screen.util.AdaptiveTextBox;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConfigPanel extends Panel {
    public static final float maxHeight = 200;
    private final List<ConfigItem> items = new CopyOnWriteArrayList<>();
    private final AdaptiveTextBox inputTextBox = new AdaptiveTextBox("Config Name...");
    private ConfigItem selectedItem = null;

    private final AdaptiveButton createButton = new AdaptiveButton("Create", () -> {
        String name = inputTextBox.getText().trim();
        if (!name.isEmpty()) {
            new ModuleConfig(name).save();
            inputTextBox.setText("");
            refreshConfigs();
        }
    });

    private final AdaptiveButton loadButton = new AdaptiveButton("Load", () -> {
        if (selectedItem != null && !selectedItem.removed) {
            new ModuleConfig(selectedItem.name).load();
        }
    });

    private final AdaptiveButton saveButton = new AdaptiveButton("Save", () -> {
        if (selectedItem != null && !selectedItem.removed) {
            new ModuleConfig(selectedItem.name).save();
        }
    });

    private final AdaptiveButton deleteButton = new AdaptiveButton("Delete", () -> {
        if (selectedItem != null && !selectedItem.removed) {
            deleteConfig(selectedItem);
            selectedItem = null;
        }
    });

    public ConfigPanel(float x, float y) {
        super(x, y);
        refreshConfigs();
    }

    public void refreshConfigs() {
        List<String> latest = instance.getConfigManager().getAvailableConfigs();
        items.removeIf(item -> !latest.contains(item.name));
        latest.stream()
                .filter(name -> items.stream().noneMatch(item -> item.name.equals(name)))
                .forEach(name -> items.add(new ConfigItem(name)));
        if (selectedItem != null && !latest.contains(selectedItem.name)) {
            selectedItem = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float globalAlpha) {
        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }

        int alphaInt = (int) (255 * globalAlpha);
        var boldFont = instance.getFontManager().getBoldFont(18);
        var normFont = instance.getFontManager().getFont(16);

        Render2D.drawRect(context, x, y, width, headerHeight, ColorUtil.applyAlpha(new Color(30, 26, 26).getRGB(), alphaInt));
        Render2D.drawRect(context, x, y + headerHeight - 1, width, 1, ColorUtil.applyAlpha(getAccent(), alphaInt));

        boldFont.drawString(context, "Configs", x + 7, y + (headerHeight - boldFont.getHeight()) / 2.0f, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaInt), false);

        float inputY = y + headerHeight;
        Render2D.drawRect(context, x, inputY, width, 62, ColorUtil.applyAlpha(new Color(24, 24, 27).getRGB(), alphaInt));

        inputTextBox.setBounds(x + 4, inputY + 4, width - 8, 16);
        createButton.setBounds(x + 4, inputY + 24, width - 8, 14);

        float btnW = (width - 16) / 3.0f;
        loadButton.setBounds(x + 4, inputY + 42, btnW, 14);
        saveButton.setBounds(x + 4 + btnW + 4, inputY + 42, btnW, 14);
        deleteButton.setBounds(x + 4 + (btnW + 4) * 2, inputY + 42, btnW, 14);

        inputTextBox.render(context);
        createButton.render(context, mouseX, mouseY);
        loadButton.render(context, mouseX, mouseY);
        saveButton.render(context, mouseX, mouseY);
        deleteButton.render(context, mouseX, mouseY);

        Render2D.drawRect(context, x, inputY + 61, width, 1, ColorUtil.applyAlpha(new Color(45, 45, 50).getRGB(), alphaInt));

        float listY = inputY + 62;
        items.forEach(item -> item.anim.run(item.removed ? 0 : 1));

        float totalListHeight = (float) items.stream().mapToDouble(item -> 16 * item.anim.getValue().floatValue()).sum();
        float bodyH = Math.min(totalListHeight, maxHeight);

        Render2D.drawRect(context, x, listY, width, bodyH, ColorUtil.applyAlpha(new Color(22, 22, 25).getRGB(), alphaInt));
        updateScroll(totalListHeight, maxHeight);

        Render2D.beginScissor(context, x, listY, width, bodyH);
        float renderItemY = listY + scrollAnimation.getValue().floatValue();

        for (ConfigItem item : items) {
            float animProgress = item.anim.getValue().floatValue();
            if (animProgress < 0.01f) {
                if (item.removed) {
                    items.remove(item);
                }
                continue;
            }

            float currentHeight = 16 * animProgress;
            boolean hovered = isHovered(mouseX, mouseY, x, renderItemY, width, currentHeight);
            boolean isSelected = selectedItem == item;

            item.hoverAnim.run((hovered || isSelected) ? 1 : 0);
            int bgColor = ColorUtil.interpolate(new Color(22, 22, 25).getRGB(), new Color(42, 42, 48).getRGB(), item.hoverAnim.getValue().floatValue());
            Render2D.drawRect(context, x, renderItemY, width, currentHeight, ColorUtil.applyAlpha(bgColor, (int) (alphaInt * animProgress)));

            int textColor = ColorUtil.interpolate(new Color(170, 170, 170).getRGB(), Color.WHITE.getRGB(), item.hoverAnim.getValue().floatValue());
            normFont.drawString(context, item.name, x + 8, renderItemY + (currentHeight - normFont.getHeight()) / 2.0f, ColorUtil.applyAlpha(textColor, (int) (alphaInt * animProgress)), false);

            renderItemY += currentHeight;
        }
        Render2D.endScissor(context);
    }

    @Override
    public void mouseClicked(Click click) {
        handleDrag(click);
        if (dragging || inputTextBox.mouseClicked(click)) return;

        if (click.button() == 0) {
            if (createButton.isHovered(click.x(), click.y())) {
                createButton.onClick();
                return;
            }
            if (loadButton.isHovered(click.x(), click.y())) {
                loadButton.onClick();
                return;
            }
            if (saveButton.isHovered(click.x(), click.y())) {
                saveButton.onClick();
                return;
            }
            if (deleteButton.isHovered(click.x(), click.y())) {
                deleteButton.onClick();
                return;
            }
        }

        float listY = y + headerHeight + 62;
        if (click.y() >= listY) {
            float renderItemY = listY + scrollAnimation.getValue().floatValue();
            for (ConfigItem item : items) {
                float currentHeight = 16 * item.anim.getValue().floatValue();
                if (isHovered(click.x(), click.y(), x, renderItemY, width, currentHeight)) {
                    if (click.button() == 0) {
                        selectedItem = (selectedItem == item) ? null : item;
                    }
                    break;
                }
                renderItemY += currentHeight;
            }
        }
    }

    private void deleteConfig(ConfigItem item) {
        if (item.name.equalsIgnoreCase("Default")) return;
        item.removed = true;
        File file = new File(new File(Client.name, "configs"), item.name.toLowerCase() + ".json");
        if (file.exists() && !file.delete()) {
            Client.logger.debug("Failed to delete config file: {}", file.getPath());
        }
    }

    @Override
    public boolean keyTyped(KeyInput input) {
        if (inputTextBox.isFocused() && input.key() == GLFW.GLFW_KEY_ENTER) {
            createButton.onClick();
            return true;
        }
        return inputTextBox.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return inputTextBox.charTyped(input);
    }

    private static class ConfigItem {
        String name;
        boolean removed = false;
        EasingAnimation anim = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
        EasingAnimation hoverAnim = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);

        ConfigItem(String name) {
            this.name = name;
        }
    }
}