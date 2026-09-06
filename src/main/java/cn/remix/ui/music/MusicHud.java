package cn.remix.ui.music;

import cn.remix.management.MusicManager;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * 右Alt 弹出的音乐界面（浅色主题，无圆角）。
 * 两个页签：正在播放（封面/控制/进度/音量 + 右侧大号动态双语歌词）、播放列表（独立全高）。
 */
public final class MusicHud implements IMinecraft {

    // 浅色调色板
    private static final int C_ACCENT = 0xFF10B981;
    private static final int C_ACCENT2 = 0xFF0891B2;
    private static final int C_TEXT = 0xFF1A2430;
    private static final int C_TEXT2 = 0xFF445166;
    private static final int C_TEXT3 = 0xFF8A97A8;
    private static final int C_PANEL = 0xF2F7FAFE;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_DIV = 0xFFDDE6EF;
    private static final int C_HOVER = 0xFFEEF3F8;
    private static final int C_CUR = 0xFFE0F5EC;

    private boolean open;
    private float anim;
    private long lastFrameMs;
    private int mode; // 0=正在播放 1=播放列表

    // 布局
    private float hudX, hudY, hudW, hudH, radius;
    private float leftX, leftY, leftW, leftInnerW;
    private float rightX, rightW;
    private float listTop, listH, listBottom;
    private float coverX, coverY, coverSize;
    private float playX, playY, playW, playH;
    private float prevX, nextX;
    private float progX, progY, progW, progH;
    private float volX, volY, volW;
    private float closeX, closeY, closeS;
    private float tabPlayX, tabListX, tabY, tabH;
    private float tabW0, tabW1;
    private int listRowH = 44;

    // 歌词滑动
    private double anchor = -1;
    private long lyricGen = -1;
    private float listScroll;
    private long lastGen;
    private boolean listAutoScroll;
    private double[] lyricTime = new double[0];
    private float[] lyricY = new float[0];
    private float lyricRowH = 30;

    // 环境色晕 / 频谱 / 交互状态
    private final float[] amb = new float[9];
    private boolean ambInit;
    private final float[] spec = new float[22];
    private boolean showTrans = true;
    private boolean dragging;
    private float dragRatio;

    public void setOpen(boolean open) {
        this.open = open;
        if (open) lastFrameMs = System.currentTimeMillis();
    }

    public boolean isOpen() {
        return open;
    }

    public void setShowTranslation(boolean show) {
        this.showTrans = show;
    }

    // ===================== 渲染 =====================

    public void render(DrawContext ctx, MusicManager m, float mx, float my) {
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        anim += ((open ? 1f : 0f) - anim) * Math.min(1f, dt * 14f);
        if (anim < 0.02f) return;
        float a = anim;

        hudW = sw * 0.94f;
        hudH = sh * 0.92f;
        hudX = (sw - hudW) / 2f;
        hudY = (sh - hudH) / 2f;
        radius = 18;

        updateAmbient(m);
        tickSpectrum(m.isPlaying());

        drawBg(ctx, a);
        drawHeader(ctx, m, mx, my);

        double progress = m.getProgress();
        tickLyricAnchor(m, dt, progress);

        if (mode == 1) {
            drawListPage(ctx, m, a, mx, my);
        } else {
            drawPlayerPage(ctx, m, a, mx, my, progress);
        }
    }

    private void drawBg(DrawContext ctx, float a) {
        Render2D.setGlobalAlpha(a);
        square(ctx, hudX - 8, hudY - 8, hudW + 16, hudH + 16, radius + 2, 0x1A000000);
        square(ctx, hudX, hudY, hudW, hudH, radius, C_PANEL);
        // 环境光色晕（浅色背景用低透明度）
        drawAmbientGlow(ctx, hudX, hudY, hudW * 0.72f, hudH * 0.6f, ambCol(0, 22), 0x00000000, true);
        drawAmbientGlow(ctx, hudX + hudW * 0.3f, hudY + hudH * 0.5f, hudW * 0.7f, hudH * 0.5f, 0x00000000, ambCol(2, 18), true);
        Render2D.drawRect(ctx, hudX, hudY, hudW, 3, ambCol(0, 160));
        Render2D.setGlobalAlpha(1f);
    }

    private void drawAmbientGlow(DrawContext ctx, float x, float y, float w, float h, int c1, int c2, boolean horizontal) {
        if (w <= 0 || h <= 0) return;
        Render2D.drawGradient(ctx, x, y, w, h, c1, c2, horizontal);
    }

