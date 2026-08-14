package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.Formatting;

import java.util.Objects;

public class Teams extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Color", "Color", "Armor", "Scoreboard");

    public Teams() {
        super("Teams", Category.Player);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(mode.getValue());
    }

    public boolean isTeam(LivingEntity entity) {
        if (mc.player == null || mode.is("None")) return false;

        if (entity instanceof PlayerEntity player) {
            switch (mode.getValue()) {
                case "Armor" -> {
                    ItemStack playerHelmet = mc.player.getInventory().getStack(3);
                    ItemStack entityHelmet = player.getInventory().getStack(3);

                    if (!playerHelmet.isEmpty() && !entityHelmet.isEmpty()) {
                        DyedColorComponent playerDyed = playerHelmet.get(DataComponentTypes.DYED_COLOR);
                        DyedColorComponent entityDyed = entityHelmet.get(DataComponentTypes.DYED_COLOR);

                        if (playerDyed != null && entityDyed != null) {
                            return playerDyed.rgb() == entityDyed.rgb();
                        }
                    }
                }

                case "Color" -> {
                    String myDisplayName = Objects.requireNonNull(mc.player.getDisplayName()).getString();
                    String targetDisplayName = Objects.requireNonNull(player.getDisplayName()).getString();

                    char myColor = getColorCode(myDisplayName);
                    char targetColor = getColorCode(targetDisplayName);

                    return targetColor != '\0' && myColor == targetColor;
                }

                case "Scoreboard" -> {
                    AbstractTeam myTeam = mc.player.getScoreboardTeam();
                    AbstractTeam targetTeam = player.getScoreboardTeam();

                    if (myTeam != null && targetTeam != null) {
                        if (myTeam.isEqual(targetTeam)) return true;

                        Formatting myColor = myTeam.getColor();
                        Formatting targetColor = targetTeam.getColor();

                        return myColor != Formatting.RESET && myColor == targetColor;
                    }
                }
            }
        }

        return false;
    }

    private char getColorCode(String text) {
        if (text == null || text.isEmpty()) return '\0';

        int index = text.indexOf('§');
        if (index != -1 && index + 1 < text.length()) {
            return text.charAt(index + 1);
        }
        return '\0';
    }
}