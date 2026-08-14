package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import lombok.Getter;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class BetterTab extends Module {

    private final ModeValue sorting = new ModeValue("Sorting", "Vanilla", "Vanilla", "Ping", "Alphabetical", "ReverseAlphabetical", "NameLength");
    private final BoolValue showGameMode = new BoolValue("Show GameMode", true);
    private final BoolValue accurateLatency = new BoolValue("Accurate Latency", true);
    private final BoolValue latencySuffix = new BoolValue("Latency Suffix", true, () -> accurateLatency.getValue());

    private final NumberValue tabSize = new NumberValue("Tab Size", 80, 1, 200, 1);
    private final NumberValue columnHeight = new NumberValue("Column Height", 20, 1, 50, 1);

    private final BoolValue highlightEnabled = new BoolValue("Highlight", true);
    private final ColorValue selfColor = new ColorValue("Self Color", new Color(50, 193, 50, 80));
    private final ColorValue friendColor = new ColorValue("Friend Color", new Color(16, 89, 203, 80));
    private final ColorValue otherColor = new ColorValue("Other Color", new Color(35, 35, 35, 80));

    private final BoolValue playerHider = new BoolValue("Player Hider", false);

    private List<PlayerListEntry> cachedPlayers = new ArrayList<>();

    public BetterTab() {
        super("BetterTab", Category.Misc);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        cachedPlayers.clear();
        Util.log("[BetterTab] Enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        cachedPlayers.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.getNetworkHandler() != null) {
            List<PlayerListEntry> players = new ArrayList<>(mc.getNetworkHandler().getPlayerList());

            Comparator<PlayerListEntry> comp = getComparator();
            if (comp != null) {
                players.sort(comp);
            }

            int maxSize = tabSize.getValue().intValue();
            if (players.size() > maxSize) {
                players = players.subList(0, maxSize);
            }

            cachedPlayers = players;
        }

        setSuffix(cachedPlayers.size() + " players");
    }


    private Comparator<PlayerListEntry> getComparator() {
        String sort = sorting.getValue();
        return switch (sort) {
            case "Ping" -> Comparator.comparingInt(PlayerListEntry::getLatency);
            case "Alphabetical" -> Comparator.comparing(p -> getPlayerName(p));
            case "ReverseAlphabetical" -> Comparator.comparing((PlayerListEntry p) -> getPlayerName(p)).reversed();
            case "NameLength" -> Comparator.comparingInt(p -> getPlayerName(p).length());
            default -> null;
        };
    }

    private String getPlayerName(PlayerListEntry entry) {
        try {
            var profile = entry.getProfile();
            var field = profile.getClass().getDeclaredField("name");
            field.setAccessible(true);
            return (String) field.get(profile);
        } catch (Exception e) {
            Text display = entry.getDisplayName();
            if (display != null) {
                return display.getString();
            }
            return "Unknown";
        }
    }

    private String getPlayerNameForDisplay(PlayerListEntry entry) {
        Text display = entry.getDisplayName();
        if (display != null && !display.getString().isEmpty()) {
            return display.getString();
        }
        return getPlayerName(entry);
    }


    public List<PlayerListEntry> getPlayers() {
        return new ArrayList<>(cachedPlayers);
    }

    public String getFormattedName(PlayerListEntry entry) {
        String displayName = getPlayerNameForDisplay(entry);

        if (showGameMode.getValue()) {
            GameMode gameMode = entry.getGameMode();
            String modeName = getGameModeName(gameMode);
            if (modeName != null) {
                return displayName + " (" + modeName + ")";
            }
        }

        return displayName;
    }

    private String getGameModeName(GameMode gameMode) {
        if (gameMode == null) return null;
        return switch (gameMode) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
            default -> null;
        };
    }

    public String getLatencyString(PlayerListEntry entry) {
        int latency = entry.getLatency();
        if (!accurateLatency.getValue()) {
            if (latency < 150) return "Green";
            if (latency < 300) return "Yellow";
            if (latency < 600) return "Orange";
            return "Red";
        }
        if (latencySuffix.getValue()) {
            return latency + "ms";
        }
        return String.valueOf(latency);
    }

    public boolean shouldHighlight(PlayerListEntry entry) {
        if (!highlightEnabled.getValue()) return false;
        if (mc.player == null) return false;

        String name = getPlayerName(entry);
        String selfName = mc.player.getName().getString();

        return name.equals(selfName) || isFriend(name);
    }

    public Color getHighlightColor(PlayerListEntry entry) {
        if (mc.player == null) return otherColor.getValue();

        String name = getPlayerName(entry);
        String selfName = mc.player.getName().getString();

        if (name.equals(selfName)) return selfColor.getValue();
        if (isFriend(name)) return friendColor.getValue();

        return otherColor.getValue();
    }

    private boolean isFriend(String name) {
        return false;
    }

    public int getTabSizeValue() {
        return tabSize.getValue().intValue();
    }

    public int getColumnHeightValue() {
        return columnHeight.getValue().intValue();
    }


}