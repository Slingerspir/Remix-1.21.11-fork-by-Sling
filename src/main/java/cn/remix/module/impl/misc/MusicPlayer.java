package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.event.impl.MouseClickEvent;
import cn.remix.event.impl.MouseScrollEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.management.MusicManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.StringValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.music.MusicHud;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import org.lwjgl.glfw.GLFW;

public final class MusicPlayer extends Module {
    private final ModeValue server = new ModeValue("Server", "netease", "netease", "tencent");
    private final StringValue playlistId = new StringValue("Playlist ID", "0");
    private final BoolValue altControl = new BoolValue("Alt Control", true);
    private final BoolValue autoPlay = new BoolValue("Auto Play", true);
    private final BoolValue translation = new BoolValue("Translation", true);
    private final BoolValue resume = new BoolValue("Resume", true);

    private final MusicHud hud = new MusicHud();

    private long lastSaveMs;

    // 迷你歌词平滑滑动
    private double mAnchor = -1;
    private long mGen = -1;
    private long mLastMs;

    public MusicPlayer() {
        super("MusicPlayer", Category.Misc);
    }

    @Override
    public void onEnable() {
        MusicManager m = MusicManager.getInstance();
        m.setTranslationEnabled(translation.getValue());
        hud.setShowTranslation(translation.getValue());
        if (resume.getValue() && m.doResume(server.getValue(), playlistId.getValue())) {
            return; // 已恢复上次会话
        }
        m.setSource(server.getValue(), playlistId.getValue(), autoPlay.getValue());
    }

    @Override
    public void onDisable() {
        hud.setOpen(false);
        lockCursor();
        MusicManager m = MusicManager.getInstance();
        if (m != null) {
            m.rememberNow();
            m.stop();
        }
    }

    private boolean inGame() {
        return mc.player != null && mc.world != null && mc.currentScreen == null;
    }

    private void unlockCursor() {
        if (inGame() && mc.mouse.isCursorLocked()) mc.mouse.unlockCursor();
    }

