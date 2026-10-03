package cn.remix.ui.music;

import cn.remix.management.MusicManager;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.List;
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
    /** 顶栏高度：左列、歌词区的起始位置都以它为准 */
    private static final float HEADER_H = 54f;

    private boolean open;
    private float anim;
    private float frameDt = 0.016f;
    private long lastFrameMs;
    private int mode; // 0=正在播放 1=播放列表

    /** 悬停/动效用：把瞬变改成渐变，避免鼠标划过时颜色硬跳 */
    private final Map<String, Float> hoverAnim = new java.util.HashMap<>();

    // 布局
    private float hudX, hudY, hudW, hudH, radius;
    private float leftX, leftW, leftInnerW;
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

    // 列表：搜索 / 排序 / 惯性滚动 / 滚动条
    private int[] view = new int[0];
    private String query = "";
    private boolean searchFocused;
    /** 搜索框是否正在接收键盘（静态，供 MixinKeyboard 判断要不要把按键漏给游戏） */
    private static volatile boolean typing;
    /** 界面是否开着（静态副本），isTyping() 用它兜底，避免标志卡住导致按键被永久吞掉 */
    private static volatile boolean openFlag;
    private int sortMode;                 // 0 默认 1 歌名 2 歌手 3 时长
    private int viewVersion = -1;
    private long playlistSig = -1;
    private float scrollVel;
    private boolean scrollbarDragging;
    private float barX, barW;
    private float searchX, searchY, searchW, searchH;
    private float sortX, sortY, chipW, chipH;

    // 环境色晕 / 频谱 / 交互状态
    private final float[] amb = new float[9];
    private boolean ambInit;
    private final float[] spec = new float[22];
    private boolean showTrans = true;
    private boolean dragging;
    private boolean volDragging;
    private boolean tipPending;
    private float tipMx, tipMy;
    private double tipAt;
    private float dragRatio;

    public void setOpen(boolean open) {
        this.open = open;
        openFlag = open;
        if (open) {
            lastFrameMs = System.currentTimeMillis();
        } else {
            // 关界面时别把拖动/输入状态留着，否则下次打开会跟着鼠标乱跑
            dragging = false;
            volDragging = false;
            scrollbarDragging = false;
            setSearchFocused(false);
        }
    }

    /** 聚焦状态同时同步到静态标志，游戏侧靠它屏蔽移动/背包等按键。 */
    private void setSearchFocused(boolean focused) {
        this.searchFocused = focused;
        typing = focused;
    }

    public static boolean isTyping() {
        return typing && openFlag;
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
        frameDt = dt;
        // 开/关用指数趋近 + smootherstep，收尾比线性插值干净
        float target = open ? 1f : 0f;
        anim += (target - anim) * Math.min(1f, dt * (open ? 13f : 18f));
        if (anim < 0.02f && !open) return;
        float a = anim * anim * anim * (anim * (anim * 6f - 15f) + 10f);   // smootherstep

        hudW = sw * 0.94f;
        hudH = sh * 0.92f;
        hudX = (sw - hudW) / 2f;
        hudY = (sh - hudH) / 2f + (1f - a) * 14f;   // 打开时由下往上滑一点点
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
        if (anchor < 0) {
            anchor = active;
            return;
        }
        // 指数趋近 + 限速：换行顺滑，跳转/拖动进度条时也不会整屏甩过去
        double diff = active - anchor;
        double step = Math.min(Math.abs(diff) * 10.0, 16.0) * dt;
        if (Math.abs(diff) <= step) anchor = active;
        else anchor += Math.signum(diff) * step;
    }

    /** 悬停/动效数值：on 为真时逐帧趋近 1，否则趋近 0。 */
    private float hoverAnim(String key, boolean on) {
        float v = hoverAnim.getOrDefault(key, 0f);
        v += ((on ? 1f : 0f) - v) * Math.min(1f, frameDt * 20f);
        if (v < 0.002f) v = 0f;
        hoverAnim.put(key, v);
        return v;
    }

    /** 两个 ARGB 颜色按 t 混合。 */
    private static int mix(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int a1 = (c1 >>> 24) & 255, r1 = (c1 >>> 16) & 255, g1 = (c1 >>> 8) & 255, b1 = c1 & 255;
        int a2 = (c2 >>> 24) & 255, r2 = (c2 >>> 16) & 255, g2 = (c2 >>> 8) & 255, b2 = c2 & 255;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
            float hv = hoverAnim("tab" + i, hover && !sel);
            float selv = hoverAnim("tabsel" + i, sel);
            Render2D.drawRect(ctx, x, tabY, w, tabH,
                    mix(mix(0x00000000, 0x14000000, hv), C_HOVER, selv));
            // 选中下划线做宽度动画，切页签时不会硬闪
            if (selv > 0.01f) {
                float uw = w * selv;
                Render2D.drawRect(ctx, x + (w - uw) / 2f, tabY + tabH - 2, uw, 2,
                        ColorUtil.applyAlpha(C_ACCENT, (int) (255 * selv)));
            }
            tf.drawStringWithShadow(ctx, tabs[i], x + (w - tf.getStringWidth(tabs[i])) / 2,
                    tabY + (tabH - tf.getHeight()) / 2, mix(mix(C_TEXT3, C_TEXT2, hv), C_TEXT, selv));
            tx = x + w + 8;
        }

        // 关闭
        closeS = 24;
        closeX = hudX + hudW - 30;
        closeY = hudY + 16;
        float chv = hoverAnim("close", inside(mx, my, closeX, closeY, closeS, closeS));
        square(ctx, closeX, closeY, closeS, closeS, 4, mix(0x00000000, C_HOVER, chv));
        TrueTypeFont cf = instance.getFontManager().getFont(14);
        String x = "✕";
        cf.drawStringWithShadow(ctx, x, closeX + (closeS - cf.getStringWidth(x)) / 2, closeY + 3,
                mix(C_TEXT3, C_TEXT, chv));
    }

    // ===================== 正在播放页 =====================

    private void drawPlayerPage(DrawContext ctx, MusicManager m, float a, float mx, float my, double progress) {
        Render2D.setGlobalAlpha(a);
        float pad = 24;
        // 左列：外宽用于整体居中，leftX/leftInnerW 是真正的内容列，所有控件都对齐到它
        leftW = Math.max(300, Math.min(380, hudW * 0.32f));
        leftInnerW = leftW - 20;
        leftX = hudX + pad + (leftW - leftInnerW) / 2f;
        rightX = hudX + pad + leftW + 30;
        rightW = hudX + hudW - pad - rightX;

        TrueTypeFont nameFont = instance.getFontManager().getFont(17);
        TrueTypeFont artistFont = instance.getFontManager().getFont(12);
        float specH = 20;
        playH = 42;
        coverSize = Math.min(leftInnerW, Math.min(hudH * 0.26f, 280f));

        // 整块内容在标题栏以下的区域里垂直居中，不再全部堆在上半部分
        float blockH = coverSize + 12 + specH + 20 + nameFont.getHeight() + 4 + artistFont.getHeight()
                + 20 + playH + 24 + 34 + 24;
        float top = hudY + HEADER_H + Math.max(10f, (hudH - HEADER_H - blockH) / 2f);

        // 封面
        coverX = leftX + (leftInnerW - coverSize) / 2f;
        coverY = top;
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
        Render2D.drawRect(ctx, coverX - 1, coverY - 1, coverSize + 2, 1, 0x33C9D6E3);
        Render2D.drawRect(ctx, coverX - 1, coverY + coverSize, coverSize + 2, 1, 0x33C9D6E3);
        Render2D.drawRect(ctx, coverX - 1, coverY - 1, 1, coverSize + 2, 0x33C9D6E3);
        Render2D.drawRect(ctx, coverX + coverSize, coverY - 1, 1, coverSize + 2, 0x33C9D6E3);

        float specY = coverY + coverSize + 12;
        drawSpectrum(ctx, coverX, coverSize, specY, specH);
        float ty = specY + specH + 20;

        if (m.getCurrentIndex() >= 0 && !m.getPlaylist().isEmpty()) {
            MusicManager.Song song = m.getPlaylist().get(m.getCurrentIndex());
            String name = trimTo(song.getName(), 26);
            String artist = trimTo(song.getArtist(), 36);
            nameFont.drawStringWithShadow(ctx, name, leftX + (leftInnerW - nameFont.getStringWidth(name)) / 2, ty, C_TEXT);
            float ay = ty + nameFont.getHeight() + 4;
            artistFont.drawStringWithShadow(ctx, artist, leftX + (leftInnerW - artistFont.getStringWidth(artist)) / 2, ay, C_TEXT2);

            float transportY = ay + artistFont.getHeight() + 20;
            drawTransport(ctx, m, mx, my, transportY);
            float py = transportY + playH + 24;
            drawProgress(ctx, m, mx, my, py, progress);
            drawVolume(ctx, m, py + 26, mx, my);
        } else {
            TrueTypeFont t1 = instance.getFontManager().getFont(17);
            String na = "未播放";
            t1.drawStringWithShadow(ctx, na, leftX + (leftInnerW - t1.getStringWidth(na)) / 2, ty, C_TEXT3);
        }

        drawLyrics(ctx, m, a, progress);
        // 进度条浮层压到最后画，免得被右侧歌词盖住
        if (tipPending) {
            tipPending = false;
            drawSeekTooltip(ctx, m, tipMx, tipMy, tipAt);
        }
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
        float bw = f.getStringWidth("◀") + 14;
        float total = bw + gap + playW + gap + bw;
        playY = ty;
        playX = leftX + (leftInnerW - total) / 2f + bw + gap;
        prevX = playX - bw - gap;
        nextX = playX + playW + gap;

        float pv = hoverAnim("prev", inside(mx, my, prevX, playY, bw, playH));
        square(ctx, prevX, playY, bw, playH, 0, mix(0x00000000, C_HOVER, pv));
        f.drawStringWithShadow(ctx, "◀", prevX + (bw - f.getStringWidth("◀")) / 2f,
                playY + playH / 2 - f.getHeight() / 2, mix(C_TEXT3, C_TEXT2, pv));

        float cv = hoverAnim("play", inside(mx, my, playX, playY, playW, playH));
        square(ctx, playX, playY, playW, playH, 0, mix(C_CARD, C_ACCENT, cv));
        String sym = m.isPlaying() ? "❚❚" : "▶";
        big.drawString(ctx, sym, playX + (playW - big.getStringWidth(sym)) / 2,
                playY + (playH - big.getHeight()) / 2 - 2, mix(C_ACCENT, 0xFFEFFEFA, cv));

        float nv = hoverAnim("next", inside(mx, my, nextX, playY, bw, playH));
        square(ctx, nextX, playY, bw, playH, 0, mix(0x00000000, C_HOVER, nv));
        f.drawStringWithShadow(ctx, "▶", nextX + (bw - f.getStringWidth("▶")) / 2f,
                playY + playH / 2 - f.getHeight() / 2, mix(C_TEXT3, C_TEXT2, nv));
    }

    private void drawProgress(DrawContext ctx, MusicManager m, float mx, float my, float py, double progress) {
        progW = leftInnerW;
        progH = 8;
        progX = leftX;
        progY = py;
        double total = FxMusicRuntime.getDurationSeconds();
        if (total <= 0 && !m.getLyrics().isEmpty()) total = m.getLyrics().get(m.getLyrics().size() - 1).getTime() + 5;
        boolean hover = inside(mx, my, progX, progY - 6, progW, progH + 14);
        float hv = hoverAnim("prog", dragging || hover);
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
        float knobW = 2 + 2 * hv;
        Render2D.drawRect(ctx, knobX - knobW / 2f, progY - 2 - hv, knobW, progH + 4 + hv * 2,
                mix(0xCCCCCCCC, C_ACCENT, hv));

        double shown = total > 0 ? ratio * total : progress;
        TrueTypeFont t = instance.getFontManager().getFont(11);
        t.drawStringWithShadow(ctx, fmt(shown), progX, progY + 12, dragging ? C_ACCENT : C_TEXT2);
        t.drawStringWithShadow(ctx, fmt(total), progX + progW - t.getStringWidth(fmt(total)), progY + 12, C_TEXT3);

        if ((dragging || hover) && total > 0) {
            // 鼠标位置对应的播放时间，用来找那一刻的歌词；真正绘制压到帧末
            tipPending = true;
            tipMx = mx;
            tipMy = my;
            tipAt = clamp01((mx - progX) / progW) * total;
        }
    }

    /**
     * 悬停/拖动进度条时，在鼠标上方浮出那一刻的歌词（有翻译就一起显示）。
     * 上方空间不够时自动翻到鼠标下方，横向也会被夹在界面内。
     */
    private void drawSeekTooltip(DrawContext ctx, MusicManager m, float mx, float my, double at) {
        String lyric = null;
        String trans = null;
        int idx = lyricIndexAt(m, at);
        if (idx >= 0) {
            lyric = m.getLyrics().get(idx).getText();
            if (showTrans) trans = m.translationAt(m.getLyrics().get(idx).getTime());
        }
        if (lyric != null && lyric.isEmpty()) lyric = null;
        if (trans != null && trans.isEmpty()) trans = null;

        TrueTypeFont small = instance.getFontManager().getFont(11);
        TrueTypeFont text = instance.getFontManager().getFont(14);
        float maxTextW = Math.min(hudW - 60, 340);

        String timeStr = fmt(at);
        if (lyric != null) lyric = clip(text, lyric, maxTextW);
        if (trans != null) trans = clip(small, trans, maxTextW);

        float padX = 9.0f, padY = 6.0f, gap = 3.0f;
        float w = small.getStringWidth(timeStr);
        float h = small.getHeight();
        if (lyric != null) {
            w = Math.max(w, text.getStringWidth(lyric));
            h += gap + text.getHeight();
        }
        if (trans != null) {
            w = Math.max(w, small.getStringWidth(trans));
            h += gap + small.getHeight();
        }
        w += padX * 2;
        h += padY * 2;

        float bx = Math.max(hudX + 8, Math.min(hudX + hudW - w - 8, mx - w / 2f));
        float by = my - h - 12;
        if (by < hudY + 8) by = my + 16;   // 上方放不下就翻到下面

        square(ctx, bx, by, w, h, 0, 0xF2FFFFFF);
        Render2D.drawRect(ctx, bx, by, w, 1, 0xFFDDE6EF);
        Render2D.drawRect(ctx, bx, by + h - 1, w, 1, 0xFFDDE6EF);
        Render2D.drawRect(ctx, bx, by, 2, h, C_ACCENT2);

        float ty = by + padY;
        small.drawStringWithShadow(ctx, timeStr, bx + padX, ty, C_ACCENT2);
        ty += small.getHeight() + gap;
        if (lyric != null) {
            text.drawStringWithShadow(ctx, lyric, bx + padX, ty, C_TEXT);
            ty += text.getHeight() + gap;
        }
        if (trans != null) {
            small.drawStringWithShadow(ctx, trans, bx + padX, ty, 0xFF5BC2B8);
        }
    }

    /** 找到 time 时刻正在唱的那一行（没有则 -1）。 */
    private static int lyricIndexAt(MusicManager m, double time) {
        List<MusicManager.LrcLine> lines = m.getLyrics();
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getTime() <= time) idx = i;
            else break;
        }
        return idx;
    }

    /** 按像素宽度裁字，保证浮层不会撑出界面。 */
    private static String clip(TrueTypeFont font, String s, float maxW) {
        if (font.getStringWidth(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (font.getStringWidth(sb.toString() + c + "...") > maxW) break;
            sb.append(c);
        }
        return sb + "...";
    }

    private void drawVolume(DrawContext ctx, MusicManager m, float vy, float mx, float my) {
        TrueTypeFont t = instance.getFontManager().getFont(11);
        t.drawStringWithShadow(ctx, "音量", leftX, vy, C_TEXT3);
        volW = leftInnerW - 60;
        volX = leftX + 38;
        volY = vy + 3;

        float active = hoverAnim("vol", volDragging || inside(mx, my, volX - 8, volY - 8, volW + 16, 22));
        // 拖动中：每帧都用鼠标横坐标更新音量，这就是"滑条"
        if (volDragging) m.setVolume(clamp01((mx - volX) / volW));

        double v = m.getVolume();
        float trackH = 6 + 2 * active;
        float trackY = volY + (6 - trackH) / 2f;
        square(ctx, volX, trackY, volW, trackH, 0, 0xFFD9E2EC);
        if (v > 0) {
            Render2D.beginScissor(ctx, volX, trackY, (float) (volW * v), trackH);
            square(ctx, volX, trackY, volW, trackH, 0, C_ACCENT2);
            Render2D.endScissor(ctx);
        }
        float knobX = volX + (float) (volW * v);
        float knobW = 2 + 2 * active;
        Render2D.drawRect(ctx, knobX - knobW / 2f, trackY - 3, knobW, trackH + 6,
                mix(0xFF222F3E, C_ACCENT2, active));

        TrueTypeFont pf = instance.getFontManager().getFont(10);
        String pct = Math.round(v * 100) + "%";
        pf.drawStringWithShadow(ctx, pct, volX + volW + 6, vy + 1, mix(C_TEXT3, C_ACCENT2, active));
    }

    // ===================== 右侧歌词 =====================

    private void drawLyrics(DrawContext ctx, MusicManager m, float a, double progress) {
        Render2D.setGlobalAlpha(a);
        float lyrTop = hudY + HEADER_H + 16;
        float lyrBottom = hudY + hudH - 24;
        float lyrH = Math.max(80f, lyrBottom - lyrTop);
        float lyrCy = lyrTop + lyrH * 0.44f; // 当前行略高于正中
        float maxW = rightW - 24;

        if (m.getLyrics().isEmpty()) {
            TrueTypeFont f = instance.getFontManager().getFont(15);
            String hint = "暂无歌词";
            f.drawStringWithShadow(ctx, hint, rightX + (rightW - f.getStringWidth(hint)) / 2, lyrCy - 8, C_TEXT3);
            Render2D.setGlobalAlpha(1f);
            return;
        }

        int active = activeLine(m, progress);
        int baseSize = (int) Math.max(22, Math.min(34, rightW / 16));
        TrueTypeFont baseFont = font(baseSize);
        int transSize = Math.max(13, baseSize - 11);
        TrueTypeFont transFont = font(transSize);
        float lineH = baseFont.getHeight();
        float transH = transFont.getHeight();
        // 行距必须把译文的实际高度算进去，否则双语时译文会压到下一行上
        lyricRowH = lineH + (showTrans ? transH + 12f : 10f);

        int half = Math.max(2, Math.min(7, (int) (lyrH / lyricRowH / 2)));
        int from = Math.max(0, (int) Math.floor(anchor) - half - 1);
        int to = Math.min(m.getLyrics().size() - 1, (int) Math.ceil(anchor) + half + 1);
        int n = to - from + 1;
        lyricTime = new double[n];
        lyricY = new float[n];
        for (int i = 0; i < n; i++) lyricY[i] = Float.NaN;

        for (int i = from; i <= to; i++) {
            boolean cur = i == active;
            int idx = i - from;
            double time = m.getLyrics().get(i).getTime();
            lyricTime[idx] = time;
            float y = lyrCy + (float) (i - anchor) * lyricRowH;
            if (y + lyricRowH < lyrTop - 24 || y > lyrBottom + 24) continue;
            lyricY[idx] = y;

            String text = m.getLyrics().get(i).getText();
            if (text == null || text.isEmpty()) continue;
            String tr = cur && showTrans ? translationOf(m, time) : null;
            if (tr != null && tr.isEmpty()) tr = null;

            int dist = Math.abs(i - active);
            float alpha = cur ? 1f : Math.max(0.20f, 0.95f - dist * 0.20f);
            int size = cur ? baseSize : Math.max(14, baseSize - 7);
            TrueTypeFont f = font(size);
            float sw0 = f.getStringWidth(text);
            if (sw0 > maxW) {
                f = font(Math.max(12, (int) (size * maxW / sw0)));
                sw0 = f.getStringWidth(text);
            }
            float x = rightX + (rightW - sw0) / 2f;

            if (cur) {
                // 高亮块把原文和译文一起包住，不再只框住原文
                float boxH = lineH + (tr != null ? transH + 6f : 0f);
                square(ctx, x - 14, y - 6, sw0 + 28, boxH + 12, 0, ColorUtil.applyAlpha(C_CUR, 210));
                // 伪粗体：双层错位描边
                f.drawString(ctx, text, x - 0.6f, y - 0.6f, ColorUtil.applyAlpha(0xFF86C9A8, (int) (255 * alpha)));
                f.drawStringWithShadow(ctx, text, x, y, ColorUtil.applyAlpha(C_TEXT, (int) (255 * alpha)));
                if (tr != null) {
                    TrueTypeFont tf = transFont;
                    float tw = tf.getStringWidth(tr);
                    if (tw > maxW) {
                        tf = font(Math.max(12, (int) (transSize * maxW / tw)));
                        tw = tf.getStringWidth(tr);
                    }
                    tf.drawStringWithShadow(ctx, tr, rightX + (rightW - tw) / 2f, y + lineH + 4,
                            ColorUtil.applyAlpha(C_ACCENT, 245));
                }
            } else {
                f.drawStringWithShadow(ctx, text, x, y, ColorUtil.applyAlpha(0xFFA9B4C2, (int) (255 * alpha)));
            }
        }
        Render2D.setGlobalAlpha(1f);
    }

    private TrueTypeFont font(int size) {
        return instance.getFontManager().getFont(size);
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
        float barH = 42;
        float barSpace = 12;   // 右边留给滚动条
        listTop = hudY + HEADER_H + barH + 8;
        listBottom = hudY + hudH - 18;
        listH = listBottom - listTop;
        listRowH = 52;
        float lw = hudW - pad * 2 - barSpace;
        float lx = hudX + pad;

        if (m.getPlaylist().isEmpty()) {
            TrueTypeFont f = font(15);
            TrueTypeFont f2 = font(11);
            String e = m.isThrottled()
                    ? "接口限流中 · 约 " + m.getThrottleSeconds() + " 秒后自动重试"
                    : "歌单为空或加载失败";
            f.drawStringWithShadow(ctx, e, lx + (lw - f.getStringWidth(e)) / 2, listTop + 26, C_TEXT3);
            if (m.isThrottled()) {
                String hint = m.getThrottleMessage();
                f2.drawStringWithShadow(ctx, hint, lx + (lw - f2.getStringWidth(hint)) / 2, listTop + 48, 0xFFB0BAC6);
            }
            Render2D.setGlobalAlpha(1f);
            return;
        }

        drawListToolbar(ctx, m, mx, my, lx, lw);
        rebuildViewIfNeeded(m);

        int total = view.length;
        float contentH = total * listRowH;
        float maxScroll = Math.max(0, contentH - listH);

        // 自动滚到当前歌曲（按排序/过滤后的位置）
        int curView = viewIndexOf(m.getCurrentIndex());
        if (m.getCurrentIndex() != lastGen) {
            lastGen = m.getCurrentIndex();
            listAutoScroll = true;
        }
        if (listAutoScroll && curView >= 0) {
            float target = curView * listRowH - (listH - listRowH) / 2f;
            listScroll += (target - listScroll) * Math.min(1f, frameDt * 12f);
            if (Math.abs(target - listScroll) < 0.6f) {
                listScroll = target;
                listAutoScroll = false;
            }
            scrollVel = 0;
        }

        // 惯性滚动：滚轮给速度，之后指数衰减
        if (!scrollbarDragging && !listAutoScroll) {
            if (Math.abs(scrollVel) > 1f) {
                listScroll += scrollVel * frameDt;
                scrollVel *= Math.max(0f, 1f - frameDt * 9f);
                if (Math.abs(scrollVel) < 3f) scrollVel = 0f;
            } else {
                scrollVel = 0f;
            }
        }
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        // 滚动条
        barX = lx + lw + 6;
        barW = 5;
        float thumbH = maxScroll <= 0 ? listH : Math.max(28f, listH * listH / contentH);
        // 拖动优先于一切：直接由鼠标位置反推列表位移
        if (scrollbarDragging) {
            float travel = Math.max(1f, listH - thumbH);
            listScroll = clamp01((my - listTop - thumbH / 2f) / travel) * maxScroll;
            scrollVel = 0f;
        }
        float thumbY = listTop + (maxScroll <= 0 ? 0 : (listH - thumbH) * (listScroll / maxScroll));

        if (total == 0) {
            TrueTypeFont f = font(15);
            String e = query.isEmpty() ? "没有歌曲" : "没有匹配「" + trimTo(query, 16) + "」的歌曲";
            f.drawStringWithShadow(ctx, e, lx + (lw - f.getStringWidth(e)) / 2, listTop + 30, C_TEXT3);
            drawScrollbar(ctx, thumbY, thumbH, false, mx, my);
            Render2D.setGlobalAlpha(1f);
            return;
        }

        Render2D.beginScissor(ctx, lx, listTop, lw, listH);
        int first = Math.max(0, (int) Math.floor(listScroll / listRowH));
        for (int n = first; n < total; n++) {
            float ry = listTop - listScroll + n * listRowH;
            if (ry > listBottom) break;
            drawListRow(ctx, m, n, ry, lx, lw, mx, my);
        }
        Render2D.endScissor(ctx);
        drawScrollbar(ctx, thumbY, thumbH, maxScroll > 0, mx, my);
        Render2D.setGlobalAlpha(1f);
    }

    /** 搜索框 + 排序切换 + 歌词索引进度 */
    private void drawListToolbar(DrawContext ctx, MusicManager m, float mx, float my, float lx, float lw) {
        searchH = 26;
        searchY = hudY + HEADER_H + 10;
        searchW = Math.max(180f, Math.min(300f, lw * 0.34f));
        searchX = lx;

        TrueTypeFont f = font(12);
        boolean sh = inside(mx, my, searchX, searchY, searchW, searchH);
        float shv = hoverAnim("search", sh || searchFocused);
        int boxBg = mix(0xFFFFFFFF, 0xFFF2F7FB, shv);
        int boxLine = mix(C_DIV, C_ACCENT2, shv);
        square(ctx, searchX, searchY, searchW, searchH, 0, boxBg);
        Render2D.drawRect(ctx, searchX, searchY, searchW, 1, boxLine);
        Render2D.drawRect(ctx, searchX, searchY + searchH - 1, searchW, 1, boxLine);
        Render2D.drawRect(ctx, searchX, searchY, 1, searchH, boxLine);
        Render2D.drawRect(ctx, searchX + searchW - 1, searchY, 1, searchH, boxLine);

        float iconX = searchX + 10;
        drawSearchIcon(ctx, iconX, searchY + searchH / 2f, shv, boxBg);
        String shown = query.isEmpty() ? "" : trimTo(query, 18);
        float textX = iconX + 14;
        if (shown.isEmpty() && !searchFocused) {
            f.drawStringWithShadow(ctx, "搜索歌名 / 歌手 / 歌词", textX, searchY + (searchH - f.getHeight()) / 2, C_TEXT3);
        } else {
            f.drawStringWithShadow(ctx, shown, textX, searchY + (searchH - f.getHeight()) / 2, C_TEXT);
        }
        // 光标
        if (searchFocused) {
            float caretX = textX + (shown.isEmpty() ? 0 : f.getStringWidth(shown)) + 1;
            if ((System.currentTimeMillis() / 500L) % 2 == 0) {
                Render2D.drawRect(ctx, caretX, searchY + 6, 1, searchH - 12, C_ACCENT2);
            }
        }
        if (!query.isEmpty()) {
            float cx = searchX + searchW - 16;
            float cv = hoverAnim("searchclear", inside(mx, my, cx - 8, searchY, 22, searchH));
            f.drawStringWithShadow(ctx, "✕", cx, searchY + (searchH - f.getHeight()) / 2, mix(C_TEXT3, C_TEXT, cv));
        }

        // 排序
        String[] sorts = {"默认", "歌名", "歌手", "时长"};
        chipH = 24;
        chipW = 44;
        sortY = searchY + (searchH - chipH) / 2f;
        sortX = searchX + searchW + 14;
        TrueTypeFont sf = font(11);
        for (int i = 0; i < sorts.length; i++) {
            float x = sortX + i * (chipW + 6);
            boolean sel = sortMode == i;
            float hv = hoverAnim("sort" + i, inside(mx, my, x, sortY, chipW, chipH) && !sel);
            float selv = hoverAnim("sortsel" + i, sel);
            square(ctx, x, sortY, chipW, chipH, 0, mix(mix(0x00000000, 0xFFEEF3F8, hv), C_ACCENT, selv));
            sf.drawStringWithShadow(ctx, sorts[i], x + (chipW - sf.getStringWidth(sorts[i])) / 2,
                    sortY + (chipH - sf.getHeight()) / 2, mix(mix(C_TEXT3, C_TEXT2, hv), 0xFFFFFFFF, selv));
        }

        // 歌词索引进度
        if (m.isLyricIndexing() && m.getLyricIndexTotal() > 0) {
            String p = "歌词索引 " + m.getLyricIndexDone() + "/" + m.getLyricIndexTotal();
            TrueTypeFont pf = font(10);
            pf.drawStringWithShadow(ctx, p, lx + lw - pf.getStringWidth(p), searchY + (searchH - pf.getHeight()) / 2, C_TEXT3);
        } else if (m.isThrottled()) {
            String p = "接口限流 " + m.getThrottleSeconds() + "s";
            TrueTypeFont pf = font(10);
            pf.drawStringWithShadow(ctx, p, lx + lw - pf.getStringWidth(p), searchY + (searchH - pf.getHeight()) / 2, 0xFFC98A5B);
        }
    }

    /** 放大镜：外圈用底色再盖一层做成圆环（Render2D 只有实心扇形） */
    private void drawSearchIcon(DrawContext ctx, float cx, float cy, float hv, int bg) {
        int col = mix(C_TEXT3, C_ACCENT2, hv);
        Render2D.drawArc(ctx, cx, cy - 1, 4.2f, 0, 360, col);
        Render2D.drawArc(ctx, cx, cy - 1, 2.6f, 0, 360, bg);
        Render2D.drawRect(ctx, cx + 2.4f, cy + 1.4f, 1.4f, 4.2f, col);
    }

    private void drawListRow(DrawContext ctx, MusicManager m, int n, float ry, float lx, float lw, float mx, float my) {
        int idx = view[n];
        MusicManager.Song s = m.getPlaylist().get(idx);
        boolean cur = idx == m.getCurrentIndex();
        float hv = hoverAnim("row" + n, inside(mx, my, lx, ry, lw, listRowH - 2));
        if (hv > 0.01f) {
            Render2D.drawRect(ctx, lx + 2, ry, lw - 4, listRowH - 2,
                    ColorUtil.applyAlpha(0xFFEEF3F8, (int) (255 * hv)));
        }
        if (cur) Render2D.drawRect(ctx, lx + 2, ry, lw - 4, listRowH - 2, C_CUR);
        Render2D.drawRect(ctx, lx + 2, ry + listRowH - 2, lw - 4, 1, 0x14C9D6E3);

        // 序号（右对齐到固定列宽）/ 播放中跳动条
        float noX = lx + 14;
        if (cur && m.isPlaying()) {
            drawEq(ctx, noX + 3, ry + listRowH / 2 - 8);
        } else {
            TrueTypeFont nf = font(cur ? 13 : 11);
            String no = String.valueOf(n + 1);
            nf.drawString(ctx, no, noX + 16 - nf.getStringWidth(no),
                    ry + (listRowH - 2 - nf.getHeight()) / 2f, cur ? C_ACCENT : C_TEXT3);
        }

        // 缩略封面
        float cs = 38;
        float cx = lx + 36;
        float cy = ry + (listRowH - 2 - cs) / 2f;
        Identifier thumb = m.getThumbnail(s.getPic());
        square(ctx, cx, cy, cs, cs, 0, 0xFFE8EEF5);
        if (thumb != null) {
            Render2D.drawTexture(ctx, thumb, cx, cy, cs, cs);
        } else {
            TrueTypeFont nf = font(16);
            String note = "♪";
            nf.drawString(ctx, note, cx + (cs - nf.getStringWidth(note)) / 2f, cy + (cs - nf.getHeight()) / 2f, 0x66B8C4D0);
        }

        // 时长（右对齐）
        TrueTypeFont df = font(11);
        String dur = s.getDuration() > 0 ? fmt(s.getDuration()) : "--:--";
        float durW = df.getStringWidth(dur);
        df.drawStringWithShadow(ctx, dur, lx + lw - 16 - durW, ry + (listRowH - 2 - df.getHeight()) / 2f, C_TEXT3);

        // 标题 + 歌手 · 专辑
        float tx = cx + cs + 12;
        float avail = lx + lw - 26 - durW - tx;
        TrueTypeFont t1 = font(cur ? 14 : 13);
        TrueTypeFont t2 = font(10);
        String title = clip(t1, s.getName(), avail);
        String sub = s.getArtist();
        if (!s.getAlbum().isEmpty() && !s.getAlbum().equals(s.getArtist())) sub = sub + " · " + s.getAlbum();
        sub = clip(t2, sub, avail);
        t1.drawStringWithShadow(ctx, title, tx, ry + (listRowH - 2) / 2f - t1.getHeight() - 1, cur ? C_TEXT : C_TEXT2);
        t2.drawStringWithShadow(ctx, sub, tx, ry + (listRowH - 2) / 2f + 2, C_TEXT3);
    }

    private void drawScrollbar(DrawContext ctx, float thumbY, float thumbH, boolean active, float mx, float my) {
        if (!active) return;
        square(ctx, barX, listTop, barW, listH, 0, 0x14000000);
        float target = (scrollbarDragging || inside(mx, my, barX - 5, listTop, barW + 10, listH)) ? 1f : 0f;
        float hv = hoverAnim("bar", target > 0f);
        square(ctx, barX, thumbY, barW, thumbH, 0, mix(0x55000000, C_ACCENT2, hv));
    }

    // ===================== 列表：搜索 / 排序 / 视图 =====================

    private void rebuildViewIfNeeded(MusicManager m) {
        if (viewVersion == sortMode && playlistSig == m.getPlaylist().size() * 31L + m.getLyricIndexVersion()) return;
        viewVersion = sortMode;
        playlistSig = m.getPlaylist().size() * 31L + m.getLyricIndexVersion();
        List<Integer> hits = m.search(query);
        Integer[] arr = hits.toArray(new Integer[0]);
        String q = query.toLowerCase();
        // 完全命中（歌名/歌手）的排前面，只有歌词命中的排后面
        java.util.Arrays.sort(arr, (x, y) -> {
            int rx = rank(m, x, q), ry2 = rank(m, y, q);
            if (rx != ry2) return Integer.compare(rx, ry2);
            switch (sortMode) {
                case 1:
                    return m.getPlaylist().get(x).getName().compareToIgnoreCase(m.getPlaylist().get(y).getName());
                case 2:
                    return m.getPlaylist().get(x).getArtist().compareToIgnoreCase(m.getPlaylist().get(y).getArtist());
                case 3:
                    return Double.compare(m.getPlaylist().get(y).getDuration(), m.getPlaylist().get(x).getDuration());
                default:
                    return Integer.compare(x, y);
            }
        });
        view = new int[arr.length];
        for (int i = 0; i < arr.length; i++) view[i] = arr[i];
        if (listScroll > 0) listAutoScroll = false;
    }

    /** 0 = 歌名/歌手/专辑命中，1 = 只有歌词命中。 */
    private int rank(MusicManager m, int idx, String q) {
        if (q.isEmpty()) return 0;
        MusicManager.Song s = m.getPlaylist().get(idx);
        return (s.getName().toLowerCase().contains(q) || s.getArtist().toLowerCase().contains(q)
                || s.getAlbum().toLowerCase().contains(q)) ? 0 : 1;
    }

    private int viewIndexOf(int playlistIndex) {
        for (int i = 0; i < view.length; i++) {
            if (view[i] == playlistIndex) return i;
        }
        return -1;
    }

    private void setQuery(String q) {
        query = q;
        playlistSig = -1;   // 强制重建
        if (!q.isEmpty()) MusicManager.getInstance().ensureLyricIndex();
    }

    /** 搜索框输入（由 MixinKeyboard 的字符事件驱动）。 */
    public boolean onChar(char c) {
        if (!open || mode != 1 || !searchFocused) return false;
        if (c < 32 || c == 127 || query.length() >= 40) return false;
        setQuery(query + c);
        return true;
    }

    /** 搜索框按键（退格 / 回车 / Esc）。返回 true 表示已消费。 */
    public boolean onKey(int key) {
        if (!open || mode != 1 || !searchFocused) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            setSearchFocused(false);
            return true;
        }
        return false;
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
            volDragging = false;   // 松开即结束音量拖动
            scrollbarDragging = false;
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
            playlistSig = -1;     // 进列表页重建一次视图
            listAutoScroll = true;
            scrollVel = 0f;
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
            if (inside(mx, my, volX - 8, volY - 8, volW + 16, 22)) {
                volDragging = true;
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
            // 清空按钮（在搜索框内部，必须先判）
            if (!query.isEmpty() && inside(mx, my, searchX + searchW - 22, searchY, 22, searchH)) {
                setQuery("");
                return true;
            }
            if (inside(mx, my, searchX, searchY, searchW, searchH)) {
                setSearchFocused(true);
                return true;
            }
            for (int i = 0; i < 4; i++) {
                float x = sortX + i * (chipW + 6);
                if (inside(mx, my, x, sortY, chipW, chipH)) {
                    if (sortMode != i) {
                        sortMode = i;
                        playlistSig = -1;
                        listAutoScroll = true;
                    }
                    setSearchFocused(false);
                    return true;
                }
            }
            if (inside(mx, my, barX - 5, listTop, barW + 10, listH)) {
                scrollbarDragging = true;
                scrollVel = 0f;
                listAutoScroll = false;
                return true;
            }
            if (inside(mx, my, hudX + 22, listTop, hudW - 44, listH)) {
                int n = (int) ((my - listTop + listScroll) / listRowH);
                if (n >= 0 && n < view.length) {
                    m.playIndex(view[n]);
                    return true;
                }
            }
            setSearchFocused(false);
            return true;
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
                // 只给速度，位移交给每帧的惯性衰减，滚动带一点滑
                scrollVel -= (float) amount * 1100f;
                scrollVel = Math.max(-4200f, Math.min(4200f, scrollVel));
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
