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
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.LinkedList;

public final class DynamicIsland extends Module {

    
    
    
    private final BoolValue showUser = new BoolValue("Show User", true);
    private final BoolValue showVolume = new BoolValue("Show Volume", true);
    private final BoolValue showHealth = new BoolValue("Show Health", true);
    private final BoolValue showFPS = new BoolValue("Show FPS", true);
    private final BoolValue showPing = new BoolValue("Show Ping", true);
    private final BoolValue showMemory = new BoolValue("Show Memory", true);
    private final BoolValue showTime = new BoolValue("Show Time", true);
    private final BoolValue showModules = new BoolValue("Show Modules", true);
    private final BoolValue showCPS = new BoolValue("Show CPS", false);
    private final BoolValue showSpeed = new BoolValue("Show Speed", false);

    
    
    
    private final BoolValue moduleNotify = new BoolValue("Module Notify", true);
    private final NumberValue notifyDuration = new NumberValue("Notify Duration", 2000, 500, 5000, 100);

    
    
    
    private final ModeValue style = new ModeValue("Style", "Default",
            "Default", "Glass", "Neon", "Minimal", "Gradient",
            "Compact", "Dark", "Light", "Colorful", "Retro"
    );

    private final ModeValue position = new ModeValue("Position", "TopCenter",
            "TopCenter", "TopLeft", "TopRight", "BottomCenter",
            "BottomLeft", "BottomRight"
    );

    private final BoolValue rounded = new BoolValue("Rounded", true);
    private final NumberValue radius = new NumberValue("Radius", 20, 0, 30, 1);

    
    
    
    private final NumberValue backgroundAlpha = new NumberValue("Background Alpha", 180, 0, 255, 5);

    private final NumberValue textColorRed = new NumberValue("Text R", 255, 0, 255, 1);
    private final NumberValue textColorGreen = new NumberValue("Text G", 255, 0, 255, 1);
    private final NumberValue textColorBlue = new NumberValue("Text B", 255, 0, 255, 1);
    private final BoolValue colorText = new BoolValue("Color Text", false);

    private final NumberValue iconColorRed = new NumberValue("Icon R", 100, 0, 255, 1);
    private final NumberValue iconColorGreen = new NumberValue("Icon G", 200, 0, 255, 1);
    private final NumberValue iconColorBlue = new NumberValue("Icon B", 255, 0, 255, 1);

    private final NumberValue accentColorRed = new NumberValue("Accent R", 0, 0, 255, 1);
    private final NumberValue accentColorGreen = new NumberValue("Accent G", 150, 0, 255, 1);
    private final NumberValue accentColorBlue = new NumberValue("Accent B", 255, 0, 255, 1);

    
    
    
    private final NumberValue islandWidth = new NumberValue("Width", 480, 200, 700, 5);
    private final NumberValue islandHeight = new NumberValue("Height", 40, 28, 60, 1);
    private final NumberValue xOffset = new NumberValue("X Offset", 0, -200, 200, 1);
    private final NumberValue yOffset = new NumberValue("Y Offset", 0, -100, 100, 1);

    
    
    
    private final NumberValue animationSpeed = new NumberValue("Animation Speed", 0.12f, 0.02f, 0.5f, 0.01f);

    
    
    
    private float currentWidth;
    private float targetWidth;
    private float currentAlpha;
    private float targetAlpha;
    private float currentX;
    private float targetX;
    private float currentY;
    private float targetY;
    private float widthVelocity;

    
    
    
    private ModuleNotification currentNotification;
    private boolean moduleNotifyVisible;