    private void lockCursor() {
        if (mc.player != null && mc.world != null && mc.currentScreen == null && !mc.mouse.isCursorLocked()) {
            mc.mouse.lockCursor();
        }
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (!inGame()) return;
        int k = event.getKey();
        if (altControl.getValue() && k == GLFW.GLFW_KEY_RIGHT_ALT) {
            boolean next = !hud.isOpen();
            hud.setOpen(next);
            hud.setShowTranslation(translation.getValue());
            if (next) unlockCursor();
            else lockCursor();
        } else if (k == GLFW.GLFW_KEY_ESCAPE && hud.isOpen()) {
            hud.setOpen(false);
            lockCursor();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        MusicManager m = MusicManager.getInstance();
        if (m != null && m.isTranslationEnabled() != translation.getValue()) {
            m.setTranslationEnabled(translation.getValue());
        }
        if (m != null && m.isPlaying()) {
            long now = System.currentTimeMillis();
            if (now - lastSaveMs > 8000) {
                lastSaveMs = now;
                m.rememberNow();
            }
        }
        if (!inGame()) return;
        if (hud.isOpen()) {
            if (mc.mouse.isCursorLocked()) mc.mouse.unlockCursor();
        } else if (!mc.mouse.isCursorLocked()) {
            lockCursor();
        }
    }

    private float mouseX() {
        return (float) mc.mouse.getX() / mc.getWindow().getScaleFactor();
    }

    private float mouseY() {
        return (float) mc.mouse.getY() / mc.getWindow().getScaleFactor();
    }

    @EventTarget
    public void onMouseClick(MouseClickEvent event) {
        if (!hud.isOpen() || !inGame()) return;
        MusicManager m = MusicManager.getInstance();
        if (hud.onMouse(mouseX(), mouseY(), event.getAction(), m)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMouseScroll(MouseScrollEvent event) {
        if (!hud.isOpen() || !inGame()) return;
        hud.onScroll(mouseX(), mouseY(), event.getVerticalAmount());
        event.setCancelled(true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        MusicManager m = MusicManager.getInstance();
        hud.setShowTranslation(translation.getValue());
        if (hud.isOpen()) {
            hud.render(event.getContext(), m, mouseX(), mouseY());
        } else {
            renderMiniLyrics(event, m);
        }
    }

    // ===================== 游戏内迷你双语歌词（平滑滑动） =====================

    private void renderMiniLyrics(Render2DEvent event, MusicManager m) {
        if (m.getLyrics().isEmpty()) return;

        double progress = m.getProgress();
        int active = -1;
        for (int i = 0; i < m.getLyrics().size(); i++) {
            if (m.getLyrics().get(i).getTime() <= progress) active = i;
            else break;
        }
        if (active < 0 || active >= m.getLyrics().size()) return;

        // 限制：最多 当前 + 上方 2 行 + 1 行翻译，互不重叠
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float maxW = Math.min(sw * 0.76f, 620);
        int size = Math.max(20, Math.min(28, (int) (maxW / 26)));
        TrueTypeFont font = instance.getFontManager().getFont(size);
        TrueTypeFont transFont = instance.getFontManager().getFont(15);

        String curText = m.getLyrics().get(active).getText();
        String tr = translation.getValue() ? m.translationAt(m.getLyrics().get(active).getTime()) : null;
        if ((curText == null || curText.isEmpty()) && tr == null) return;

        TrueTypeFont useF = font;
        float cw = useF.getStringWidth(curText == null ? "" : curText);
        if (curText != null && cw > maxW) {
            int ns = Math.max(12, (int) (size * maxW / cw));
            useF = instance.getFontManager().getFont(ns);
            cw = useF.getStringWidth(curText);
        }
        float transH = (tr != null && !tr.isEmpty()) ? transFont.getHeight() + 5 : 0;

        // 当前行基线：给翻译留出空间，底部不压到快捷栏
        float activeY = sh - 26 - useF.getHeight() - transH - 4;
        if (activeY < sh * 0.3f) activeY = sh * 0.3f;

        // 上方最多 2 行淡出的历史行
        int prev = Math.min(2, active);
        float rowUp = size + 10f;
        for (int k = prev; k >= 1; k--) {
            int idx = active - k;
            String txt = m.getLyrics().get(idx).getText();
            if (txt == null || txt.isEmpty()) continue;
            float yy = activeY - rowUp * k;
            if (yy < sh * 0.12f) continue;
            TrueTypeFont pf = instance.getFontManager().getFont(Math.max(14, size - 5));
            float pw0 = pf.getStringWidth(txt);
            if (pw0 > maxW) {
                int ns = Math.max(11, (int) (size * maxW / pw0));
                pf = instance.getFontManager().getFont(ns);
                pw0 = pf.getStringWidth(txt);
            }
            float px = sw / 2f - pw0 / 2f;
            float alpha = Math.max(0.16f, 0.55f - (k - 1) * 0.18f);
            pf.drawStringWithShadow(event.getContext(), txt, px, yy, ColorUtil.applyAlpha(0xE6EDF5FF, (int) (255 * alpha)));
        }

        // 当前行（大字白色）
        float x = sw / 2f - cw / 2f;
        useF.drawStringWithShadow(event.getContext(), curText, x, activeY, 0xFFFFFFFF);

        // 翻译（独立一行，浅青偏深可读）
        if (tr != null && !tr.isEmpty()) {
            TrueTypeFont tf = transFont;
            float tw = tf.getStringWidth(tr);
            if (tw > maxW) {
                tf = instance.getFontManager().getFont(Math.max(12, (int) (15 * maxW / tw)));
                tw = tf.getStringWidth(tr);
            }
            tf.drawStringWithShadow(event.getContext(), tr, sw / 2f - tw / 2f, activeY + useF.getHeight() + 4,
                    ColorUtil.applyAlpha(0xFF4FB3C8, 240));
            float underY = activeY + useF.getHeight() + 4 + tf.getHeight() + 2;
            Render2D.drawRect(event.getContext(), x - 6, underY, cw + 12, 1.4f, ColorUtil.applyAlpha(0xFF10B981, 180));
        }
    }

}
