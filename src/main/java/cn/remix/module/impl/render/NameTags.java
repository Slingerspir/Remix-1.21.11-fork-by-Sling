package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class NameTags extends Module {
    private final NumberValue scale = new NumberValue("Scale", 0.3f, 0.1f, 0.5f, 0.01f);

    private final Map<PlayerEntity, Vec3d> screenPositions = new HashMap<>();

    public NameTags() {
        super("NameTags", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        screenPositions.clear();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isRemoved()) continue;
            if (!EntityUtil.isSelected(player, true, true, false, true)) continue;

            Vec3d pos = new Vec3d(player.getX(), player.getY() + player.getHeight() + 0.5, player.getZ());
            Vec3d screen = ProjectUtil.worldSpaceToScreenSpace(pos, event.getProjectionMatrix(), event.getModelViewMatrix());
            if (screen.x != 0 || screen.y != 0 || screen.z != 0) {
                screenPositions.put(player, screen);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        TrueTypeFont font = instance.getFontManager().getFont((int) (16 * scale.getValue()));

        for (Map.Entry<PlayerEntity, Vec3d> entry : screenPositions.entrySet()) {
            PlayerEntity player = entry.getKey();
            if (player == null || !player.isAlive()) continue;

            String name = player.getName().getString();
            float health = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float absorption = player.getAbsorptionAmount();

            String healthText = String.format("%.1f", health);
            String text = name + " " + healthText;
            float w = font.getStringWidth(text);

            Vec3d screen = entry.getValue();
            float x = (float) screen.x;
            float y = (float) screen.y;

            Render2D.drawRect(event.getContext(), x - w / 2 - 3, y - 5, w + 6, 11, 0x80000000);
            font.drawStringWithShadow(event.getContext(), text, x - w / 2, y - 2, 0xFFFFFFFF);

            // health bar
            float barWidth = w + 6;
            float healthRatio = MathHelper.clamp(health / maxHealth, 0, 1);
            float absRatio = maxHealth > 0 ? MathHelper.clamp(absorption / maxHealth, 0, 1) : 0;

            Render2D.drawRect(event.getContext(), x - barWidth / 2, y + 7, barWidth, 2, 0x80000000);
            if (absRatio > 0) {
                Render2D.drawRect(event.getContext(), x - barWidth / 2, y + 7, barWidth * absRatio, 2, 0xFFFFFF55);
            }
            Render2D.drawRect(event.getContext(), x - barWidth / 2, y + 7, barWidth * healthRatio, 2, health > 6 ? 0xFF55FF55 : 0xFFFF5555);
        }
    }
}