    private final Queue<ModuleNotification> notificationQueue = new LinkedList<>();

    
    
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    
    
    
    public DynamicIsland() {
        super("DynamicIsland", Category.Render);
        this.setEnabled(true);
        this.targetWidth = this.islandWidth.getValue().floatValue();
        this.currentWidth = this.targetWidth;
        this.currentAlpha = 1.0f;
        this.targetAlpha = 1.0f;
        this.currentX = 0;
        this.targetX = 0;
        this.currentY = 0;
        this.targetY = 0;
        this.widthVelocity = 0;
        this.currentNotification = null;
        this.moduleNotifyVisible = false;
    }

    
    
    
    @Override
    public void onEnable() {
        this.targetWidth = this.islandWidth.getValue().floatValue();
        this.currentWidth = this.targetWidth;
        this.currentAlpha = 1.0f;
        this.targetAlpha = 1.0f;
        this.currentNotification = null;
        this.moduleNotifyVisible = false;
        this.notificationQueue.clear();
    }

    @Override
    public void onDisable() {
        this.currentNotification = null;
        this.moduleNotifyVisible = false;
        this.notificationQueue.clear();
    }

    
    
    
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        DrawContext context = event.getContext();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        this.updateAnimations();
        this.updateModuleNotification();

        float width = this.currentWidth;
        float height = this.islandHeight.getValue().floatValue();
        float alpha = this.currentAlpha;
        float rad = this.rounded.getValue() ? this.radius.getValue().floatValue() : 0.0f;

        float[] pos = this.getPosition(sw, sh, width, height);
        float x = pos[0];
        float y = pos[1];

        this.targetX = x;
        this.targetY = y;
        float animSpeed = this.animationSpeed.getValue().floatValue();
        this.currentX += (this.targetX - this.currentX) * animSpeed;
        this.currentY += (this.targetY - this.currentY) * animSpeed;

        int textColor = this.getTextColor();
        int iconColor = this.getIconColor();

        this.renderBackground(context, this.currentX, this.currentY, width, height, alpha, rad);
        this.renderContent(context, this.currentX, this.currentY, width, height, textColor, iconColor);
        this.renderBorderDecoration(context, this.currentX, this.currentY, width, height, rad, alpha);

