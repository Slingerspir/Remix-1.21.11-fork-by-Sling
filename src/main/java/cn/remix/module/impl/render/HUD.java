package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.ChatScreenEvent;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.impl.move.Fly;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.MultiBoolValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.hud.Drag;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.misc.RomanNumeralUtil;
import cn.remix.util.player.MovementUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Getter
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class HUD extends Module {
    private final ModeValue colorMode = new ModeValue("Color Setting", "Rainbow", "Rainbow", "Fade", "Custom");
    private final ColorValue mainColor = new ColorValue("Main Color", Color.WHITE);
    private final ColorValue secondColor = new ColorValue("Second Color", Color.WHITE, () -> colorMode.is("Fade"));

    public final MultiBoolValue hudOptionsProperty = new MultiBoolValue("HUD Options",
            new BoolValue("TabGUI", true),
            new BoolValue("Watermark", true),
            new BoolValue("Potion Effects", true),
            new BoolValue("Display", true),
            new BoolValue("Position", true),
            new BoolValue("Indicators", true)
    );

    private final BoolValue noPotionIcons = new BoolValue("No Potion Icons", true);
    private final BoolValue whiteMode = new BoolValue("White Mode", false);
    private final EasingAnimation yAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 250);
    private final EasingAnimation selectorAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final EasingAnimation moduleAnimation = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final EasingAnimation expandAnimationX = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final EasingAnimation expandAnimationY = new EasingAnimation(Easing.EASE_OUT_QUART, 200);
    private final List<Category> categories = Arrays.stream(Category.values()).toList();

    private int current, moduleIndex;
    private boolean expanded;

    public HUD() {
        super("HUD", Category.Render);
        setEnabled(true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        DrawContext context = event.getContext();
        TrueTypeFont font20 = instance.getFontManager().getFont(20);
        TrueTypeFont font16 = instance.getFontManager().getFont(16);
        TrueTypeFont font18b = instance.getFontManager().getFont(18);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float height = font20.getHeight();


        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            if (module instanceof TargetHUD targetHUD && targetHUD.shouldRenderAnimated()) {
                targetHUD.render(context);
                targetHUD.updatePos();
            } else if (module instanceof Drag drag && drag.isEnabled()) {
                drag.render(context);
                drag.updatePos();
            }
        }
        getModule(Scaffold.class).renderHud(context);
        getModule(Fly.class).renderHud(context);

        yAnimation.run(mc.currentScreen instanceof ChatScreen ? 14 : 0);

        if (hudOptionsProperty.isEnabled("Display")) {
            Disabler disabler = getModule(Disabler.class);
            if (disabler.isEnabled()) {
                String text;

                if (disabler.isWaiting()) {
                    text = Formatting.RED + "!! ALERT: DISABLER DISABLED !!";
                } else {
                    text = String.valueOf(disabler.getPacketQueue().size());
                }

                float textWidth = font18b.getStringWidth(text);
                float textX = (sw - textWidth) / 2f;
                float textY = 60;

                font18b.drawStringWithShadow(context, text, textX, textY, getWhiteMode().getValue() ? -1 : getColor());
            }
        }

        if (hudOptionsProperty.isEnabled("Watermark")) {
            String text = Client.name + " " + Client.version;

            String firstChar = text.substring(0, 1);
            font20.drawStringWithShadow(context, firstChar, 2, 0, getColor());

            String remainder = Formatting.GRAY + text.substring(1);
            font20.drawStringWithShadow(context, remainder, 2 + font20.getStringWidth(firstChar), 0, -1);
        }

        if (hudOptionsProperty.isEnabled("Position")) {
            int fps = mc.getCurrentFps();
            int tps = (int) mc.world.getTickManager().getTickRate();
            double bps = MovementUtil.getBPS();

            String xyzText = "XYZ: ";
            String xyzVal = Math.round(mc.player.getX()) + " " + Math.round(mc.player.getY()) + " " + Math.round(mc.player.getZ()) + " ";
            String fpsText = "FPS: ";
            String fpsVal = fps + " ";
            String tpsText = "TPS: ";
            String tpsVal = tps + " ";
            String bpsText = "BPS: ";
            String bpsVal = String.valueOf(bps);

            float px = 2;
            float py = sh - height - yAnimation.getValue().floatValue();

            font20.drawStringWithShadow(context, xyzText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(xyzText);
            font20.drawStringWithShadow(context, Formatting.GRAY + xyzVal, px, py, -1);
            px += font20.getStringWidth(xyzVal);

            font20.drawStringWithShadow(context, fpsText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(fpsText);
            font20.drawStringWithShadow(context, Formatting.GRAY + fpsVal, px, py, -1);
            px += font20.getStringWidth(fpsVal);

            font20.drawStringWithShadow(context, tpsText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(tpsText);
            font20.drawStringWithShadow(context, Formatting.GRAY + tpsVal, px, py, -1);
            px += font20.getStringWidth(tpsVal);

            font20.drawStringWithShadow(context, bpsText, px, py, getModule(HUD.class).getWhiteMode().getValue() ? -1 : getColor());
            px += font20.getStringWidth(bpsText);
            font20.drawStringWithShadow(context, Formatting.GRAY + bpsVal, px, py, -1);
        }

        if (hudOptionsProperty.isEnabled("Potion Effects")) {
            List<StatusEffectInstance> potions = new ArrayList<>(mc.player.getStatusEffects());
            potions.sort(Comparator.comparingDouble(e -> -font20.getStringWidth(I18n.translate(e.getEffectType().value().getTranslationKey()))));

            float fontH = font20.getHeight();
            float basePy = sh - height - 2 - yAnimation.getValue().floatValue();

            String infoText = "fork by Slingerspir";
            font20.drawStringWithShadow(context, infoText, sw - font20.getStringWidth(infoText) - 2, basePy, -1);

            int count = 0;
            for (StatusEffectInstance effect : potions) {
                StatusEffect potion = effect.getEffectType().value();
                String name = I18n.translate(potion.getTranslationKey()) + (effect.getAmplifier() > 0 ? " " + RomanNumeralUtil.generate(effect.getAmplifier() + 1) : "");

                String durationStr;
                if (effect.getDuration() >= 100000000 || effect.isInfinite() || effect.getDuration() < 0) {
                    durationStr = "**:**";
                } else {
                    durationStr = StatusEffectUtil.getDurationText(effect, 1, mc.world.getTickManager().getTickRate()).getString();
                }

                String text = name + Formatting.WHITE + ": " + Formatting.GRAY + durationStr;
                float py = basePy - fontH - (fontH * count);
                font20.drawStringWithShadow(context, text, sw - font20.getStringWidth(text) - 2, py, potion.getColor() | 0xFF000000);
                count++;
            }
        }

        if (hudOptionsProperty.isEnabled("TabGUI")) {
            int x = 2, y = (int) font20.getHeight(), categoryWidth = 80, itemHeight = 13;
            int categoryHeight = categories.size() * itemHeight;
            selectorAnimation.run(current * itemHeight);

            Render2D.drawRect(context, x, y, categoryWidth, categoryHeight, new Color(23, 23, 23).getRGB());
            Render2D.drawGradient(context, x, y + selectorAnimation.getValue().floatValue(), categoryWidth, itemHeight, getColor(), getColor(4), true);
            for (int i = 0; i < categories.size(); i++) {
                font16.drawStringWithShadow(context, categories.get(i).name(), x + 4, y + 1.5f + i * itemHeight, -1);
            }

            List<Module> modules = instance.getModuleManager().getModuleMap().values().stream().filter(m -> m.getCategory() == categories.get(current)).toList();
            if (modules.isEmpty()) return;

            int width = 0;
            for (Module m : modules) width = (int) Math.max(font16.getStringWidth(m.getName()) + 5, width);

            expandAnimationX.run(expanded ? width : 0);
            expandAnimationY.run(expanded ? modules.size() * itemHeight : 0);
            moduleAnimation.run(moduleIndex * itemHeight);

            float expandX = expandAnimationX.getValue().floatValue(), expandY = expandAnimationY.getValue().floatValue(), moduleY = moduleAnimation.getValue().floatValue();
            if (expandX < 1 || expandY < 1) return;

            float boxX = x + categoryWidth, boxY = y + current * itemHeight;
            Render2D.drawRect(context, boxX, boxY, expandX, expandY, new Color(0, 0, 0, 180).getRGB());
            Render2D.beginScissor(context, boxX, boxY, expandX, expandY);
            if (expanded)
                Render2D.drawRect(context, boxX, boxY + moduleY, width, 12, new Color(0, 0, 0, 120).getRGB());
            for (int i = 0; i < modules.size(); i++) {
                font16.drawStringWithShadow(context, modules.get(i).getName(), boxX + 2, boxY + i * itemHeight + 1.5f, modules.get(i).isEnabled() ? -1 : Color.LIGHT_GRAY.getRGB());
            }
            Render2D.endScissor(context);
        }
    }

    @EventTarget
    public void onChatScreen(ChatScreenEvent event) {
        if (mc.player == null || mc.world == null) return;

        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            if (module instanceof Drag drag && drag.isEnabled()) {
                drag.onChatGUI(event.getMouseX(), event.getMouseY(), GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
            }
        }
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (mc.currentScreen != null) return;
        int code = event.getKey();
        List<Module> modules = instance.getModuleManager().getModuleMap().values().stream().filter(m -> m.getCategory() == categories.get(current)).toList();

        if (!expanded) {
            if (code == GLFW.GLFW_KEY_DOWN && current < categories.size() - 1) { current++; expandAnimationX.setValue(0); expandAnimationY.setValue(0); }
            if (code == GLFW.GLFW_KEY_UP && current > 0) { current--; expandAnimationX.setValue(0); expandAnimationY.setValue(0); }
            if (code == GLFW.GLFW_KEY_RIGHT) { expanded = true; moduleIndex = 0; }
        } else {
            if (code == GLFW.GLFW_KEY_DOWN && modules.size() > moduleIndex + 1) moduleIndex++;
            if (code == GLFW.GLFW_KEY_UP && moduleIndex > 0) moduleIndex--;
            if (code == GLFW.GLFW_KEY_LEFT) { expanded = false; moduleIndex = 0; }
            if ((code == GLFW.GLFW_KEY_RIGHT || code == GLFW.GLFW_KEY_ENTER) && !modules.isEmpty()) modules.get(moduleIndex).toggle();
        }
    }

    public int getColor() {
        return getColor(0);
    }

    public int getColor(int counter) {
        return getColor(counter, 255);
    }

    public int getColor(int counter, int alpha) {
        return switch (colorMode.getValue()) {
            case "Rainbow" -> ColorUtil.getRainbow(counter, alpha);
            case "Fade" -> ColorUtil.getFade(counter, alpha);
            default -> ColorUtil.getCustom(alpha);
        };
    }
}