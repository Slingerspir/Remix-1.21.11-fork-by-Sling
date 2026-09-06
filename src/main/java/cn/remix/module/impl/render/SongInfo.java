package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.management.MusicManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.Render2D;
import net.minecraft.util.Identifier;

import java.awt.*;

public final class SongInfo extends Module {
    private final ColorValue accent = new ColorValue("Accent", new Color(120, 200, 255));
    private final NumberValue x = new NumberValue("X", 8, 0, 1000, 1);
    private final NumberValue y = new NumberValue("Y", 8, 0, 1000, 1);
    private final NumberValue width = new NumberValue("Width", 280, 180, 440, 1);
    private final BoolValue showCover = new BoolValue("Cover", true);
    private final BoolValue showProgress = new BoolValue("Progress", true);

    public SongInfo() {
        super("SongInfo", Category.Render);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        MusicManager m = MusicManager.getInstance();
        if (m.getPlaylist().isEmpty() || m.getCurrentIndex() < 0) return;

        MusicManager.Song song = m.getPlaylist().get(m.getCurrentIndex());
        float px = x.getValue();
        float py = y.getValue();
        float w = width.getValue();
        float cardH = 84.0f;
        float coverSize = showCover.getValue() ? 60.0f : 0.0f;
        float textX = px + 14.0f + coverSize;
        float textW = w - 28.0f - coverSize;

        drawGlassCard(event, px, py, w, cardH);

        if (showCover.getValue()) {
            Identifier cover = m.getCoverTexture();
            if (cover != null) {
                Render2D.drawTexture(event.getContext(), cover, px + 10, py + 12, coverSize, coverSize);
            } else {
                Render2D.drawRect(event.getContext(), px + 10, py + 12, coverSize, coverSize, 0x33333B44);
            }
        }

        TrueTypeFont titleFont = instance.getFontManager().getFont(16);
        TrueTypeFont smallFont = instance.getFontManager().getFont(11);

        String title = song.getName();
        if (title.length() > 22) title = title.substring(0, 22) + "...";
        titleFont.drawStringWithShadow(event.getContext(), title, textX, py + 14, 0xFFFFFFFF);

        String artist = song.getArtist();
        if (artist.length() > 30) artist = artist.substring(0, 30) + "...";
        smallFont.drawStringWithShadow(event.getContext(), artist, textX, py + 36, 0x99FFFFFF);

        if (showProgress.getValue()) {
            double progress = m.getProgress();
            double max = 240.0;
            float barY = py + cardH - 26;
            Render2D.drawRect(event.getContext(), textX, barY, textW, 2.0f, 0x40FFFFFF);
            float fill = (float) Math.min(1.0, progress / max);
            Render2D.drawRect(event.getContext(), textX, barY, textW * fill, 2.0f, accent.getValue().getRGB());

            String timeText = formatTime(progress) + " / " + formatTime(max);
            smallFont.drawStringWithShadow(event.getContext(), timeText, textX, barY + 5, 0x88FFFFFF);
        }

        // 播放状态指示（右上角）
        String status = m.isPlaying() ? "▶" : "❚❚";
        titleFont.drawString(event.getContext(), status, px + w - 26, py + 12, accent.getValue().getRGB());
    }

    private void drawGlassCard(Render2DEvent event, float x, float y, float w, float h) {
        // 直角玻璃质感：半透明深色底 + 顶部高光渐变 + 细边框
        Render2D.drawRect(event.getContext(), x, y, w, h, 0xC4141418);
        Render2D.drawGradient(event.getContext(), x, y, w, 30, 0x1EFFFFFF, 0x00000000, false);
        Render2D.drawRect(event.getContext(), x, y, w, 1, 0x26FFFFFF);
        Render2D.drawRect(event.getContext(), x, y + h - 1, w, 1, 0x14000000);
    }

    private String formatTime(double seconds) {
        int s = (int) seconds;
        int m = s / 60;
        s %= 60;
        return String.format("%d:%02d", m, s);
    }
}