    private int ambCol(int i, int alpha) {
        int r = Math.round(amb[i * 3]);
        int g = Math.round(amb[i * 3 + 1]);
        int b = Math.round(amb[i * 3 + 2]);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private void updateAmbient(MusicManager m) {
        int[] p = m.getCoverPalette();
        if (!ambInit) {
            for (int i = 0; i < 3; i++) {
                amb[i * 3] = (p[i] >>> 16) & 255;
                amb[i * 3 + 1] = (p[i] >>> 8) & 255;
                amb[i * 3 + 2] = p[i] & 255;
            }
            ambInit = true;
            return;
        }
        for (int i = 0; i < 3; i++) {
            float tr = (p[i] >>> 16) & 255;
            float tg = (p[i] >>> 8) & 255;
            float tb = p[i] & 255;
            amb[i * 3] += (tr - amb[i * 3]) * 0.05f;
            amb[i * 3 + 1] += (tg - amb[i * 3 + 1]) * 0.05f;
            amb[i * 3 + 2] += (tb - amb[i * 3 + 2]) * 0.05f;
        }
    }

    private void tickSpectrum(boolean playing) {
        double t = System.currentTimeMillis();
        for (int i = 0; i < spec.length; i++) {
            if (playing) {
                float target = 0.08f + 0.92f * (float) Math.abs(Math.sin((t / (380.0 + i * 97.0)) + i * 1.3));
                spec[i] += (target - spec[i]) * 0.32f;
            } else {
                spec[i] *= 0.86f;
            }
        }
    }

    private void tickLyricAnchor(MusicManager m, float dt, double progress) {
        if (m.getLyrics().isEmpty()) {
            anchor = -1;
            return;
        }
        if (m.getGeneration() != lyricGen) {
            lyricGen = m.getGeneration();
            anchor = -1;
        }
        int active = activeLine(m, progress);
        if (anchor < 0) anchor = active;
        anchor += (active - anchor) * Math.min(1f, dt * 12f);
    }

    // ===================== 头部 =====================

    private void drawHeader(DrawContext ctx, MusicManager m, float mx, float my) {
        TrueTypeFont f18 = instance.getFontManager().getFont(15);
        // 呼吸圆点 + 标题
        float pulse = 0.35f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        Render2D.drawArc(ctx, hudX + 30, hudY + 27 + 4, 4, 0, 360, ColorUtil.applyAlpha(ambCol(0, 255), (int) (120 * pulse)));
        f18.drawStringWithShadow(ctx, "Remix · 音乐", hudX + 42, hudY + 20, C_TEXT);
        String sub = (m.getServer().equals("netease") ? "网易云" : "QQ音乐") + "   ·   " + (m.getCurrentIndex() >= 0 ? (m.getCurrentIndex() + 1) + "/" + m.getPlaylist().size() : "0/0");
        instance.getFontManager().getFont(11).drawStringWithShadow(ctx, sub, hudX + 42, hudY + 36, C_TEXT3);

        // 页签
        TrueTypeFont tf = instance.getFontManager().getFont(12);
        String[] tabs = {"正在播放", "播放列表"};
        tabY = hudY + 18;
        tabH = 24;
        tabW0 = tf.getStringWidth(tabs[0]) + 28;
        tabW1 = tf.getStringWidth(tabs[1]) + 28;
        float tx = hudX + hudW - 40 - tabW1 - 8 - tabW0;
        for (int i = 0; i < 2; i++) {
            float w = i == 0 ? tabW0 : tabW1;
            float x = i == 0 ? tx : tabPlayX + tabW0 + 8;
            if (i == 0) tabPlayX = x;
            else tabListX = x;
            boolean sel = mode == i;
            boolean hover = inside(mx, my, x, tabY, w, tabH);
            Render2D.drawRect(ctx, x, tabY, w, tabH, sel ? C_HOVER : (hover ? 0x12000000 : 0x00000000));
            Render2D.drawRect(ctx, x, tabY + tabH - 2, w, 2, sel ? C_ACCENT : 0x00000000);
            tf.drawStringWithShadow(ctx, tabs[i], x + (w - tf.getStringWidth(tabs[i])) / 2,
                    tabY + (tabH - tf.getHeight()) / 2, sel ? C_TEXT : C_TEXT3);
            tx = x + w + 8;
        }

        // 关闭
        closeS = 24;
        closeX = hudX + hudW - 30;
        closeY = hudY + 16;
        boolean ch = inside(mx, my, closeX, closeY, closeS, closeS);
        square(ctx, closeX, closeY, closeS, closeS, 4, ch ? C_HOVER : 0x00000000);
        TrueTypeFont cf = instance.getFontManager().getFont(14);
        String x = "✕";
        cf.drawStringWithShadow(ctx, x, closeX + (closeS - cf.getStringWidth(x)) / 2, closeY + 3, C_TEXT2);
    }

    // ===================== 正在播放页 =====================

    private void drawPlayerPage(DrawContext ctx, MusicManager m, float a, float mx, float my, double progress) {
        Render2D.setGlobalAlpha(a);
        float pad = 22;
        float headerH = 54;
        leftW = Math.max(290, Math.min(370, hudW * 0.34f));
        leftX = hudX + pad;
        leftY = hudY + headerH + 6;
        leftInnerW = leftW - pad;
        rightX = hudX + pad + leftW + 24;
        rightW = hudW - pad * 2 - leftW - 24;

        // 封面
        coverSize = Math.min(leftInnerW - 20, hudH * 0.42f);
        coverX = leftX + (leftW - coverSize) / 2;
        coverY = leftY + 6;
        Identifier cover = m.getCoverTexture();
        float glow = 0.4f + 0.12f * (float) Math.sin(System.currentTimeMillis() / 240.0);
        square(ctx, coverX - 10, coverY - 10, coverSize + 20, coverSize + 20, 0, ColorUtil.applyAlpha(ambCol(0, 255), (int) (18 + 50 * glow)));
        if (cover != null) {
            Render2D.drawTexture(ctx, cover, coverX, coverY, coverSize, coverSize);
        } else {
            square(ctx, coverX, coverY, coverSize, coverSize, 0, 0xFFE8EEF5);
            TrueTypeFont cf = instance.getFontManager().getFont(52);
            String n = "♫";
            cf.drawString(ctx, n, coverX + (coverSize - cf.getStringWidth(n)) / 2, coverY + (coverSize - cf.getHeight()) / 2, 0x55B8C4D0);
        }
        Render2D.drawRect(ctx, coverX, coverY, coverSize, coverSize, 0x00000000);
        Render2D.drawRect(ctx, coverX - 1, coverY - 1, coverSize + 2, 1, 0x33C9D6E3);
        Render2D.drawRect(ctx, coverX - 1, coverY + coverSize, coverSize + 2, 1, 0x33C9D6E3);
        Render2D.drawRect(ctx, coverX - 1, coverY - 1, 1, coverSize + 2, 0x33C9D6E3);
        Render2D.drawRect(ctx, coverX + coverSize, coverY - 1, 1, coverSize + 2, 0x33C9D6E3);

        float specH = 20;
        drawSpectrum(ctx, coverX, coverSize, coverY + coverSize + 10, specH);
        float ty = coverY + coverSize + 10 + specH + 8;

        if (m.getCurrentIndex() >= 0 && !m.getPlaylist().isEmpty()) {
            MusicManager.Song song = m.getPlaylist().get(m.getCurrentIndex());
            TrueTypeFont t1 = instance.getFontManager().getFont(17);
            TrueTypeFont t2 = instance.getFontManager().getFont(12);
            String name = trimTo(song.getName(), 24);
            String artist = trimTo(song.getArtist(), 34);
            float nx = leftX + (leftW - t1.getStringWidth(name)) / 2;
            t1.drawStringWithShadow(ctx, name, nx, ty, C_TEXT);
            float ax = leftX + (leftW - t2.getStringWidth(artist)) / 2;
            t2.drawStringWithShadow(ctx, artist, ax, ty + 21, C_TEXT2);

            drawTransport(ctx, m, mx, my, ty + 40);
            float py = ty + 40 + playH + 20;
            drawProgress(ctx, m, mx, my, py, progress);
            drawVolume(ctx, m, py + 26);
        } else {
            TrueTypeFont t1 = instance.getFontManager().getFont(17);
            String na = "未播放";
            t1.drawStringWithShadow(ctx, na, leftX + (leftW - t1.getStringWidth(na)) / 2, ty, C_TEXT3);
        }

        drawLyrics(ctx, m, a, progress);
        Render2D.setGlobalAlpha(1f);
    }

    private void drawSpectrum(DrawContext ctx, float cx, float coverW, float y, float h) {
        float bw = 3f;
        float gap = 2.5f;
        float totalW = Math.min(coverW - 20, spec.length * (bw + gap) - gap);
        float x0 = cx + (coverW - totalW) / 2f;
        int bars = Math.max(2, (int) (totalW / (bw + gap)));
        float mid = y + h / 2f;
        int col = ambCol(0, 255);
        for (int i = 0; i < bars && i < spec.length; i++) {
            float v = spec[i];
            float bh = Math.max(2, v * (h - 5));
            Render2D.drawRect(ctx, x0 + i * (bw + gap), mid - bh / 2f, bw, bh, ColorUtil.applyAlpha(col, (int) (80 + 175 * v)));
        }
    }

    private void drawTransport(DrawContext ctx, MusicManager m, float mx, float my, float ty) {
        TrueTypeFont f = instance.getFontManager().getFont(18);
        TrueTypeFont big = instance.getFontManager().getFont(26);
        playW = 48;
        playH = 42;
        float gap = 18;
        float total = f.getStringWidth("◀") + gap + playW + gap + f.getStringWidth("▶");
        playX = leftX + (leftW - total) / 2 + f.getStringWidth("◀") + gap;
        playY = ty;
        prevX = playX - f.getStringWidth("◀") - gap;
        nextX = playX + playW + gap;

        boolean ph = inside(mx, my, prevX, playY, f.getStringWidth("◀") + 8, playH);
        square(ctx, prevX, playY, f.getStringWidth("◀") + 8, playH, 0, ph ? C_HOVER : C_CARD);
        f.drawStringWithShadow(ctx, "◀", prevX + 4, playY + playH / 2 - f.getHeight() / 2, C_TEXT2);

        ph = inside(mx, my, playX, playY, playW, playH);
        square(ctx, playX, playY, playW, playH, 0, ph ? ColorUtil.applyAlpha(C_ACCENT, 255) : C_CARD);
        String sym = m.isPlaying() ? "❚❚" : "▶";
        big.drawString(ctx, sym, playX + (playW - big.getStringWidth(sym)) / 2, playY + (playH - big.getHeight()) / 2 - 2, ph ? 0xFFEFFEFA : ColorUtil.applyAlpha(C_ACCENT, 255));

        ph = inside(mx, my, nextX, playY, f.getStringWidth("▶") + 8, playH);
        square(ctx, nextX, playY, f.getStringWidth("▶") + 8, playH, 0, ph ? C_HOVER : C_CARD);
        f.drawStringWithShadow(ctx, "▶", nextX + 4, playY + playH / 2 - f.getHeight() / 2, C_TEXT2);
    }

    private void drawProgress(DrawContext ctx, MusicManager m, float mx, float my, float py, double progress) {
        progW = leftInnerW;
        progH = 8;
        progX = leftX;
        progY = py;
        double total = FxMusicRuntime.getDurationSeconds();
        if (total <= 0 && !m.getLyrics().isEmpty()) total = m.getLyrics().get(m.getLyrics().size() - 1).getTime() + 5;
        boolean hover = inside(mx, my, progX, progY - 6, progW, progH + 14);
        float ratio;
        if (dragging) {
            ratio = clamp01((mx - progX) / progW);
        } else {
            ratio = total > 0 ? clamp01((float) (progress / total)) : 0;
        }
        square(ctx, progX, progY, progW, progH, 0, 0xFFD9E2EC);
        if (ratio > 0) {
            Render2D.beginScissor(ctx, progX, progY, progW * ratio, progH);
            square(ctx, progX, progY, progW, progH, 0, C_ACCENT);
            Render2D.endScissor(ctx);
        }
        float knobX = progX + progW * ratio;
        Render2D.drawRect(ctx, knobX - 1, progY - 2, 2, progH + 4, dragging ? ColorUtil.applyAlpha(C_ACCENT, 255) : 0xCCCCCCCC);

        double shown = total > 0 ? ratio * total : progress;
        TrueTypeFont t = instance.getFontManager().getFont(11);
        t.drawStringWithShadow(ctx, fmt(shown), progX, progY + 12, dragging ? C_ACCENT : C_TEXT2);
        t.drawStringWithShadow(ctx, fmt(total), progX + progW - t.getStringWidth(fmt(total)), progY + 12, C_TEXT3);

        if ((dragging || hover) && total > 0) {
            float tx = Math.max(progX + 16, Math.min(progX + progW - 16, knobX));
            String tip = fmt(shown);
            TrueTypeFont tf = instance.getFontManager().getFont(11);
            float tw = tf.getStringWidth(tip);
            square(ctx, tx - tw / 2 - 5, progY - 26, tw + 10, 15, 0, 0xF2FFFFFF);
            tf.drawStringWithShadow(ctx, tip, tx - tw / 2, progY - 23, C_ACCENT);
        }
    }

    private void drawVolume(DrawContext ctx, MusicManager m, float vy) {
        TrueTypeFont t = instance.getFontManager().getFont(11);
        t.drawStringWithShadow(ctx, "音量", leftX, vy, C_TEXT3);
        volW = leftInnerW - 44;
        volX = leftX + 38;
        volY = vy + 3;
        double v = m.getVolume();
        square(ctx, volX, volY, volW, 6, 0, 0xFFD9E2EC);
        if (v > 0) {
            Render2D.beginScissor(ctx, volX, volY, (float) (volW * v), 6);
            square(ctx, volX, volY, volW, 6, 0, C_ACCENT2);
            Render2D.endScissor(ctx);
        }
        Render2D.drawRect(ctx, volX + (float) (volW * v) - 1, volY - 3, 2, 12, 0xFF222F3E);
    }

    // ===================== 右侧歌词 =====================

    private void drawLyrics(DrawContext ctx, MusicManager m, float a, double progress) {
        Render2D.setGlobalAlpha(a);
        float lyrTop = hudY + 54 + 14;
        float lyrH = hudH - 54 - 14 - 26;
        float lyrCy = hudY + 54 + lyrH * 0.46f; // 位置偏高一点
        float maxW = rightW - 20;

        if (m.getLyrics().isEmpty()) {
            TrueTypeFont f = instance.getFontManager().getFont(15);
            String hint = "暂无歌词";
            f.drawStringWithShadow(ctx, hint, rightX + (rightW - f.getStringWidth(hint)) / 2, lyrCy - 8, C_TEXT3);
            Render2D.setGlobalAlpha(1f);
            return;
        }

        int active = activeLine(m, progress);
        int baseSize = (int) Math.max(24, Math.min(36, rightW / 15));
        TrueTypeFont baseFont = instance.getFontManager().getFont(baseSize);
        lyricRowH = baseFont.getHeight() + (showTrans ? 14 : 6);
        // 限制最多显示行数
        int half = Math.max(2, Math.min(6, (int) (lyrH / lyricRowH / 2)));
        int from = Math.max(0, (int) Math.floor(anchor) - half - 1);
        int to = Math.min(m.getLyrics().size() - 1, (int) Math.ceil(anchor) + half + 1);
        int n = to - from + 1;
        lyricTime = new double[n];
        lyricY = new float[n];
        for (int i = 0; i < n; i++) lyricY[i] = Float.NaN;

        for (int i = from; i <= to; i++) {
            boolean cur = i == active;
            int idx = i - from;
            lyricTime[idx] = m.getLyrics().get(i).getTime();
            float y = lyrCy + (float) (i - anchor) * lyricRowH;
            if (y < hudY + 44 - 40 || y > hudY + hudH - 6 + 40) continue;
            lyricY[idx] = y;

            String text = m.getLyrics().get(i).getText();
            if (text == null || text.isEmpty()) continue;
            int dist = Math.abs(i - active);
            int size = cur ? baseSize : Math.max(14, baseSize - 7);
            TrueTypeFont f = instance.getFontManager().getFont(size);
            float sw0 = f.getStringWidth(text);
            if (sw0 > maxW) {
                int ns = Math.max(12, (int) (size * maxW / sw0));
                f = instance.getFontManager().getFont(ns);
                sw0 = f.getStringWidth(text);
            }
            float x = rightX + (rightW - sw0) / 2;
            float alpha = cur ? 1f : Math.max(0.20f, 0.95f - dist * 0.20f);

            if (cur) {
                square(ctx, x - 12, y - 5, sw0 + 24, f.getHeight() + 14, 0, ColorUtil.applyAlpha(C_CUR, 210));
                // 伪粗体：双层错位描边
                f.drawString(ctx, text, x - 0.6f, y - 0.6f, ColorUtil.applyAlpha(0xFF86C9A8, (int) (255 * alpha)));
                f.drawStringWithShadow(ctx, text, x, y, ColorUtil.applyAlpha(C_TEXT, (int) (255 * alpha)));
            } else {
                f.drawStringWithShadow(ctx, text, x, y, ColorUtil.applyAlpha(0xFFA9B4C2, (int) (255 * alpha)));
            }

            if (cur && showTrans) {
                String tr = translationOf(m, m.getLyrics().get(i).getTime());
                if (tr != null && !tr.isEmpty()) {
                    TrueTypeFont tf = instance.getFontManager().getFont(Math.max(14, baseSize - 9));
                    float tw = tf.getStringWidth(tr);
                    if (tw > maxW) {
                        int ns2 = Math.max(12, (int) (tf.getHeight() * maxW / tw));
                        tf = instance.getFontManager().getFont(ns2);
                        tw = tf.getStringWidth(tr);
                    }
                    tf.drawStringWithShadow(ctx, tr, rightX + (rightW - tw) / 2, y + f.getHeight() + 5,
                            ColorUtil.applyAlpha(C_ACCENT, (int) (255 * Math.min(1f, alpha + 0.15f))));
                }
            }
        }
        Render2D.setGlobalAlpha(1f);
    }

    private String translationOf(MusicManager m, double t) {
        double bestKey = Double.NEGATIVE_INFINITY;
        String best = null;
        for (Map.Entry<Double, String> e : m.getTranslation().entrySet()) {
            if (e.getKey() <= t + 0.3 && e.getKey() > bestKey) {
                bestKey = e.getKey();
                best = e.getValue();
            }
        }
        return best;
    }

    private int activeLine(MusicManager m, double progress) {
        int idx = 0;
        for (int i = 0; i < m.getLyrics().size(); i++) {
            if (m.getLyrics().get(i).getTime() <= progress) idx = i;
            else break;
        }
        return idx;
    }

    // ===================== 播放列表页 =====================

    private void drawListPage(DrawContext ctx, MusicManager m, float a, float mx, float my) {
        Render2D.setGlobalAlpha(a);
        float pad = 22;
        float headerH = 54;
        listTop = hudY + headerH + 10;
        listBottom = hudY + hudH - 16;
        listH = listBottom - listTop;
        float lw = hudW - pad * 2;
        float lx = hudX + pad;

        if (m.getPlaylist().isEmpty()) {
            TrueTypeFont f = instance.getFontManager().getFont(15);
            String e = "歌单为空或加载失败";
            f.drawStringWithShadow(ctx, e, lx + (lw - f.getStringWidth(e)) / 2, listTop + 30, C_TEXT3);
            Render2D.setGlobalAlpha(1f);
            return;
        }

        if (m.getCurrentIndex() != lastGen) {
            lastGen = m.getCurrentIndex();
            listAutoScroll = true;
        }
        if (listAutoScroll && m.getCurrentIndex() >= 0) {
            float target = m.getCurrentIndex() * listRowH - (listH - listRowH) / 2f;
            listScroll += (target - listScroll) * 0.25f;
            if (Math.abs(target - listScroll) < 0.6f) listAutoScroll = false;
        }
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, m.getPlaylist().size() * listRowH - listH)));

        Render2D.beginScissor(ctx, lx, listTop, lw, listH);
        int first = (int) Math.floor(listScroll / listRowH);
        for (int i = Math.max(0, first); i < m.getPlaylist().size(); i++) {
            float ry = listTop - listScroll + i * listRowH;
            if (ry > listBottom) break;
            boolean cur = i == m.getCurrentIndex();
            boolean hover = inside(mx, my, lx, ry, lw, listRowH - 2);
            if (hover && !cur) Render2D.drawRect(ctx, lx + 2, ry, lw - 4, listRowH - 2, C_HOVER);
            if (cur) Render2D.drawRect(ctx, lx + 2, ry, lw - 4, listRowH - 2, C_CUR);
            Render2D.drawRect(ctx, lx + 2, ry + listRowH - 2, lw - 4, 1, 0x14C9D6E3);

            MusicManager.Song s = m.getPlaylist().get(i);
            float noX = lx + 16;
            if (cur && m.isPlaying()) {
                drawEq(ctx, noX + 16, ry + listRowH / 2 - 6);
            } else {
                String no = String.valueOf(i + 1);
                TrueTypeFont nf = instance.getFontManager().getFont(cur ? 14 : 12);
                nf.drawString(ctx, no, noX, ry + (listRowH - 2 - nf.getHeight()) / 2 + 1, cur ? C_ACCENT : C_TEXT3);
            }
            float tx = noX + 46;
            TrueTypeFont t1 = instance.getFontManager().getFont(cur ? 15 : 13);
            TrueTypeFont t2 = instance.getFontManager().getFont(11);
            String title = trimTo(s.getName(), (int) ((lw - 70) / 9));
            String artist = trimTo(s.getArtist(), (int) ((lw - 70) / 9));
            t1.drawStringWithShadow(ctx, title, tx, ry + (listRowH - 2) / 2 - t1.getHeight() - 1, cur ? C_TEXT : C_TEXT2);
            t2.drawStringWithShadow(ctx, artist, tx, ry + (listRowH - 2) / 2 + 2, C_TEXT3);
        }
        Render2D.endScissor(ctx);
        Render2D.setGlobalAlpha(1f);
    }

    private void drawEq(DrawContext ctx, float x, float y) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 3; i++) {
            float h = (float) (3 + 8 * (0.5 + 0.5 * Math.sin(t * 6 + i * 1.7)));
            Render2D.drawRect(ctx, x + i * 4, y + (10 - h) / 2, 2.5f, h, ColorUtil.applyAlpha(C_ACCENT, 220));
        }
    }

    // ===================== 交互 =====================

    public boolean onMouse(float mx, float my, int action, MusicManager m) {
        if (action == 0) {
            if (dragging) {
                dragging = false;
                seekRatio(m, mx);
            }
            return open;
        }
        if (action != 1) return open;
        if (!inside(mx, my, hudX, hudY, hudW, hudH)) {
            setOpen(false);
            return true;
        }
        if (inside(mx, my, closeX, closeY, closeS, closeS)) {
            setOpen(false);
            return true;
        }
        if (inside(mx, my, tabPlayX, tabY, tabW0, tabH)) {
            mode = 0;
            return true;
        }
        if (inside(mx, my, tabListX, tabY, tabW1, tabH)) {
            mode = 1;
            return true;
        }
        if (mode == 0) {
            if (inside(mx, my, prevX, playY, 40, playH)) {
                m.previous();
                return true;
            }
            if (inside(mx, my, playX, playY, playW, playH)) {
                m.togglePlay();
                return true;
            }
            if (inside(mx, my, nextX, playY, 40, playH)) {
                m.next();
                return true;
            }
            if (inside(mx, my, progX, progY - 4, progW, progH + 10)) {
                dragging = true;
                seekRatio(m, mx);
                return true;
            }
            if (inside(mx, my, volX - 6, volY - 6, volW + 12, 18)) {
                m.setVolume(clamp01((mx - volX) / volW));
                return true;
            }
            for (int i = 0; i < lyricY.length; i++) {
                if (Float.isNaN(lyricY[i])) continue;
                if (mx > rightX && mx < rightX + rightW && Math.abs(my - lyricY[i]) < lyricRowH * 0.55f) {
                    m.seek(lyricTime[i]);
                    return true;
                }
            }
        } else {
            if (inside(mx, my, hudX + 22, listTop, hudW - 44, listH)) {
                int idx = (int) ((my - listTop + listScroll) / listRowH);
                if (idx >= 0 && idx < m.getPlaylist().size()) {
                    m.playIndex(idx);
                    return true;
                }
                return true;
            }
        }
        return true;
    }

    private void seekRatio(MusicManager m, float mx) {
        double total = FxMusicRuntime.getDurationSeconds();
        if (total <= 0 && !m.getLyrics().isEmpty()) total = m.getLyrics().get(m.getLyrics().size() - 1).getTime() + 5;
        if (total > 0) m.seek(clamp01((mx - progX) / progW) * total);
    }

    public boolean onScroll(float mx, float my, double amount) {
        if (!open) return false;
        if (mode == 1) {
            if (inside(mx, my, hudX + 22, listTop, hudW - 44, listH)) {
                listScroll -= (float) amount * 22;
                listAutoScroll = false;
                return true;
            }
            return false;
        }
        return false;
    }

    // ===================== 工具 =====================

    private static void square(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        Render2D.drawRect(ctx, x, y, w, h, color);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static String trimTo(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String fmt(double seconds) {
        if (seconds < 0) seconds = 0;
        int s = (int) seconds;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
