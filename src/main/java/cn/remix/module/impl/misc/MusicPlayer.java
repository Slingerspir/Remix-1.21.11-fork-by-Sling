package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.CharInputEvent;
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
    private final StringValue apiUrl = new StringValue("API", "https://meting.mikus.ink/api");
    private final StringValue playlistId = new StringValue("Playlist ID", "0");
    private final BoolValue altControl = new BoolValue("Alt Control", true);
    private final BoolValue autoPlay = new BoolValue("Auto Play", true);
    private final BoolValue translation = new BoolValue("Translation", true);
    private final BoolValue lyricSearch = new BoolValue("Lyric Search", true);
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
        m.setApiUrl(apiUrl.getValue());
        m.setLyricSearchEnabled(lyricSearch.getValue());
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
        // 搜索框聚焦时优先吃掉按键（退格/回车/Esc）
        if (hud.isOpen() && hud.onKey(k)) return;
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

    /** 游戏内自绘界面没有原版 Screen 的 charTyped，字符走 Keyboard.onChar 的混入事件。 */
    @EventTarget
    public void onChar(CharInputEvent event) {
        if (!inGame() || !hud.isOpen()) return;
        char c = (char) event.getCodepoint();
        if (hud.onChar(c)) event.setCancelled(true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        MusicManager m = MusicManager.getInstance();
        if (m != null) m.tick();   // 限流解除后自动补拉歌单
        if (m != null && !m.getApiUrl().equals(apiUrl.getValue().trim())) {
            // 换接口地址：清掉限流状态重新拉
            m.setApiUrl(apiUrl.getValue());
            m.clearThrottle();
            m.setSource(server.getValue(), playlistId.getValue(), true);
        }
        if (m != null && m.isTranslationEnabled() != translation.getValue()) {
            m.setTranslationEnabled(translation.getValue());
        }
        if (m != null && m.isPlaying()) {
            long now = System.currentTimeMillis();
            if (now - lastSaveMs > 5000) {
                lastSaveMs = now;
                m.rememberNow();
            }
        }
        // 退出世界/回到标题界面时自动收起界面，别让覆盖层挂在菜单上
        if ((mc.player == null || mc.world == null) && hud.isOpen()) {
            hud.setOpen(false);
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

        // 平滑滑动（continuous anchor）
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - mLastMs) / 1000f);
        mLastMs = now;
        if (mGen != m.getGeneration()) {
            mGen = m.getGeneration();
            mAnchor = -1;
        }
        if (mAnchor < 0) mAnchor = active;
        mAnchor += (active - mAnchor) * Math.min(1f, dt * 12f);

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float maxW = Math.min(sw * 0.8f, 640);
        int base = Math.max(20, Math.min(26, (int) (maxW / 26)));
        TrueTypeFont big = instance.getFontManager().getFont(base);
        TrueTypeFont transFont = instance.getFontManager().getFont(14);

        // 当前行位置略高
        float rowH = base + 8f;
        float cy = sh - 118;

        int from = Math.max(0, (int) Math.floor(mAnchor) - 3);
        int to = Math.min(m.getLyrics().size() - 1, (int) Math.ceil(mAnchor) + (translation.getValue() ? 1 : 2));

        for (int i = from; i <= to; i++) {
            String text = m.getLyrics().get(i).getText();
            if (text == null || text.isEmpty()) continue;
            boolean cur = i == active;
            int dist = Math.abs(i - active);
            float y = cy + (float) (i - mAnchor) * rowH;
            if (y < sh * 0.10f || y > sh - 28) continue;

            TrueTypeFont use = cur ? big : instance.getFontManager().getFont(Math.max(13, base - 6));
            float w0 = use.getStringWidth(text);
            if (w0 > maxW) {
                int ns = Math.max(11, (int) (base * maxW / w0));
                use = instance.getFontManager().getFont(ns);
                w0 = use.getStringWidth(text);
            }
            float x = sw / 2f - w0 / 2f;
            float alpha = cur ? 1f : Math.max(0.10f, 0.9f - dist * 0.25f);

            if (cur) {
                use.drawString(event.getContext(), text, x + 0.7f, y + 0.7f, ColorUtil.applyAlpha(0xFF10B981, (int) (150 * alpha)));
                use.drawStringWithShadow(event.getContext(), text, x, y, ColorUtil.applyAlpha(0xFFFFFFFF, 255));
            } else {
                use.drawStringWithShadow(event.getContext(), text, x, y,
                        ColorUtil.applyAlpha(0xFFB9C4D2, (int) (255 * alpha)));
            }

            if (cur) {
                // 下划线位置实时算：没有双语就贴原歌词，有双语就贴翻译，翻译字号会被压缩也照样跟住
                float underlineY = y + use.getHeight();
                String tr = translation.getValue() ? m.translationAt(m.getLyrics().get(i).getTime()) : null;
                if (tr != null && tr.isEmpty()) tr = null;
                if (tr != null) {
                    TrueTypeFont tf = transFont;
                    float tw = tf.getStringWidth(tr);
                    if (tw > maxW) {
                        tf = instance.getFontManager().getFont(Math.max(11, (int) (14 * maxW / tw)));
                        tw = tf.getStringWidth(tr);
                    }
                    float trY = underlineY + 3;
                    tf.drawStringWithShadow(event.getContext(), tr, sw / 2f - tw / 2f, trY,
                            ColorUtil.applyAlpha(0xFF5BC2B8, 245));
                    underlineY = trY + tf.getHeight();
                }
                Render2D.drawRect(event.getContext(), x - 8, underlineY + 2, w0 + 16, 2,
                        ColorUtil.applyAlpha(0xFF10B981, 230));
            }
        }
    }


}
