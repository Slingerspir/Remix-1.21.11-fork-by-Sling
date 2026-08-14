package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class BetterChat extends Module {

    private final BoolValue infinite = new BoolValue("Infinite", true);
    private final BoolValue antiClear = new BoolValue("AntiClear", true);
    private final BoolValue keepAfterDeath = new BoolValue("KeepAfterDeath", false);
    private final BoolValue forceUnicode = new BoolValue("ForceUnicode", false);

    private final BoolValue appendPrefix = new BoolValue("Append Prefix", false);
    private final String prefixText = "> "; 
    private final BoolValue appendSuffix = new BoolValue("Append Suffix", false);
    private final String suffixText = " | Remix"; 

    private final BoolValue antiSpam = new BoolValue("AntiSpam", true);
    private final BoolValue stackMessages = new BoolValue("Stack Messages", true);
    private final BoolValue regexFilters = new BoolValue("Filters", false);
    private final NumberValue filterCount = new NumberValue("Filter Count", 0, 0, 10, 1);

    private final BoolValue copyEnabled = new BoolValue("Copy", true);
    private final BoolValue copyNotify = new BoolValue("Copy Notify", true);

    private final Map<String, Integer> messageCounts = new HashMap<>();
    private final List<Pattern> filterPatterns = new ArrayList<>();

    public BetterChat() {
        super("BetterChat", Category.Misc);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        messageCounts.clear();
        compileFilters();
        Util.log("[BetterChat] Enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        messageCounts.clear();
    }

    private void compileFilters() {
        filterPatterns.clear();
        String[] defaultFilters = {"", ""};
        for (String filter : defaultFilters) {
            String trimmed = filter.trim();
            if (!trimmed.isEmpty()) {
                try {
                    filterPatterns.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    Util.log("[BetterChat] Invalid regex: " + trimmed);
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Received) return;

        if (event.getPacket() instanceof GameMessageS2CPacket packet) {
            String message = packet.content().getString();
            boolean shouldCancel = false;

            if (antiSpam.getValue() && !filterPatterns.isEmpty()) {
                String content = message;
                int separator = message.indexOf('>');
                if (separator != -1 && separator + 1 < message.length()) {
                    content = message.substring(separator + 1).trim();
                }

                for (Pattern pattern : filterPatterns) {
                    if (pattern.matcher(content).matches()) {
                        shouldCancel = true;
                        break;
                    }
                }
            }

            if (shouldCancel) {
                event.setCancelled(true);
                return;
            }

            if (antiSpam.getValue() && stackMessages.getValue()) {
                event.setCancelled(true);

                String id = message + "-external";
                int count = messageCounts.getOrDefault(id, 0) + 1;
                messageCounts.put(id, count);

                String displayMessage = message;
                if (count > 1) {
                    displayMessage = message + " §7[" + count + "]";
                }

                mc.player.sendMessage(Text.literal(displayMessage), false);
                return;
            }

            if (forceUnicode.getValue()) {
                String unicode = convertToUnicode(message);
                event.setPacket(new GameMessageS2CPacket(Text.literal(unicode), packet.overlay()));
                return;
            }

            String modified = message;
            if (appendPrefix.getValue()) {
                modified = prefixText + modified;
            }
            if (appendSuffix.getValue()) {
                modified = modified + suffixText;
            }
            if (!modified.equals(message)) {
                event.setPacket(new GameMessageS2CPacket(Text.literal(modified), packet.overlay()));
            }
        }
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (mc.player == null) return;

        if (keepAfterDeath.getValue() && mc.currentScreen instanceof DeathScreen) {
            int key = event.getKey();
            if (key == mc.options.chatKey.getDefaultKey().getCode()) {
                mc.setScreen(null);
                mc.player.sendMessage(Text.literal(""), false);
            }
        }
    }


    private String convertToUnicode(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 33 && c <= 128) {
                result.append((char) (c + 65248));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public boolean isInfiniteLength() {
        return infinite.getValue();
    }

    public boolean isAntiClear() {
        return antiClear.getValue();
    }

    public String modifyMessage(String content) {
        if (!isEnabled()) return content;

        String result = content;
        if (forceUnicode.getValue()) {
            result = convertToUnicode(result);
        }
        if (appendPrefix.getValue()) {
            result = prefixText + result;
        }
        if (appendSuffix.getValue()) {
            result = result + suffixText;
        }
        return result;
    }

    public static void copyMessage(String content) {
        if (mc.player == null) return;
        mc.keyboard.setClipboard(content);
    }

}