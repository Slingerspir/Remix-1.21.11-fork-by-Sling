package cn.remix.ui.font;

import cn.remix.ui.font.base.FontData;
import cn.remix.ui.font.base.FontTexture;
import cn.remix.util.IMinecraft;
import cn.remix.util.misc.StringUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrueTypeFont implements IMinecraft {
    private final FontTexture fontTexture;
    private final float fontHeight;
    private final float scale;

    private final Map<String, GlyphLayout> cache = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GlyphLayout> eldest) {
            return size() > 8964;
        }
    };

    public TrueTypeFont(Font font, List<Font> fallbackFont, float scale) {
        this.fontTexture = new FontTexture(font, fallbackFont);
        this.scale = scale;
        this.fontHeight = fontTexture.getFontHeight() / scale;
    }

    public void drawStringWithShadow(DrawContext ctx, String text, float x, float y, int color) {
        drawString(ctx, text, x, y, color, true);
    }

    public void drawString(DrawContext ctx, String text, float x, float y, int color) {
        drawString(ctx, text, x, y, color, false);
    }

    public void drawString(DrawContext ctx, String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }

        if (text.indexOf('§') < 0) {
            GlyphLayout layout = cache.computeIfAbsent(text, this::shape);
            if (shadow) {
                renderLayout(ctx, layout, null, color, x + 0.5f, y + 0.5f, true);
            }
            renderLayout(ctx, layout, null, color, x, y, false);
            return;
        }

        int len = text.length();
        int[] colors = new int[len];
        StringBuilder stripped = new StringBuilder(len);
        int cur = color;
        int alpha = color & 0xFF000000;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                char code = text.charAt(++i);
                cur = (code == 'r' || code == 'R') ? color : (StringUtil.parseColorCode(code, cur) & 0x00FFFFFF) | alpha;
            } else {
                colors[stripped.length()] = cur;
                stripped.append(c);
            }
        }

        GlyphLayout layout = cache.computeIfAbsent(stripped.toString(), this::shape);
        if (shadow) {
            renderLayout(ctx, layout, colors, 0, x + 0.5f, y + 0.5f, true);
        }
        renderLayout(ctx, layout, colors, 0, x, y, false);
    }

    private GlyphLayout shape(String text) {
        if (text.isEmpty()) {
            return new GlyphLayout(new int[0], new Font[0], new float[0], new float[0], new int[0], 0, new FontData[0]);
        }

        char[] chars = text.toCharArray();
        int len = chars.length;

        int[] glyphs = new int[len * 2];
        Font[] fonts = new Font[len * 2];
        float[] glyphX = new float[len * 2];
        float[] glyphY = new float[len * 2];
        int[] indices = new int[len * 2];

        int count = 0;
        int start = 0;
        float cursorX = 0;
        Font currentFont = fontTexture.getFont(Character.codePointAt(chars, 0));

        for (int i = 0; i < len; ) {
            int cp = Character.codePointAt(chars, i);
            int next = i + Character.charCount(cp);
            Font nextFont = (next < len) ? fontTexture.getFont(Character.codePointAt(chars, next)) : null;

            if (nextFont != currentFont || next == len) {
                GlyphVector gv = currentFont.layoutGlyphVector(fontTexture.getContext(), chars, start, next, Font.LAYOUT_LEFT_TO_RIGHT);

                int numGlyphs = gv.getNumGlyphs();
                for (int j = 0; j < numGlyphs; j++) {
                    int code = gv.getGlyphCode(j);
                    if (code == 0) {
                        continue;
                    }
                    glyphs[count] = code;
                    fonts[count] = currentFont;
                    glyphX[count] = cursorX + (float) gv.getGlyphPosition(j).getX();
                    glyphY[count] = (float) gv.getGlyphPosition(j).getY();
                    indices[count] = start + gv.getGlyphCharIndex(j);
                    count++;
                }
                cursorX += (float) gv.getLogicalBounds().getWidth();
                currentFont = nextFont;
                start = next;
            }
            i = next;
        }

        FontData[] data = new FontData[count];
        for (int i = 0; i < count; i++) {
            data[i] = fontTexture.getGlyphTexture(fonts[i], glyphs[i]);
        }
        fontTexture.flush();

        return new GlyphLayout(Arrays.copyOf(glyphs, count), Arrays.copyOf(fonts, count), Arrays.copyOf(glyphX, count), Arrays.copyOf(glyphY, count), Arrays.copyOf(indices, count), cursorX, data);
    }

    private void renderLayout(DrawContext ctx, GlyphLayout layout, int[] colors, int uniformColor, float x, float y, boolean shadow) {
        FontData[] data = layout.data();
        if (data.length == 0) {
            return;
        }

        float[] xs = layout.xs();
        float[] ys = layout.ys();
        int[] indices = layout.indices();

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.scale(1f / scale, 1f / scale);

        int baseX = Math.round(x * scale);
        int baseY = Math.round(y * scale + fontTexture.getFontAscent());

        for (int i = 0; i < data.length; i++) {
            FontData glyph = data[i];
            if (glyph.width() <= 0) {
                continue;
            }
            int c = colors != null ? colors[indices[i]] : uniformColor;
            if (shadow) {
                c = (c & 0xFCFCFC) >> 2 | (c & 0xFF000000);
            }
            int px = baseX + Math.round(xs[i] + glyph.offsetX());
            int py = baseY + Math.round(ys[i] + glyph.offsetY());
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, glyph.atlasId(), px, py, glyph.u0() * 4096f, glyph.v0() * 4096f, glyph.width(), glyph.height(), 4096, 4096, c);
        }
        matrices.popMatrix();
    }

    public float getStringWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return cache.computeIfAbsent(stripFormatCodes(text), this::shape).width() / scale;
    }

    public float getHeight() {
        return fontHeight;
    }

    private String stripFormatCodes(String text) {
        if (text.indexOf('§') < 0) return text;

        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '§' && i + 1 < len) {
                i++;
            } else {
                sb.append(text.charAt(i));
            }
        }
        return sb.toString();
    }

    private record GlyphLayout(int[] glyphs, Font[] fonts, float[] xs, float[] ys, int[] indices, float width, FontData[] data) {}
}
