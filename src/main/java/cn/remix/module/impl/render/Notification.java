package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.module.value.impl.StringValue;
import cn.remix.notification.BeautifulState;
import cn.remix.notification.NotificationManager;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.animation.Easing;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.LiquidGlassUtil;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class Notification extends Module {

    private static final String[] CURVES = {
            "Linear", "Ease In Quad", "Ease Out Quad", "Ease In Out Quad",
            "Ease In Cubic", "Ease Out Cubic", "Ease In Out Cubic",
            "Ease In Quart", "Ease Out Quart", "Ease In Out Quart",
            "Ease In Quint", "Ease Out Quint", "Ease In Out Quint",
            "Ease In Sine", "Ease Out Sine", "Ease In Out Sine",
            "Ease In Expo", "Ease Out Expo", "Ease In Out Expo",
            "Ease In Circ", "Ease Out Circ",
            "Ease Out Elastic", "Ease In Back", "Ease Out Back"
    };

    
    private final ModeValue style = new ModeValue("Style", "Naven",
            "Off",
            "Naven",
            "Beautiful",
            "Windows 11",
            "Android 12",
            "macOS",
            "iOS",
            "Linux GNOME",
            "Material You",
            "Fluent Design",
            "Neumorphism",
            "Glassmorphism",
            "Retro Pixel",
            "Hacker Terminal",
            "Cyberpunk 2077",
            "Minimal",
            "Glow Neon"
    );

    
    private final BoolValue log = new BoolValue("Log", true);
    private final ModeValue logStyle = new ModeValue("Log Style", "xxx Enabled/Disabled",
            "xxx Enabled/Disabled",
            "Toggled xxx on/off.",
            "xxx Activated/Deactivated.",
            "xxx → ON/OFF",
            "Module xxx is now ON/OFF");
    private final ModeValue prefixStyle = new ModeValue("Prefix Style", "Remix",
            "None", "Remix", "Bracket", "Arrow", "Debug", "Myau", "Sharp", "Dot", "Star");
    private final ModeValue sound = new ModeValue("Sound", "Button",
            "Off", "Button", "Experience", "Click", "Pling", "Pop", "Chime");
    private final BoolValue progress = new BoolValue("Progress", true);
    private final BoolValue actionbar = new BoolValue("Actionbar", false);
    private final NumberValue duration = new NumberValue("Duration", 2500, 1000, 7000, 250);
    private final NumberValue maxVisible = new NumberValue("Max Visible", 4, 1, 10, 1);
    private final NumberValue xOffset = new NumberValue("X Offset", 0, -200, 200, 1);
    private final NumberValue yOffset = new NumberValue("Y Offset", 0, -120, 120, 1);
    private final ModeValue position = new ModeValue("Position", "TopRight",
            "TopRight", "TopLeft", "BottomRight", "BottomLeft", "TopCenter", "BottomCenter");

    
    private final NumberValue openDuration = new NumberValue("Open Duration", 350, 150, 1200, 10, () -> style.is("Beautiful"));
    private final NumberValue sweepDuration = new NumberValue("Sweep Duration", 260, 100, 1000, 10, () -> style.is("Beautiful"));
    private final NumberValue eraseDuration = new NumberValue("Erase Duration", 450, 150, 1500, 10, () -> style.is("Beautiful"));
    private final NumberValue closeDuration = new NumberValue("Close Duration", 350, 150, 1200, 10, () -> style.is("Beautiful"));
    private final NumberValue contractDuration = new NumberValue("Contract Duration", 350, 150, 1200, 10, () -> style.is("Beautiful"));
    private final NumberValue lastDelay = new NumberValue("Last Close Delay", 180, 0, 1000, 10, () -> style.is("Beautiful"));
    private final NumberValue lastEraseDuration = new NumberValue("Last Erase Duration", 450, 150, 1500, 10, () -> style.is("Beautiful"));
    private final NumberValue barWidth = new NumberValue("Bar Width", 3, 1, 30, 1, () -> style.is("Beautiful"));
    private final NumberValue textPadding = new NumberValue("Text Padding", 10, 4, 60, 1, () -> style.is("Beautiful"));
    private final NumberValue backgroundOpacity = new NumberValue("Background Opacity", 50, 0, 100, 1, () -> style.is("Beautiful"));
    private final NumberValue curtainAlpha = new NumberValue("Curtain Alpha", 255, 0, 255, 1, () -> style.is("Beautiful"));
    private final NumberValue groupWindow = new NumberValue("Group Window", 250, 50, 1000, 10, () -> style.is("Beautiful"));
    private final NumberValue beautifulSize = new NumberValue("Size", 100, 60, 200, 5, () -> style.is("Beautiful"));
    private final NumberValue animationSpeed = new NumberValue("Animation Speed", 100, 25, 400, 5, () -> style.is("Beautiful"));
    private final ModeValue openCurve = new ModeValue("Open Curve", "Ease Out Cubic", () -> style.is("Beautiful"), CURVES);
    private final ModeValue sweepCurve = new ModeValue("Sweep Curve", "Ease Out Cubic", () -> style.is("Beautiful"), CURVES);
    private final ModeValue eraseCurve = new ModeValue("Erase Curve", "Ease Out Cubic", () -> style.is("Beautiful"), CURVES);
    private final ModeValue coverCurve = new ModeValue("Cover Curve", "Ease In Cubic", () -> style.is("Beautiful"), CURVES);
    private final ModeValue contractCurve = new ModeValue("Contract Curve", "Ease In Cubic", () -> style.is("Beautiful"), CURVES);
    private final ModeValue textCurve = new ModeValue("Text Curve", "Ease Out Cubic", () -> style.is("Beautiful"), CURVES);
    private final StringValue enableText = new StringValue("Enable Text", "Enable", () -> style.is("Beautiful"));
    private final StringValue disableText = new StringValue("Disable Text", "Disable", () -> style.is("Beautiful"));
    private final ColorValue enableColor = new ColorValue("Enable Color", new Color(0, 255, 110), () -> style.is("Beautiful"));
    private final ColorValue disableColor = new ColorValue("Disable Color", new Color(255, 60, 60), () -> style.is("Beautiful"));

    public Notification() {
        super("Notification", Category.Render);
        setEnabled(true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (style.is("Off")) {
            return;
        }

        if (style.is("Beautiful") && (position.is("TopRight") || position.is("BottomRight"))) {
            renderBeautiful(event);
            return;
        }

        long durationMillis = duration.getValue().longValue();
        NotificationManager.prune(durationMillis);
        if (NotificationManager.entries().isEmpty()) {
            return;
        }

        TrueTypeFont titleFont = instance.getFontManager().getBoldFont(15);
        TrueTypeFont bodyFont = instance.getFontManager().getFont(14);
        DrawContext context = event.getContext();
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
            float width = Math.max(160.0f, Math.min(300.0f, Math.max(titleWidth, messageWidth) + 32.0f));
            float height = getHeightByStyle();
            float gap = 8.0f;
            float alpha = entry.alpha(now, durationMillis);
            float progressValue = entry.progress(now, durationMillis);

            float[] pos = getPosition(sw, sh, width, height, rendered, gap);
            float targetX = pos[0];
            float targetY = pos[1];
            float hiddenX = pos[2];

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

            float x = entry.getX();
            float y = entry.getY();

            renderByStyle(context, x, y, width, height, alpha, progressValue, entry, title, message, titleFont, bodyFont);
            rendered++;
        }
    }

    
    
    
    private void renderBeautiful(Render2DEvent event) {
        List<NotificationManager.NotificationEntry> entries = NotificationManager.entries();
        if (entries.isEmpty()) {
            return;
        }

        DrawContext context = event.getContext();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();
        float scale = beautifulScale();
        TrueTypeFont titleFont = instance.getFontManager().getBoldFont(beautifulFontSize(15, scale));
        TrueTypeFont bodyFont = instance.getFontManager().getFont(beautifulFontSize(14, scale));

        float gap = 8.0f;
        float height = 44.0f * scale;
        int maxVisible = this.maxVisible.getValue().intValue();
        long window = this.groupWindow.getValue().longValue();

        
        List<List<NotificationManager.NotificationEntry>> runs = new ArrayList<>();
        List<NotificationManager.NotificationEntry> run = new ArrayList<>();
        for (NotificationManager.NotificationEntry entry : entries) {
            if (!run.isEmpty()) {
                long diff = run.get(run.size() - 1).getCreatedAt() - entry.getCreatedAt();
                if (diff > window) {
                    runs.add(run);
                    run = new ArrayList<>();
                }
            }
            run.add(entry);
        }
        if (!run.isEmpty()) {
            runs.add(run);
        }

        List<NotificationManager.NotificationEntry> toRemove = new ArrayList<>();
        int rendered = 0;

        for (List<NotificationManager.NotificationEntry> group : runs) {
            if (rendered >= maxVisible) {
                break;
            }

            NotificationManager.NotificationEntry leader = group.get(0);
            BeautifulState state = leader.getBeautifulState();
            if (state == null) {
                state = new BeautifulState();
                leader.setBeautifulState(state);
            }
            advanceBeautiful(state, group, entries, now);

            if (state.phase == BeautifulState.Phase.DONE) {
                toRemove.addAll(group);
                continue;
            }

            float width = 0.0f;
            for (NotificationManager.NotificationEntry entry : group) {
                width = Math.max(width, computeBeautifulWidth(entry, titleFont, bodyFont));
            }

            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (NotificationManager.NotificationEntry entry : group) {
                int listIndex = entries.indexOf(entry);
                float[] pos = getPosition(sw, sh, width, height, listIndex, gap);
                entry.setDestination(pos[0], pos[1]);
                minX = Math.min(minX, entry.getX());
                minY = Math.min(minY, entry.getY());
                maxY = Math.max(maxY, entry.getY() + height);
            }

            float anchorX = minX;
            float anchorY = minY;
            float groupWidth = width;
            float groupHeight = maxY - minY;
            float progress = beautifulProgress(state, now, group.size() > 1);

            for (NotificationManager.NotificationEntry entry : group) {
                if (rendered >= maxVisible) {
                    break;
                }
                renderBeautifulEntry(context, entry, width, height, state, now, progress,
                        anchorX, anchorY, groupWidth, groupHeight, group.size() > 1,
                        titleFont, bodyFont);
                rendered++;
            }
        }

        if (!toRemove.isEmpty()) {
            entries.removeAll(toRemove);
        }
    }

    private void advanceBeautiful(BeautifulState state, List<NotificationManager.NotificationEntry> group,
                                  List<NotificationManager.NotificationEntry> all, long now) {
        switch (state.phase) {
            case OPEN:
                if (beautifulProgress(state, now, group.size() > 1) >= 1.0f) {
                    startBeautifulPhase(state, BeautifulState.Phase.ERASE);
                }
                break;
            case ERASE:
                if (beautifulProgress(state, now, true) >= 1.0f) {
                    startBeautifulPhase(state, BeautifulState.Phase.HOLD);
                }
                break;
            case HOLD:
                if (beautifulProgress(state, now, true) >= 1.0f) {
                    startBeautifulPhase(state, BeautifulState.Phase.COVER);
                }
                break;
            case COVER:
                if (beautifulProgress(state, now, true) >= 1.0f) {
                    boolean lastOnly = group.size() == 1 && all.size() == 1;
                    startBeautifulPhase(state, lastOnly ? BeautifulState.Phase.LAST_ERASE : BeautifulState.Phase.CONTRACT);
                }
                break;
            case CONTRACT:
                if (beautifulProgress(state, now, true) >= 1.0f) {
                    state.phase = BeautifulState.Phase.DONE;
                }
                break;
            case LAST_ERASE:
                if (beautifulProgress(state, now, true) >= 1.0f) {
                    state.phase = BeautifulState.Phase.DONE;
                }
                break;
            default:
                break;
        }
    }

    private void startBeautifulPhase(BeautifulState state, BeautifulState.Phase phase) {
        state.phase = phase;
        state.phaseStart = System.currentTimeMillis();
    }

    private long beautifulDurationMs(BeautifulState.Phase phase, boolean group) {
        long base;
        switch (phase) {
            case OPEN:
                base = (group ? openDuration : sweepDuration).getValue().longValue();
                break;
            case ERASE:
                base = eraseDuration.getValue().longValue();
                break;
            case HOLD:
                base = duration.getValue().longValue();
                break;
            case COVER:
                base = closeDuration.getValue().longValue();
                break;
            case CONTRACT:
                base = contractDuration.getValue().longValue();
                break;
            case LAST_ERASE:
                base = lastDelay.getValue().longValue() + lastEraseDuration.getValue().longValue();
                break;
            default:
                return 0L;
        }
        return beautifulScaled(base);
    }

    private float beautifulProgress(BeautifulState state, long now, boolean group) {
        long duration = beautifulDurationMs(state.phase, group);
        if (duration <= 0L) {
            return 1.0f;
        }
        return MathHelper.clamp((now - state.phaseStart) / (float) duration, 0.0f, 1.0f);
    }

    private void renderBeautifulEntry(DrawContext context, NotificationManager.NotificationEntry entry,
                                      float width, float height, BeautifulState state, long now, float progress,
                                      float anchorX, float anchorY, float groupWidth, float groupHeight,
                                      boolean group, TrueTypeFont titleFont, TrueTypeFont bodyFont) {
        float x = entry.getX();
        float y = entry.getY();
        int curtain = curtainColor(entry);
        int bg = new Color(0, 0, 0, Math.round(255.0f * this.backgroundOpacity.getValue().floatValue() / 100.0f)).getRGB();
        float scale = beautifulScale();
        float pad = this.textPadding.getValue().floatValue() * scale;
        float barW = this.barWidth.getValue().floatValue();

        switch (state.phase) {
            case OPEN: {
                float e = clampEase(group ? openCurve : sweepCurve, progress);
                if (group) {
                    
                    drawClippedRect(context, x, y, width, height, anchorX, anchorY, groupWidth * e, groupHeight * e, bg);
                    drawClippedRect(context, x, y, width, height, anchorX, anchorY, groupWidth * e, groupHeight * e, curtain);
                } else {
                    
                    drawClippedRect(context, x, y, width, height, x, y, width * e, height, bg);
                    drawClippedRect(context, x, y, width, height, x, y, width * e, height, curtain);
                }
                break;
            }
            case ERASE: {
                float e = clampEase(eraseCurve, progress);
                float reveal = Math.max(0.0f, width - barW) * e;
                Render2D.drawRect(context, x, y, width, height, bg);
                drawBeautifulText(context, entry, x, y, width, height, titleFont, bodyFont, pad, reveal, clampEase(textCurve, e));
                if (reveal < width) {
                    
                    Render2D.drawRect(context, x + reveal, y, width - reveal, height, curtain);
                }
                break;
            }
            case HOLD: {
                Render2D.drawRect(context, x, y, width, height, bg);
                drawBeautifulText(context, entry, x, y, width, height, titleFont, bodyFont, pad, width, 1.0f);
                drawBar(context, x, y, width, height, barW, curtain);
                break;
            }
            case COVER: {
                float e = clampEase(coverCurve, progress);
                Render2D.drawRect(context, x, y, width, height, bg);
                drawBeautifulText(context, entry, x, y, width, height, titleFont, bodyFont, pad, width, 1.0f - e);
                drawBar(context, x, y, width, height, barW, curtain);
                if (e > 0.0f) {
                    Render2D.drawRect(context, x + width - width * e, y, width * e, height, curtain);
                }
                break;
            }
            case CONTRACT: {
                float e = clampEase(contractCurve, progress);
                
                drawClippedRect(context, x, y, width, height, anchorX, anchorY,
                        groupWidth * (1.0f - e), groupHeight * (1.0f - e), curtain);
                break;
            }
            case LAST_ERASE: {
                long elapsed = now - state.phaseStart;
                long delay = beautifulScaled(lastDelay.getValue().longValue());
                if (elapsed < delay) {
                    Render2D.drawRect(context, x, y, width, height, curtain);
                } else {
                    float p = MathHelper.clamp((elapsed - delay) / (float) beautifulScaled(lastEraseDuration.getValue().longValue()), 0.0f, 1.0f);
                    float e = clampEase(eraseCurve, p);
                    float remain = width * (1.0f - e);
                    Render2D.drawRect(context, x, y, Math.max(0.0f, remain), height, curtain);
                }
                break;
            }
            default:
                break;
        }
    }

    private void drawBeautifulText(DrawContext context, NotificationManager.NotificationEntry entry,
                                   float x, float y, float width, float height,
                                   TrueTypeFont titleFont, TrueTypeFont bodyFont,
                                   float pad, float clipWidth, float alpha) {
        if (clipWidth <= 0.0f || alpha <= 0.01f) {
            return;
        }
        int a = Math.round(255.0f * MathHelper.clamp(alpha, 0.0f, 1.0f));
        int color1 = ColorUtil.applyAlpha(Color.WHITE.getRGB(), a);
        int color2 = ColorUtil.applyAlpha(new Color(228, 228, 235).getRGB(), a);
        float scale = beautifulScale();

        Render2D.beginScissor(context, x, y, Math.min(clipWidth, width), height);
        titleFont.drawString(context, beautifulLine1(entry), x + pad, y + 6.0f * scale, color1, false);
        bodyFont.drawString(context, beautifulLine2(entry), x + pad, y + 24.0f * scale, color2, false);
        Render2D.endScissor(context);
    }

    private void drawClippedRect(DrawContext context, float x, float y, float width, float height,
                                 float clipX, float clipY, float clipW, float clipH, int color) {
        float cx = Math.max(x, clipX);
        float cy = Math.max(y, clipY);
        float cxx = Math.min(x + width, clipX + clipW);
        float cyy = Math.min(y + height, clipY + clipH);
        if (cxx <= cx || cyy <= cy) {
            return;
        }
        Render2D.beginScissor(context, cx, cy, cxx - cx, cyy - cy);
        Render2D.drawRect(context, x, y, width, height, color);
        Render2D.endScissor(context);
    }

    private void drawBar(DrawContext context, float x, float y, float width, float height, float barWidth, int color) {
        if (barWidth <= 0.0f) {
            return;
        }
        float scale = beautifulScale();
        Render2D.drawRect(context, x + width - barWidth, y + 4.0f * scale, barWidth, height - 8.0f * scale, color);
    }

    private float computeBeautifulWidth(NotificationManager.NotificationEntry entry,
                                        TrueTypeFont titleFont, TrueTypeFont bodyFont) {
        float textWidth = Math.max(titleFont.getStringWidth(beautifulLine1(entry)),
                bodyFont.getStringWidth(beautifulLine2(entry)));
        float scale = beautifulScale();
        float pad = this.textPadding.getValue().floatValue() * scale;
        return Math.max(160.0f * scale, Math.min(300.0f * scale, textWidth + pad * 2.0f + 28.0f));
    }

    private String beautifulLine1(NotificationManager.NotificationEntry entry) {
        if (entry.getModuleName() != null) {
            return entry.getCategoryName() == null ? entry.getType().getLabel() : entry.getCategoryName();
        }
        return entry.getType().getLabel();
    }

    private String beautifulLine2(NotificationManager.NotificationEntry entry) {
        if (entry.getModuleName() != null) {
            return entry.getModuleName() + " · " + (entry.isEnabled() ? this.enableText.getValue() : this.disableText.getValue());
        }
        return entry.getMessage();
    }

    private int curtainColor(NotificationManager.NotificationEntry entry) {
        int alpha = this.curtainAlpha.getValue().intValue();
        if (entry.getModuleName() != null) {
            Color color = entry.isEnabled() ? this.enableColor.getValue() : this.disableColor.getValue();
            return ColorUtil.applyAlpha(color.getRGB(), alpha);
        }
        return ColorUtil.applyAlpha(entry.getType().getColor(), alpha);
    }

    private float clampEase(ModeValue curve, float progress) {
        double value = easing(curve.getValue()).getFunction().apply((double) MathHelper.clamp(progress, 0.0f, 1.0f));
        return (float) MathHelper.clamp(value, 0.0, 1.0);
    }

    private float beautifulScale() {
        return this.beautifulSize.getValue().floatValue() / 100.0f;
    }

    private int beautifulFontSize(int base, float scale) {
        return Math.max(8, Math.min(40, Math.round(base * scale)));
    }

    private long beautifulScaled(long millis) {
        float speed = this.animationSpeed.getValue().floatValue();
        return Math.max(1L, Math.round(millis * 100.0f / speed));
    }

    private Easing easing(String name) {
        try {
            return Easing.valueOf(name.toUpperCase().replace(' ', '_'));
        } catch (Exception ignored) {
            return Easing.EASE_OUT_CUBIC;
        }
    }

    private float getHeightByStyle() {
        String currentStyle = style.getValue();
        if (currentStyle.equals("Minimal") || currentStyle.equals("Hacker Terminal") || currentStyle.equals("Retro Pixel")) {
            return 36.0f;
        }
        if (currentStyle.equals("macOS") || currentStyle.equals("iOS") || currentStyle.equals("Linux GNOME")) {
            return 50.0f;
        }
        if (currentStyle.equals("Neumorphism") || currentStyle.equals("Glassmorphism")) {
            return 55.0f;
        }
        if (currentStyle.equals("Cyberpunk 2077")) {
            return 52.0f;
        }
        return 44.0f;
    }

    private float[] getPosition(int sw, int sh, float width, float height, int index, float gap) {
        String pos = position.getValue();
        float yOffsetVal = yOffset.getValue();
        float xOffsetVal = xOffset.getValue();
        float baseX, baseY, hiddenX;
        float totalHeight = height + gap;

        if (pos.equals("TopLeft")) {
            baseX = 12.0f + xOffsetVal;
            baseY = 12.0f + yOffsetVal + index * totalHeight;
            hiddenX = -width - 20.0f;
        } else if (pos.equals("BottomRight")) {
            baseX = sw - width - 12.0f + xOffsetVal;
            baseY = sh - 12.0f - height - yOffsetVal - index * totalHeight;
            hiddenX = sw + width + 20.0f;
        } else if (pos.equals("BottomLeft")) {
            baseX = 12.0f + xOffsetVal;
            baseY = sh - 12.0f - height - yOffsetVal - index * totalHeight;
            hiddenX = -width - 20.0f;
        } else if (pos.equals("TopCenter")) {
            baseX = (sw - width) / 2.0f + xOffsetVal;
            baseY = 12.0f + yOffsetVal + index * totalHeight;
            hiddenX = -width - 20.0f;
        } else if (pos.equals("BottomCenter")) {
            baseX = (sw - width) / 2.0f + xOffsetVal;
            baseY = sh - 12.0f - height - yOffsetVal - index * totalHeight;
            hiddenX = -width - 20.0f;
        } else {
            baseX = sw - width - 12.0f + xOffsetVal;
            baseY = 12.0f + yOffsetVal + index * totalHeight;
            hiddenX = sw + width + 20.0f;
        }
        return new float[]{baseX, baseY, hiddenX};
    }

    private void renderByStyle(DrawContext context, float x, float y, float width, float height, float alpha,
                               float progressValue, NotificationManager.NotificationEntry entry,
                               String title, String message, TrueTypeFont titleFont, TrueTypeFont bodyFont) {

        int color = entry.getType().getColor();
        String currentStyle = style.getValue();

        
        if (currentStyle.equals("Beautiful")) {
            currentStyle = "Naven";
        }

        
        if (currentStyle.equals("Naven")) {
            int bg = ColorUtil.applyAlpha(color, (int) (alpha * 232.0f));
            int shadow = new Color(0, 0, 0, (int) (alpha * 95.0f)).getRGB();
            int strip = ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 70.0f));
            int text = ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 255.0f));
            int subText = ColorUtil.applyAlpha(new Color(225, 236, 240).getRGB(), (int) (alpha * 235.0f));

            Render2D.drawRect(context, x + 2.0f, y + 3.0f, width, height, shadow);
            if (LiquidGlassUtil.isGlass()) {
                LiquidGlassUtil.drawGlass(context, x, y, width, height);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            Render2D.drawRect(context, x + 6.0f, y + 7.0f, 3.0f, height - 14.0f, strip);
            titleFont.drawString(context, title, x + 14.0f, y + 6.0f, text, false);
            bodyFont.drawString(context, message, x + 14.0f, y + 22.0f, subText, false);

            if (progress.getValue()) {
                float barWidth = (width - 12.0f) * (1.0f - progressValue);
                Render2D.drawRect(context, x + 6.0f, y + height - 4.0f, barWidth, 1.5f,
                        ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 145.0f)));
            }
            return;
        }

        int white = ColorUtil.applyAlpha(Color.WHITE.getRGB(), (int) (alpha * 255.0f));
        int gray = ColorUtil.applyAlpha(new Color(200, 200, 210).getRGB(), (int) (alpha * 230.0f));

        
        if (currentStyle.equals("Windows 11")) {
            int bg = new Color(40, 40, 45, (int) (alpha * 200)).getRGB();
            if (LiquidGlassUtil.isGlass()) {
                LiquidGlassUtil.drawGlass(context, x, y, width, height);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            Render2D.drawRect(context, x, y, width, 1, new Color(255, 255, 255, (int) (alpha * 60)).getRGB());
            Render2D.drawRect(context, x + 2, y + 4, 4, height - 8, ColorUtil.applyAlpha(color, (int) (alpha * 180)));
            titleFont.drawString(context, title, x + 14, y + 5, white, false);
            bodyFont.drawString(context, message, x + 14, y + 22, gray, false);
            if (progress.getValue()) {
                float barWidth = (width - 12.0f) * (1.0f - progressValue);
                Render2D.drawRect(context, x + 6.0f, y + height - 3.0f, barWidth, 2.0f, ColorUtil.applyAlpha(color, (int) (alpha * 150)));
            }
            return;
        }

        
        if (currentStyle.equals("Android 12")) {
            int bg = new Color(30, 30, 35, (int) (alpha * 220)).getRGB();
            if (LiquidGlassUtil.isGlass()) {
                LiquidGlassUtil.drawGlass(context, x, y, width, height);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            Render2D.drawRect(context, x, y, width, 2, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            titleFont.drawString(context, title, x + 16, y + 6, white, false);
            bodyFont.drawString(context, message, x + 16, y + 24, gray, false);
            if (progress.getValue()) {
                float barWidth = width * (1.0f - progressValue);
                Render2D.drawRect(context, x + 8, y + height - 3, barWidth - 16, 2, ColorUtil.applyAlpha(color, (int) (alpha * 180)));
            }
            return;
        }

        
        if (currentStyle.equals("macOS")) {
            int bg = new Color(45, 45, 50, (int) (alpha * 230)).getRGB();
            if (LiquidGlassUtil.isGlass()) {
                LiquidGlassUtil.drawGlass(context, x, y, width, height);
            } else {
                Render2D.drawRect(context, x, y, width, height, bg);
            }
            Render2D.drawRect(context, x + 4, y, width - 8, 1, new Color(255, 255, 255, (int) (alpha * 30)).getRGB());
            int dotY = (int) (y + 6);
            Render2D.drawRect(context, x + 8, dotY, 8, 8, new Color(255, 95, 87, (int) (alpha * 200)).getRGB());
            Render2D.drawRect(context, x + 20, dotY, 8, 8, new Color(255, 189, 46, (int) (alpha * 200)).getRGB());
            Render2D.drawRect(context, x + 32, dotY, 8, 8, new Color(39, 201, 63, (int) (alpha * 200)).getRGB());
            titleFont.drawString(context, title, x + 48, y + 5, white, false);
            bodyFont.drawString(context, message, x + 14, y + 28, gray, false);
            return;
        }

        
        if (currentStyle.equals("iOS")) {
            int bg = new Color(28, 28, 30, (int) (alpha * 235)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 4, y + 4, 3, height - 8, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            titleFont.drawString(context, title, x + 14, y + 5, white, false);
            bodyFont.drawString(context, message, x + 14, y + 24, gray, false);
            if (progress.getValue()) {
                float barWidth = (width - 20.0f) * (1.0f - progressValue);
                Render2D.drawRect(context, x + 12, y + height - 4, barWidth, 2, ColorUtil.applyAlpha(color, (int) (alpha * 160)));
            }
            return;
        }

        
        if (currentStyle.equals("Linux GNOME")) {
            int bg = new Color(30, 30, 35, (int) (alpha * 215)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 8, y + 2, width - 16, 2, ColorUtil.applyAlpha(color, (int) (alpha * 180)));
            titleFont.drawString(context, title, x + 14, y + 8, white, false);
            bodyFont.drawString(context, message, x + 14, y + 26, gray, false);
            if (progress.getValue()) {
                float barWidth = (width - 16.0f) * (1.0f - progressValue);
                Render2D.drawRect(context, x + 8, y + height - 2, barWidth, 2, ColorUtil.applyAlpha(color, (int) (alpha * 150)));
            }
            return;
        }

        
        if (currentStyle.equals("Material You")) {
            int bg = new Color(30, 30, 40, (int) (alpha * 225)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 4, y + 4, 4, height - 8, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            titleFont.drawString(context, title, x + 16, y + 5, white, false);
            bodyFont.drawString(context, message, x + 16, y + 24, gray, false);
            Render2D.drawRect(context, x + width - 18, y + height / 2 - 6, 8, 8, ColorUtil.applyAlpha(color, (int) (alpha * 150)));
            return;
        }

        
        if (currentStyle.equals("Fluent Design")) {
            int bg = new Color(20, 20, 25, (int) (alpha * 220)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 4, y, width - 8, 1, new Color(255, 255, 255, (int) (alpha * 50)).getRGB());
            Render2D.drawRect(context, x + 2, y + 2, 6, height - 4, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            titleFont.drawString(context, title, x + 16, y + 4, white, false);
            bodyFont.drawString(context, message, x + 16, y + 22, gray, false);
            if (progress.getValue()) {
                float barWidth = (width - 16.0f) * (1.0f - progressValue);
                Render2D.drawRect(context, x + 8, y + height - 3, barWidth, 2, ColorUtil.applyAlpha(color, (int) (alpha * 160)));
            }
            return;
        }

        
        if (currentStyle.equals("Neumorphism")) {
            int bg = new Color(35, 35, 40, (int) (alpha * 220)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 2, y + 2, width - 4, 1, new Color(255, 255, 255, (int) (alpha * 30)).getRGB());
            Render2D.drawRect(context, x + 2, y + 2, 1, height - 4, new Color(255, 255, 255, (int) (alpha * 30)).getRGB());
            Render2D.drawRect(context, x + width - 3, y + 2, 1, height - 4, new Color(0, 0, 0, (int) (alpha * 50)).getRGB());
            Render2D.drawRect(context, x + 2, y + height - 3, width - 4, 1, new Color(0, 0, 0, (int) (alpha * 50)).getRGB());
            titleFont.drawString(context, title, x + 16, y + 4, white, false);
            bodyFont.drawString(context, message, x + 16, y + 24, gray, false);
            return;
        }

        
        if (currentStyle.equals("Glassmorphism")) {
            int bg = new Color(255, 255, 255, (int) (alpha * 35)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x, y, width, 1, new Color(255, 255, 255, (int) (alpha * 80)).getRGB());
            Render2D.drawRect(context, x, y + height - 1, width, 1, new Color(255, 255, 255, (int) (alpha * 30)).getRGB());
            titleFont.drawString(context, title, x + 16, y + 4, white, false);
            bodyFont.drawString(context, message, x + 16, y + 24, gray, false);
            return;
        }

        
        if (currentStyle.equals("Retro Pixel")) {
            int bg = new Color(20, 20, 30, (int) (alpha * 240)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x, y, width, 2, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            Render2D.drawRect(context, x, y + height - 2, width, 2, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            Render2D.drawRect(context, x, y, 2, height, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            Render2D.drawRect(context, x + width - 2, y, 2, height, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            titleFont.drawString(context, "> " + title, x + 10, y + 4, white, false);
            bodyFont.drawString(context, message, x + 10, y + 22, gray, false);
            return;
        }

        
        if (currentStyle.equals("Hacker Terminal")) {
            int bg = new Color(0, 10, 0, (int) (alpha * 240)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 4, y, 2, height, new Color(0, 255, 0, (int) (alpha * 60)).getRGB());
            titleFont.drawString(context, "[root@server]# " + title, x + 10, y + 4, new Color(0, 255, 0, (int) (alpha * 220)).getRGB(), false);
            bodyFont.drawString(context, "  " + message, x + 10, y + 22, new Color(0, 200, 0, (int) (alpha * 180)).getRGB(), false);
            return;
        }

        
        if (currentStyle.equals("Cyberpunk 2077")) {
            int bg = new Color(10, 5, 20, (int) (alpha * 240)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x, y, width, 2, new Color(255, 215, 0, (int) (alpha * 180)).getRGB());
            Render2D.drawRect(context, x, y + height - 2, width, 2, new Color(255, 0, 200, (int) (alpha * 180)).getRGB());
            Render2D.drawRect(context, x + 2, y + 4, 3, height - 8, new Color(0, 200, 255, (int) (alpha * 150)).getRGB());
            titleFont.drawString(context, "▸ " + title, x + 12, y + 4, new Color(255, 215, 0, (int) (alpha * 220)).getRGB(), false);
            bodyFont.drawString(context, message, x + 12, y + 24, new Color(200, 180, 220, (int) (alpha * 200)).getRGB(), false);
            return;
        }

        
        if (currentStyle.equals("Minimal")) {
            int bg = new Color(25, 25, 30, (int) (alpha * 210)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x + 6, y + 4, 2, height - 8, ColorUtil.applyAlpha(color, (int) (alpha * 180)));
            titleFont.drawString(context, title, x + 14, y + 4, white, false);
            bodyFont.drawString(context, message, x + 14, y + 22, gray, false);
            return;
        }

        
        if (currentStyle.equals("Glow Neon")) {
            int bg = new Color(10, 5, 15, (int) (alpha * 235)).getRGB();
            Render2D.drawRect(context, x, y, width, height, bg);
            Render2D.drawRect(context, x, y, width, 2, ColorUtil.applyAlpha(color, (int) (alpha * 250)));
            Render2D.drawRect(context, x, y + height - 2, width, 2, ColorUtil.applyAlpha(color, (int) (alpha * 250)));
            Render2D.drawRect(context, x, y, 2, height, ColorUtil.applyAlpha(color, (int) (alpha * 250)));
            Render2D.drawRect(context, x + width - 2, y, 2, height, ColorUtil.applyAlpha(color, (int) (alpha * 250)));
            Render2D.drawRect(context, x - 2, y - 2, width + 4, 2, ColorUtil.applyAlpha(color, (int) (alpha * 40)));
            Render2D.drawRect(context, x - 2, y + height, width + 4, 2, ColorUtil.applyAlpha(color, (int) (alpha * 40)));
            titleFont.drawString(context, title, x + 12, y + 4, white, false);
            bodyFont.drawString(context, message, x + 12, y + 24, gray, false);
            if (progress.getValue()) {
                float barWidth = (width - 20.0f) * (1.0f - progressValue);
                Render2D.drawRect(context, x + 10, y + height - 4, barWidth, 2, ColorUtil.applyAlpha(color, (int) (alpha * 200)));
            }
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

        if (!notification.style.is("Off")) {
            NotificationManager.module(module, enabled,
                    notification.formatToggle(module.getName(), enabled, false, false));
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

        String body;
        String logStyleValue = logStyle.getValue();
        if (logStyleValue.equals("Toggled xxx on/off.")) {
            body = "Toggled " + name + " " + (enabled ? "on" : "off") + ".";
        } else if (logStyleValue.equals("xxx Activated/Deactivated.")) {
            body = name + " " + (enabled ? active : inactive) + ".";
        } else if (logStyleValue.equals("xxx → ON/OFF")) {
            body = name + " → " + (enabled ? "ON" : "OFF");
        } else if (logStyleValue.equals("Module xxx is now ON/OFF")) {
            body = "Module " + name + " is now " + (enabled ? "ON" : "OFF");
        } else {
            body = name + " " + (enabled ? on : off);
        }
        return (prefix ? prefix(colored) : "") + body;
    }

    public String prefix(boolean colored) {
        String prefixStyleValue = prefixStyle.getValue();
        if (prefixStyleValue.equals("None")) {
            return "";
        }
        if (prefixStyleValue.equals("Bracket")) {
            return colored ? Formatting.DARK_GRAY + "[" + Formatting.AQUA + "Notification" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Notification] ";
        }
        if (prefixStyleValue.equals("Arrow")) {
            return colored ? Formatting.AQUA + "> " + Formatting.RESET : "> ";
        }
        if (prefixStyleValue.equals("Debug")) {
            return colored ? Formatting.DARK_GRAY + "[" + Formatting.RED + "Debug" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Debug] ";
        }
        if (prefixStyleValue.equals("Myau")) {
            return colored ? Formatting.DARK_GRAY + "[" + Formatting.RED + "M" + Formatting.GOLD + "y" + Formatting.YELLOW + "a" + Formatting.GREEN + "u" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Myau] ";
        }
        if (prefixStyleValue.equals("Sharp")) {
            return colored ? Formatting.DARK_GRAY + "#" + Formatting.AQUA + " " + Formatting.RESET : "# ";
        }
        if (prefixStyleValue.equals("Dot")) {
            return colored ? Formatting.DARK_GRAY + "• " + Formatting.AQUA + " " + Formatting.RESET : "• ";
        }
        if (prefixStyleValue.equals("Star")) {
            return colored ? Formatting.DARK_GRAY + "★ " + Formatting.AQUA + " " + Formatting.RESET : "★ ";
        }
        return colored ? Formatting.DARK_GRAY + "[" + Formatting.AQUA + "Remix" + Formatting.DARK_GRAY + "] " + Formatting.RESET : "[Remix] ";
    }

    private void sendChat(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }

    private void playSound(boolean enabled) {
        String soundValue = sound.getValue();
        SoundEvent event = null;
        if (soundValue.equals("Button")) {
            event = SoundEvents.UI_BUTTON_CLICK.value();
        } else if (soundValue.equals("Experience")) {
            event = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
        } else if (soundValue.equals("Click")) {
            event = SoundEvents.UI_BUTTON_CLICK.value();
        } else if (soundValue.equals("Pling")) {
            event = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        } else if (soundValue.equals("Pop")) {
            event = SoundEvents.BLOCK_BAMBOO_BREAK;
        } else if (soundValue.equals("Chime")) {
            event = SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
        }

        if (event == null) {
            return;
        }

        float pitch = enabled ? 1.15f : 0.85f;
        mc.getSoundManager().play(PositionedSoundInstance.ui(event, pitch));
    }
}
