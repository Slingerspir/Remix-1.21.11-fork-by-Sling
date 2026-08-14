package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import lombok.Getter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class NameProtect extends Module {

    private final BoolValue protectSelf = new BoolValue("Protect Self", true);
    private final BoolValue protectFriends = new BoolValue("Protect Friends", true);
    private final BoolValue protectOthers = new BoolValue("Protect Others", false);
    private final ColorValue selfColor = new ColorValue("Self Color", new Color(255, 179, 72));
    private final ColorValue friendColor = new ColorValue("Friend Color", new Color(0, 241, 255));
    private final ColorValue otherColor = new ColorValue("Other Color", new Color(150, 150, 150));
    private final NumberValue maxPlayers = new NumberValue("Max Players", 50, 10, 200, 10);

    private final Map<String, String> nameMappings = new ConcurrentHashMap<>();
    private final List<String> onlinePlayers = new ArrayList<>();
    private String selfName = "";

    public NameProtect() {
        super("NameProtect", Category.Misc);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        nameMappings.clear();
        onlinePlayers.clear();
        Util.log("[NameProtect] Enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        nameMappings.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        selfName = mc.player.getName().getString();
        onlinePlayers.clear();

        if (mc.getNetworkHandler() != null) {
            for (var entry : mc.getNetworkHandler().getPlayerList()) {
                try {
                    String name = null;

                    try {
                        var profile = entry.getProfile();
                        var method = profile.getClass().getMethod("getName");
                        name = (String) method.invoke(profile);
                    } catch (Exception e1) {
                        try {
                            var profile = entry.getProfile();
                            var field = profile.getClass().getDeclaredField("name");
                            field.setAccessible(true);
                            name = (String) field.get(profile);
                        } catch (Exception e2) {
                            try {
                                var displayName = entry.getDisplayName();
                                if (displayName != null) {
                                    name = displayName.getString();
                                }
                            } catch (Exception e3) {
                            }
                        }
                    }

                    if (name == null || name.isEmpty()) {
                        continue;
                    }

                    if (!name.equals(selfName)) {
                        onlinePlayers.add(name);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        updateMappings();
        setSuffix(nameMappings.size() + " protected");
    }

    private void updateMappings() {
        nameMappings.clear();

        int max = maxPlayers.getValue().intValue();

        if (protectSelf.getValue() && !selfName.isEmpty()) {
            nameMappings.put(selfName, "You");
        }

        if (protectFriends.getValue()) {
            int count = 0;
            for (String name : onlinePlayers) {
                if (count >= max) break;
                if (isFriend(name)) {
                    String randomName = generateRandomName(name);
                    nameMappings.put(name, randomName);
                    count++;
                }
            }
        }

        if (protectOthers.getValue()) {
            int count = 0;
            for (String name : onlinePlayers) {
                if (nameMappings.containsKey(name)) continue;
                if (count >= max) break;
                String randomName = generateRandomName(name);
                nameMappings.put(name, randomName);
                count++;
            }
        }
    }

    private boolean isFriend(String name) {
        return false;
    }

    private String generateRandomName(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(source.getBytes());
            long seed = 0;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                seed |= ((long) hash[i] & 0xFF) << (i * 8);
            }
            Random random = new Random(seed);

            String[] prefixes = {"User", "Player", "Guest", "Gamer", "Pro", "Noob", "Cool", "Epic", "Legend"};
            String[] suffixes = {"X", "Z", "Y", "Q", "K", "V", "W", "M", "N", "P", "R", "S", "T"};

            String prefix = prefixes[random.nextInt(prefixes.length)];
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(1000);

            return prefix + suffix + number;
        } catch (Exception e) {
            return "Player" + Math.abs(source.hashCode());
        }
    }


    public String replaceName(String original) {
        if (!isEnabled()) return original;
        if (original == null || original.isEmpty()) return original;

        String replaced = nameMappings.get(original);
        if (replaced != null) {
            return replaced;
        }

        for (Map.Entry<String, String> entry : nameMappings.entrySet()) {
            if (original.contains(entry.getKey())) {
                return original.replace(entry.getKey(), entry.getValue());
            }
        }

        return original;
    }

    public Text replaceText(Text original) {
        if (!isEnabled()) return original;

        String text = original.getString();
        String replaced = replaceName(text);

        if (replaced.equals(text)) {
            return original;
        }

        return Text.literal(replaced).setStyle(original.getStyle());
    }

    public String getReplacementName(String original) {
        return nameMappings.getOrDefault(original, original);
    }

    public String getColoredName(String name) {
        String replaced = replaceName(name);
        if (replaced.equals(name)) {
            return name;
        }

        Color color = otherColor.getValue();
        if (name.equals(selfName)) {
            color = selfColor.getValue();
        } else if (isFriend(name)) {
            color = friendColor.getValue();
        }

        return getColorCode(color) + replaced + Formatting.RESET;
    }

    private String getColorCode(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        if (r > 200 && g < 100) return Formatting.RED.toString();
        if (r < 100 && g > 200) return Formatting.GREEN.toString();
        if (r < 100 && g < 100 && b > 200) return Formatting.BLUE.toString();
        if (r > 200 && g > 200 && b < 100) return Formatting.YELLOW.toString();
        if (r > 200 && g > 200 && b > 200) return Formatting.WHITE.toString();
        if (r < 100 && g < 100 && b < 100) return Formatting.DARK_GRAY.toString();

        return Formatting.GRAY.toString();
    }

    public boolean isProtected(String name) {
        return nameMappings.containsKey(name);
    }

    public boolean isSelfProtected() {
        return protectSelf.getValue() && !selfName.isEmpty();
    }

    public String getSelfReplacement() {
        return "You";
    }



    public record NameMapping(String original, String replacement, Color color) {}
}