package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.notification.NotificationManager;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.Util;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

@Getter
public final class Notification extends Module {
    private final ModeValue style = new ModeValue("Style", "Naven", "Off", "Naven");
    private final BoolValue log = new BoolValue("Log", true);
    private final ModeValue logStyle = new ModeValue("Log Style", "xxx Enabled/Disabled",
            "xxx Enabled/Disabled",
            "Toggled xxx on/off.",
            "xxx Activated/Deactivated.");
    private final ModeValue prefixStyle = new ModeValue("Prefix Style", "Remix",
            "None", "Remix", "Bracket", "Arrow", "Debug", "Myau");
    private final ModeValue sound = new ModeValue("Sound", "Button",
            "Off", "Button", "Experience", "Click", "Pling");
    private final BoolValue progress = new BoolValue("Progress", true);
    private final BoolValue actionbar = new BoolValue("Actionbar", false);
    private final NumberValue duration = new NumberValue("Duration", 2500, 1000, 7000, 250);
    private final NumberValue maxVisible = new NumberValue("Max Visible", 4, 1, 10, 1);
    private final NumberValue xOffset = new NumberValue("X Offset", 0, -200, 200, 1);
    private final NumberValue yOffset = new NumberValue("Y Offset", 0, -120, 120, 1);

    public Notification() {
        super("Notification", Category.Render);
        setEnabled(true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!style.is("Naven")) {
            return;
        }

        long durationMillis = duration.getValue().longValue();
        NotificationManager.prune(durationMillis);
        if (NotificationManager.entries().isEmpty()) {
            return;
        }

        TrueTypeFont titleFont = instance.getFontManager().getBoldFont(15);
        TrueTypeFont bodyFont = instance.getFontManager().getFont(14);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();

        int rendered = 0;
        for (NotificationManager.NotificationEntry entry : NotificationManager.entries()) {
            if (rendered >= maxVisible.getValue().intValue()) {
                break;
            }

            String title = entry.getType().getLabel();
            String message = entry.getMessage();
            float titleWidth = titleFont.getStringWidth(title);
            float messageWidth = bodyFont.getStringWidth(message);
            float width = Math.max(132.0f, Math.min(260.0f, Math.max(titleWidth, messageWidth) + 24.0f));
            float height = 42.0f;
            float gap = 7.0f;
            float alpha = entry.alpha(now, durationMillis);
            float progressValue = entry.progress(now, durationMillis);
            float targetX = sw - width - 12.0f + xOffset.getValue();
            float hiddenX = sw + 8.0f + xOffset.getValue();
            float targetY = sh - 14.0f - height - yOffset.getValue() - rendered * (height + gap);

            float slide = entry.progress(now, durationMillis);
            if (slide < 0.12f) {
                slide = 1.0f - (float) Math.pow(1.0f - slide / 0.12f, 3.0);
            } else if (slide > 0.82f) {
                slide = MathHelper.clamp((1.0f - slide) / 0.18f, 0.0f, 1.0f);
            } else {
                slide = 1.0f;
            }
            float slideX = targetX + (hiddenX - targetX) * (1.0f - slide);
            entry.setDestination(slideX, targetY);

            int bg = ColorUtil.applyAlpha(entry.getType().getColor(), (int) (alpha * 232.0f));
            int shadow = new Color(0, 0, 0, (int) (alpha * 95.0f)).getRGB();
            int strip = ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 70.0f));
            int text = ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 255.0f));
            int subText = ColorUtil.applyAlpha(new Color(225, 236, 240).getRGB(), (int) (alpha * 235.0f));

            float x = entry.getX();
            float y = entry.getY();
            Render2D.drawRect(event.getContext(), x + 2.0f, y + 3.0f, width, height, shadow);
            Render2D.drawRect(event.getContext(), x, y, width, height, bg);
            Render2D.drawRect(event.getContext(), x + 6.0f, y + 7.0f, 3.0f, height - 14.0f, strip);

            titleFont.drawString(event.getContext(), title, x + 14.0f, y + 6.0f, text, false);
            bodyFont.drawString(event.getContext(), message, x + 14.0f, y + 22.0f, subText, false);

            if (progress.getValue()) {
                float barWidth = (width - 12.0f) * (1.0f - progressValue);
                Render2D.drawRect(event.getContext(), x + 6.0f, y + height - 4.0f, barWidth, 1.5f, ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 145.0f)));
            }
            rendered++;
        }
    }

    public static void onModuleToggle(Module module, boolean enabled) {
        if (instance == null || instance.getModuleManager() == null) {
            return;
        }

        Notification notification = instance.getModuleManager().getModule(Notification.class);
        if (notification == null || module == notification) {
            return;
        }

        String message = notification.formatToggle(module.getName(), enabled, false, false);
        if (!notification.style.is("Off")) {
            NotificationManager.module(message, enabled);
        }

        if (notification.log.getValue()) {
            notification.sendChat(notification.formatToggle(module.getName(), enabled, true, true));
        }

        if (notification.actionbar.getValue() && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(Text.literal(notification.formatToggle(module.getName(), enabled, false, false)), false);
        }

        notification.playSound(enabled);
    }

    public static void success(String message) {
        NotificationManager.success(message);
    }

    public static void error(String message) {
        NotificationManager.error(message);
    }

    public static void info(String message) {
        NotificationManager.info(message);
    }

    public static void warning(String message) {
        NotificationManager.warning(message);
    }

    private String formatToggle(String name, boolean enabled, boolean colored, boolean prefix) {
        String on = colored ? Formatting.GREEN + "Enabled" + Formatting.RESET : "Enabled";
        String off = colored ? Formatting.RED + "Disabled" + Formatting.RESET : "Disabled";
        String active = colored ? Formatting.GREEN + "Activated" + Formatting.RESET : "Activated";
        String inactive = colored ? Formatting.RED + "Deactivated" + Formatting.RESET : "Deactivated";

        String body = switch (logStyle.getValue()) {
            case "Toggled xxx on/off." -> "Toggled " + name + " " + (enabled ? "on" : "off") + ".";
            case "xxx Activated/Deactivated." -> name + " " + (enabled ? active : inactive) + ".";
            default -> name + " " + (enabled ? on : off);
        };
        return (prefix ? prefix(colored) : "") + body;
    }

    public String prefix(boolean colored) {
        return switch (prefixStyle.getValue()) {
            case "None" -> "";
            case "Bracket" -> colored ? Formatting.DARK_GRAY + "[" + Formatting.AQUA + "Notification" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Notification] ";
            case "Arrow" -> colored ? Formatting.AQUA + "> " + Formatting.RESET : "> ";
            case "Debug" -> colored ? Formatting.DARK_GRAY + "[" + Formatting.RED + "Debug" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Debug] ";
            case "Myau" -> colored ? Formatting.DARK_GRAY + "[" + Formatting.RED + "M" + Formatting.GOLD + "y" + Formatting.YELLOW + "a" + Formatting.GREEN + "u" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Myau] ";
            default -> colored ? Formatting.DARK_GRAY + "[" + Formatting.AQUA + "Remix" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "Remix ";
        };
    }

    private void sendChat(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }

    private void playSound(boolean enabled) {
        SoundEvent event = switch (sound.getValue()) {
            case "Button" -> SoundEvents.UI_BUTTON_CLICK.value();
            case "Experience" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "Click" -> SoundEvents.UI_BUTTON_CLICK.value();
            case "Pling" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            default -> null;
        };

        if (event == null) {
            return;
        }

        float pitch = enabled ? 1.15f : 0.85f;
        mc.getSoundManager().play(PositionedSoundInstance.ui(event, pitch));
    }
}
