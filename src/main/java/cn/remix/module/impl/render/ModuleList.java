package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.hud.Drag;
import cn.remix.util.render.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.List;

public final class ModuleList extends Drag {
    private final BoolValue important = new BoolValue("Only Important", false);
    private final NumberValue spacing = new NumberValue("Spacing", 0, -3, 0, 1);

    public ModuleList() {
        super("ModuleList");
        percentX = 1;
        percentY = 0;
    }

    @Override
    public boolean isRightAnchored() {
        return percentX > 0.5F;
    }

    private String getModuleName(Module m) {
        String name = m.getName().replaceAll("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
        return name + (m.getSuffix().isEmpty() ? "" : " " + Formatting.GRAY + m.getSuffix());
    }

    @Override
    public void render(DrawContext context) {
        TrueTypeFont font = instance.getFontManager().getFont(20);

        List<Module> modules = Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(m -> !m.isHidden() && (m.isEnabled() || m.getAnimation().getValue() > 0))
                .filter(m -> !important.getValue() || m.getCategory() != Category.Render)
                .sorted(Comparator.comparingInt((Module m) -> (int) font.getStringWidth(getModuleName(m))).reversed().thenComparing(Module::getName))
                .toList();

        if (modules.isEmpty()) return;

        float maxWidth = 0;
        for (Module m : modules) {
            if (m.getAnimation().getValue().floatValue() > 0.01f) {
                maxWidth = Math.max(maxWidth, font.getStringWidth(getModuleName(m)));
            }
        }
        this.width = maxWidth + 4;

        float offsetY = renderY;
        float fontH = font.getHeight();
        boolean right = isRightAnchored();
        int index = 0;

        for (Module m : modules) {
            m.getAnimation().run(m.isEnabled() ? 1 : 0);
            float alpha = m.getAnimation().getValue().floatValue();
            if (alpha <= 0.01f) continue;
            float textWidth = font.getStringWidth(getModuleName(m));
            float offset = (1 - alpha) * (this.width + 10);
            float x = right ? (renderX - textWidth + offset) : (renderX - offset);
            int fontColor =  getModule(HUD.class).getWhiteMode().getValue() ? -1 : getModule(HUD.class).getColor(index++);
            font.drawStringWithShadow(context, getModuleName(m), right ? x - 2 : x + 2, offsetY, ColorUtil.applyAlpha(fontColor, alpha));
            offsetY += (fontH + spacing.getValue()) * alpha;
        }
        this.height = Math.max(fontH, offsetY - renderY);
    }
}