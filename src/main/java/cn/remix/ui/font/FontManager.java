package cn.remix.ui.font;

import cn.remix.Client;
import cn.remix.util.IMinecraft;
import lombok.Getter;

import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FontManager implements IMinecraft {
    private final Map<String, TrueTypeFont> fontCache = new ConcurrentHashMap<>();
    private final Map<String, Font> rawCache = new ConcurrentHashMap<>();
    private final List<Font> systemFonts = new ArrayList<>();
    private final Set<String> availableFamilies;

    public FontManager() {
        availableFamilies = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        systemFonts.add(resolveSystemFont("Product Sans", Font.PLAIN));
        systemFonts.add(resolveSystemFont("Google Sans", Font.PLAIN));
        systemFonts.add(new Font("Microsoft YaHei", Font.PLAIN, 16));
        systemFonts.add(new Font("Segoe UI", Font.PLAIN, 16));
        systemFonts.add(new Font("Arial Unicode MS", Font.PLAIN, 16));
        systemFonts.add(new Font("SimSun", Font.PLAIN, 16));
        systemFonts.add(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
    }

    @Getter
    private final MinecraftFont mcFont = new MinecraftFont();

    public TrueTypeFont getFont(int size) {
        return get("Default", Font.PLAIN, size);
    }

    public TrueTypeFont getTahomaFont(int fontSize) {
        return get("Tahoma.ttf", fontSize);
    }

    public TrueTypeFont getBoldFont(int size) {
        return get("Default", Font.BOLD, size);
    }

    private TrueTypeFont get(String name, int size) {
        return get(name, Font.PLAIN, size);
    }

    private TrueTypeFont get(String name, int style, int size) {
        float scale = mc.getWindow().getScaleFactor();
        return fontCache.computeIfAbsent(name + "#" + style + "#" + size + "@" + scale, k -> {
            float actual = size * scale * 0.5f;
            List<Font> fallbacks = new ArrayList<>();
            for (Font f : systemFonts) {
                fallbacks.add(f.deriveFont(Font.PLAIN, actual));
            }

            return new TrueTypeFont(loadRaw(name, style).deriveFont(style, actual), fallbacks, scale);
        });
    }

    private Font loadRaw(String name, int style) {
        if ("Default".equals(name)) {
            String suffix = style == Font.BOLD ? "Bold" : "Regular";
            String[] bundled = {
                    "ProductSans-" + suffix + ".ttf",
                    "GoogleSans-" + suffix + ".ttf"
            };
            for (String candidate : bundled) {
                Font font = loadBundled(candidate);
                if (font != null) return font;
            }

            if (availableFamilies.contains("Product Sans")) {
                return new Font("Product Sans", style, 16);
            }
            if (availableFamilies.contains("Google Sans")) {
                return new Font("Google Sans", style, 16);
            }
            return new Font("Microsoft YaHei", style, 16);
        }

        return rawCache.computeIfAbsent(name, this::loadRaw);
    }

    private Font loadRaw(String name) {
        Font bundled = loadBundled(name);
        if (bundled != null) return bundled;

        Client.logger.warn("Font not found: {}", name);
        return new Font("Microsoft YaHei", Font.PLAIN, 16);
    }

    private Font loadBundled(String name) {
        try (InputStream s = FontManager.class.getResourceAsStream("/assets/remix/fonts/" + name)) {
            if (s != null) return Font.createFont(Font.TRUETYPE_FONT, s);
        } catch (Exception e) {
            Client.logger.error("Failed to load font {}", name, e);
        }

        return null;
    }

    private Font resolveSystemFont(String name, int style) {
        return new Font(availableFamilies.contains(name) ? name : "Microsoft YaHei", style, 16);
    }
}