        if (this.moduleNotifyVisible && this.currentNotification != null) {
            this.renderNotification(context, this.currentX, this.currentY, width, height, textColor, iconColor, alpha);
        }
    }

    
    
    
    private void updateAnimations() {
        float speed = this.animationSpeed.getValue().floatValue();

        float widthDiff = this.targetWidth - this.currentWidth;
        this.widthVelocity += widthDiff * speed;
        this.widthVelocity *= 0.85f;
        this.currentWidth += this.widthVelocity;

        if (Math.abs(widthDiff) < 0.5f && Math.abs(this.widthVelocity) < 0.1f) {
            this.currentWidth = this.targetWidth;
            this.widthVelocity = 0.0f;
        }

        this.currentAlpha += (this.targetAlpha - this.currentAlpha) * speed * 2.0f;
        if (Math.abs(this.currentAlpha - this.targetAlpha) < 0.001f) {
            this.currentAlpha = this.targetAlpha;
        }

        float minWidth = this.islandWidth.getValue().floatValue();
        float maxWidth = this.islandWidth.getValue().floatValue() + 150.0f;
        if (this.currentWidth < minWidth) {
            this.currentWidth = minWidth;
        }
        if (this.currentWidth > maxWidth) {
            this.currentWidth = maxWidth;
        }
    }

    
    
    
    private int getTextColor() {
        if (this.colorText.getValue()) {
            return new Color(
                    this.textColorRed.getValue().intValue(),
                    this.textColorGreen.getValue().intValue(),
                    this.textColorBlue.getValue().intValue()
            ).getRGB();
        }
        return Color.WHITE.getRGB();
    }

    private int getIconColor() {
        return new Color(
                this.iconColorRed.getValue().intValue(),
                this.iconColorGreen.getValue().intValue(),
                this.iconColorBlue.getValue().intValue()
        ).getRGB();
    }

    private int getAccentColor() {
        return new Color(
                this.accentColorRed.getValue().intValue(),
                this.accentColorGreen.getValue().intValue(),
                this.accentColorBlue.getValue().intValue()
        ).getRGB();
    }

    
    
    
    private void renderBackground(DrawContext context, float x, float y, float width, float height,
                                  float alpha, float rad) {
        String currentStyle = this.style.getValue();
        int bgAlpha = (int) (this.backgroundAlpha.getValue().floatValue() * alpha);

        if (currentStyle.equals("Glass")) {
            int bg = new Color(255, 255, 255, (int) (bgAlpha * 0.25f)).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int border = new Color(255, 255, 255, (int) (bgAlpha * 0.15f)).getRGB();
            Render2D.drawOutline(context, x, y, width, height, 1.0f, border);
            int highlight = new Color(255, 255, 255, (int) (30.0f * alpha)).getRGB();
            Render2D.drawRect(context, x + 30.0f, y + 2.0f, width - 60.0f, 1.5f, highlight);

        } else if (currentStyle.equals("Neon")) {
            int bg = new Color(0, 0, 0, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int neonColor = this.getAccentColor();
            int neonAlpha = (int) (200.0f * alpha);
            Render2D.drawOutline(context, x, y, width, height, 2.0f,
                    ColorUtil.applyAlpha(neonColor, neonAlpha));
            Render2D.drawOutline(context, x - 4.0f, y - 4.0f, width + 8.0f, height + 8.0f, 1.0f,
                    ColorUtil.applyAlpha(neonColor, (int) (40.0f * alpha)));
            Render2D.drawOutline(context, x + 4.0f, y + 4.0f, width - 8.0f, height - 8.0f, 1.0f,
                    ColorUtil.applyAlpha(neonColor, (int) (20.0f * alpha)));

        } else if (currentStyle.equals("Minimal")) {
            

        } else if (currentStyle.equals("Gradient")) {
            int startColor = new Color(30, 30, 60, bgAlpha).getRGB();
            int endColor = new Color(60, 30, 80, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, startColor);
                Render2D.drawGradient(context, x, y, width, height, startColor, endColor, false);
            } else {
                Render2D.drawGradient(context, x, y, width, height, startColor, endColor, false);
            }

        } else if (currentStyle.equals("Compact")) {
            int bg = new Color(0, 0, 0, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int accentColor = this.getAccentColor();
            Render2D.drawRect(context, x + 20.0f, y + height - 2.0f, width - 40.0f, 1.5f,
                    ColorUtil.applyAlpha(accentColor, (int) (100.0f * alpha)));

        } else if (currentStyle.equals("Dark")) {
            int bg = new Color(10, 10, 15, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int border = new Color(40, 40, 50, (int) (100.0f * alpha)).getRGB();
            Render2D.drawOutline(context, x, y, width, height, 1.0f, border);

        } else if (currentStyle.equals("Light")) {
            int bg = new Color(240, 240, 245, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int border = new Color(200, 200, 210, (int) (80.0f * alpha)).getRGB();
            Render2D.drawOutline(context, x, y, width, height, 1.0f, border);
            Render2D.drawRect(context, x + 2.0f, y + height + 2.0f, width, 3.0f,
                    new Color(0, 0, 0, (int) (20.0f * alpha)).getRGB());

        } else if (currentStyle.equals("Colorful")) {
            int accentColor = this.getAccentColor();
            int bg = new Color(20, 20, 30, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int color1 = new Color(255, 50, 50, (int) (150.0f * alpha)).getRGB();
            int color2 = new Color(50, 255, 50, (int) (150.0f * alpha)).getRGB();
            int color3 = new Color(50, 50, 255, (int) (150.0f * alpha)).getRGB();
            Render2D.drawRect(context, x, y, width, 2.0f, color1);
            Render2D.drawRect(context, x, y + height - 2.0f, width, 2.0f, color2);
            Render2D.drawRect(context, x, y, 2.0f, height, color3);
            Render2D.drawRect(context, x + width - 2.0f, y, 2.0f, height,
                    new Color(255, 50, 255, (int) (150.0f * alpha)).getRGB());

        } else {
            
            int bg = new Color(0, 0, 0, bgAlpha).getRGB();
            if (rad > 0.0f) {
                Render2D.drawRoundedRect(context, x, y, width, height, rad, bg);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            int highlight = new Color(255, 255, 255, (int) (15.0f * alpha)).getRGB();
            Render2D.drawRect(context, x + 20.0f, y + 2.0f, width - 40.0f, 1.0f, highlight);
        }
    }

    
    
    
    private void renderContent(DrawContext context, float x, float y, float width, float height,
                               int textColor, int iconColor) {
        TrueTypeFont font = instance.getFontManager().getFont(16);
        TrueTypeFont iconFont = instance.getFontManager().getFont(16);

        if (font == null) {
            font = instance.getFontManager().getFont(14);
        }
        if (iconFont == null) {
            iconFont = instance.getFontManager().getFont(16);
        }

        float currentX = x + 14.0f;
        float centerY = y + height / 2.0f + 5.0f;
        float spacing = 14.0f;

        
        if (this.showUser.getValue() && mc.player != null) {
            String name = mc.player.getName().getString();
            if (name.length() > 10) {
                name = name.substring(0, 10) + "..";
            }
            iconFont.drawString(context, "\uD83D\uDC64", currentX, centerY - 4.0f, iconColor, false);
            font.drawString(context, " " + name, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(name) + spacing;
        }

        
        if (this.showVolume.getValue()) {
            int volume = this.getSystemVolume();
            String volumeText = volume + "%";
            iconFont.drawString(context, "\uD83D\uDD0A", currentX, centerY - 4.0f, iconColor, false);
            font.drawString(context, " " + volumeText, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(volumeText) + spacing;
        }

        
        if (this.showHealth.getValue() && mc.player != null) {
            int health = (int) Math.ceil(mc.player.getHealth());
            int hpColor;
            if (health > 16) {
                hpColor = new Color(0, 255, 50).getRGB();
            } else if (health > 10) {
                hpColor = new Color(255, 200, 0).getRGB();
            } else {
                hpColor = new Color(255, 50, 50).getRGB();
            }
            iconFont.drawString(context, "\uD83D\uDC96", currentX, centerY - 4.0f, new Color(255, 50, 50).getRGB(), false);
            font.drawString(context, " " + health, currentX + 18.0f, centerY - 4.0f, hpColor, false);
            currentX += 18.0f + font.getStringWidth(String.valueOf(health)) + spacing;
        }

        
        if (this.showFPS.getValue()) {
            int fps = mc.getCurrentFps();
            int fpsColor;
            if (fps > 120) {
                fpsColor = new Color(0, 255, 100).getRGB();
            } else if (fps > 60) {
                fpsColor = new Color(255, 200, 0).getRGB();
            } else {
                fpsColor = new Color(255, 50, 50).getRGB();
            }
            iconFont.drawString(context, "\uD83D\uDFE6", currentX, centerY - 4.0f, new Color(0, 200, 255).getRGB(), false);
            font.drawString(context, " " + fps, currentX + 18.0f, centerY - 4.0f, fpsColor, false);
            currentX += 18.0f + font.getStringWidth(String.valueOf(fps)) + spacing;
        }

        
        if (this.showPing.getValue()) {
            int ping = 0;
            ClientPlayNetworkHandler handler = mc.getNetworkHandler();
            if (handler != null) {
                
                PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
                if (entry != null) {
                    ping = entry.getLatency();
                }
            }
            int pingColor;
            if (ping < 50) {
                pingColor = new Color(0, 255, 100).getRGB();
            } else if (ping < 150) {
                pingColor = new Color(255, 200, 0).getRGB();
            } else {
                pingColor = new Color(255, 50, 50).getRGB();
            }
            String pingText = ping + "ms";
            iconFont.drawString(context, "\uD83D\uDCF6", currentX, centerY - 4.0f, new Color(100, 200, 255).getRGB(), false);
            font.drawString(context, " " + pingText, currentX + 18.0f, centerY - 4.0f, pingColor, false);
            currentX += 18.0f + font.getStringWidth(pingText) + spacing;
        }

        
        if (this.showMemory.getValue()) {
            long used = Runtime.getRuntime().totalMemory() / 1024 / 1024;
            String memText = used + "MB";
            iconFont.drawString(context, "\uD83D\uDCBE", currentX, centerY - 4.0f, new Color(150, 200, 100).getRGB(), false);
            font.drawString(context, " " + memText, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(memText) + spacing;
        }

        
        if (this.showTime.getValue()) {
            String time = this.timeFormat.format(new Date());
            iconFont.drawString(context, "\uD83D\uDD50", currentX, centerY - 4.0f, new Color(255, 200, 50).getRGB(), false);
            font.drawString(context, " " + time, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(time) + spacing;
        }

        
        if (this.showModules.getValue()) {
            int count = 0;
            try {
                count = (int) instance.getModuleManager().getModuleMap().values().stream()
                        .filter(Module::isEnabled)
                        .count();
            } catch (Exception ignored) {
            }
            String countText = String.valueOf(count);
            iconFont.drawString(context, "\uD83D\uDCCD", currentX, centerY - 4.0f, new Color(200, 100, 255).getRGB(), false);
            font.drawString(context, " " + countText, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(countText) + spacing;
        }

        
        if (this.showCPS.getValue()) {
            String cpsText = "0";
            iconFont.drawString(context, "\u2694", currentX, centerY - 4.0f, new Color(255, 100, 100).getRGB(), false);
            font.drawString(context, " " + cpsText, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(cpsText) + spacing;
        }

        
        if (this.showSpeed.getValue() && mc.player != null) {
            double speed = Math.sqrt(Math.pow(mc.player.getVelocity().x, 2.0) +
                    Math.pow(mc.player.getVelocity().z, 2.0));
            float speedKmh = (float) (speed * 20.0 * 3.6);
            String speedText = String.format("%.1f", speedKmh);
            iconFont.drawString(context, "\uD83C\uDFC3", currentX, centerY - 4.0f, new Color(100, 255, 150).getRGB(), false);
            font.drawString(context, " " + speedText, currentX + 18.0f, centerY - 4.0f, textColor, false);
            currentX += 18.0f + font.getStringWidth(speedText) + spacing;
        }

        float contentWidth = currentX - x + 14.0f;
        float newTarget = Math.max(this.islandWidth.getValue().floatValue(), contentWidth);
        if (Math.abs(this.targetWidth - newTarget) > 5.0f) {
            this.targetWidth = newTarget;
        }
    }

    
    
    
    private void renderNotification(DrawContext context, float x, float y, float width, float height,
                                    int textColor, int iconColor, float alpha) {
        if (this.currentNotification == null || !this.moduleNotifyVisible) {
            return;
        }

        TrueTypeFont font = instance.getFontManager().getFont(16);
        TrueTypeFont iconFont = instance.getFontManager().getFont(16);

        if (font == null) {
            font = instance.getFontManager().getFont(14);
        }
        if (iconFont == null) {
            iconFont = instance.getFontManager().getFont(16);
        }

        long elapsed = System.currentTimeMillis() - this.currentNotification.timestamp;
        long duration = this.notifyDuration.getValue().longValue();
        float progress = Math.min(1.0f, (float) elapsed / duration);

        float notifyAlpha = 1.0f;
        if (progress > 0.7f) {
            notifyAlpha = 1.0f - (progress - 0.7f) / 0.3f;
        }

        if (notifyAlpha <= 0.01f) {
            this.moduleNotifyVisible = false;
            this.currentNotification = null;
            return;
        }

        String icon = this.currentNotification.enabled ? "✔" : "✘";
        String status = this.currentNotification.enabled ? "ON" : "OFF";
        String displayText = icon + " " + this.currentNotification.moduleName + " " + status;

        float notifyX = x + width - 10.0f - font.getStringWidth(displayText) - 30.0f;
        float notifyY = y + height / 2.0f + 5.0f - 4.0f;

        int alphaInt = (int) (255.0f * notifyAlpha * alpha);
        int color = ColorUtil.applyAlpha(textColor, alphaInt);

        float bgWidth = font.getStringWidth(displayText) + 34.0f;
        float bgHeight = 26.0f;
        int bgColor = new Color(0, 0, 0, (int) (180.0f * notifyAlpha * alpha)).getRGB();
        Render2D.drawRoundedRect(context, notifyX - 10.0f, y + height / 2.0f - bgHeight / 2.0f,
                bgWidth, bgHeight, 14.0f, bgColor);

        Render2D.drawRect(context, notifyX - 6.0f, y + height / 2.0f + bgHeight / 2.0f - 2.0f,
                bgWidth - 8.0f, 2.0f,
                new Color(255, 255, 255, (int) (30.0f * notifyAlpha * alpha)).getRGB());

        float barWidth = (bgWidth - 8.0f) * (1.0f - progress);
        int barColor = this.currentNotification.enabled ?
                new Color(0, 255, 100, (int) (150.0f * notifyAlpha * alpha)).getRGB() :
                new Color(255, 50, 50, (int) (150.0f * notifyAlpha * alpha)).getRGB();
        Render2D.drawRect(context, notifyX - 6.0f, y + height / 2.0f + bgHeight / 2.0f - 2.0f,
                barWidth, 2.0f, barColor);

        iconFont.drawString(context, icon, notifyX + 2.0f, notifyY, iconColor, false);
        font.drawString(context, " " + this.currentNotification.moduleName + " " + status,
                notifyX + 20.0f, notifyY, color, false);
    }

    
    
    
    private void renderBorderDecoration(DrawContext context, float x, float y, float width, float height,
                                        float rad, float alpha) {
        String currentStyle = this.style.getValue();
        int accentColor = this.getAccentColor();

        if (currentStyle.equals("Glass")) {
            int dotColor = ColorUtil.applyAlpha(accentColor, (int) (80.0f * alpha));
            Render2D.drawRect(context, x + 6.0f, y + 6.0f, 4.0f, 4.0f, dotColor);
            Render2D.drawRect(context, x + width - 10.0f, y + 6.0f, 4.0f, 4.0f, dotColor);
            Render2D.drawRect(context, x + 6.0f, y + height - 10.0f, 4.0f, 4.0f, dotColor);
            Render2D.drawRect(context, x + width - 10.0f, y + height - 10.0f, 4.0f, 4.0f, dotColor);

        } else if (currentStyle.equals("Neon")) {
            int neonDot = ColorUtil.applyAlpha(accentColor, (int) (120.0f * alpha));
            Render2D.drawRect(context, x + 2.0f, y + 2.0f, 6.0f, 2.0f, neonDot);
            Render2D.drawRect(context, x + 2.0f, y + 2.0f, 2.0f, 6.0f, neonDot);
            Render2D.drawRect(context, x + width - 8.0f, y + 2.0f, 6.0f, 2.0f, neonDot);
            Render2D.drawRect(context, x + width - 4.0f, y + 2.0f, 2.0f, 6.0f, neonDot);
            Render2D.drawRect(context, x + 2.0f, y + height - 4.0f, 6.0f, 2.0f, neonDot);
            Render2D.drawRect(context, x + 2.0f, y + height - 8.0f, 2.0f, 6.0f, neonDot);
            Render2D.drawRect(context, x + width - 8.0f, y + height - 4.0f, 6.0f, 2.0f, neonDot);
            Render2D.drawRect(context, x + width - 4.0f, y + height - 8.0f, 2.0f, 6.0f, neonDot);

        } else if (currentStyle.equals("Default")) {
            int dotColor = ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (40.0f * alpha));
            Render2D.drawRect(context, x + 8.0f, y + 8.0f, 3.0f, 3.0f, dotColor);
            Render2D.drawRect(context, x + width - 11.0f, y + 8.0f, 3.0f, 3.0f, dotColor);
        }
    }

    
    
    
    private void updateModuleNotification() {
        if (!this.moduleNotify.getValue()) {
            this.moduleNotifyVisible = false;
            return;
        }

        if (this.currentNotification == null || !this.moduleNotifyVisible) {
            var entries = NotificationManager.entries();
            if (!entries.isEmpty()) {
                var last = entries.get(entries.size() - 1);
                String msg = last.getMessage();

                String moduleName = "";
                boolean enabled = false;

                if (msg.contains("Enabled") || msg.contains("ON") || msg.contains("Activated")) {
                    moduleName = msg.replaceAll("(Toggled |Module | is now | on/off\\.| Activated/Deactivated\\.| → | ON| OFF|\\.)", "").trim();
                    enabled = true;
                } else if (msg.contains("Disabled") || msg.contains("OFF") || msg.contains("Deactivated")) {
                    moduleName = msg.replaceAll("(Toggled |Module | is now | on/off\\.| Activated/Deactivated\\.| → | ON| OFF|\\.)", "").trim();
                    enabled = false;
                }

                if (!moduleName.isEmpty()) {
                    this.currentNotification = new ModuleNotification(moduleName, enabled);
                    this.moduleNotifyVisible = true;
                    this.targetWidth = this.islandWidth.getValue().floatValue() + 60.0f;
                }
            }
        }

        if (this.currentNotification != null && this.moduleNotifyVisible) {
            long elapsed = System.currentTimeMillis() - this.currentNotification.timestamp;
            if (elapsed > this.notifyDuration.getValue().longValue()) {
                this.moduleNotifyVisible = false;
                this.currentNotification = null;
                this.targetWidth = this.islandWidth.getValue().floatValue();
            }
        }
    }

    
    
    
    private float[] getPosition(int sw, int sh, float width, float height) {
        String pos = this.position.getValue();
        float xOff = this.xOffset.getValue().floatValue();
        float yOff = this.yOffset.getValue().floatValue();

        float x, y;

        if (pos.equals("TopCenter")) {
            x = (sw - width) / 2.0f + xOff;
            y = 8.0f + yOff;
        } else if (pos.equals("TopLeft")) {
            x = 8.0f + xOff;
            y = 8.0f + yOff;
        } else if (pos.equals("TopRight")) {
            x = sw - width - 8.0f + xOff;
            y = 8.0f + yOff;
        } else if (pos.equals("BottomCenter")) {
            x = (sw - width) / 2.0f + xOff;
            y = sh - height - 8.0f + yOff;
        } else if (pos.equals("BottomLeft")) {
            x = 8.0f + xOff;
            y = sh - height - 8.0f + yOff;
        } else if (pos.equals("BottomRight")) {
            x = sw - width - 8.0f + xOff;
            y = sh - height - 8.0f + yOff;
        } else {
            x = (sw - width) / 2.0f + xOff;
            y = 8.0f + yOff;
        }

        return new float[]{x, y};
    }

    
    
    
    private int getSystemVolume() {
        try {
            String os = System.getProperty("os.name");
            if (os.contains("Windows")) {
                return 80;
            } else if (os.contains("Mac")) {
                return 80;
            } else {
                return 70;
            }
        } catch (Exception e) {
            return 80;
        }
    }

    
    
    
    @Override
    public String getSuffix() {
        return this.style.getValue();
    }

    
    
    
    private static final class ModuleNotification {
        final String moduleName;
        final boolean enabled;
        final long timestamp;

        ModuleNotification(String name, boolean enabled) {
            this.moduleName = name;
            this.enabled = enabled;
            this.timestamp = System.currentTimeMillis();
        }
    }

    
    
    
    public static void onModuleToggle(Module module, boolean enabled) {
        if (instance == null) {
            return;
        }
        DynamicIsland island = instance.getModuleManager().getModule(DynamicIsland.class);
        if (island != null && island.moduleNotify.getValue()) {
            island.currentNotification = new ModuleNotification(module.getName(), enabled);
            island.moduleNotifyVisible = true;
            island.targetWidth = island.islandWidth.getValue().floatValue() + 60.0f;
        }
    }
}