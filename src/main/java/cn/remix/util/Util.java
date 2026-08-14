package cn.remix.util;

import cn.remix.Client;
import cn.remix.module.impl.render.Notification;
import lombok.experimental.UtilityClass;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@UtilityClass
public class Util implements IMinecraft{
    public int offGroundTicks, onGroundTicks;

    private void addChatMessage(String message) {
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal(message), false);
    }

    public void log(String message) {
        Notification notification = Client.instance == null || Client.instance.getModuleManager() == null
                ? null
                : Client.instance.getModuleManager().getModule(Notification.class);
        String prefix = notification == null
                ? Formatting.DARK_GRAY + "[" + Formatting.AQUA + Client.name + Formatting.DARK_GRAY + "] " + Formatting.RESET
                : notification.prefix(true);
        addChatMessage(prefix + message);
    }

    public void debug(String message) {
        addChatMessage(Formatting.DARK_GRAY + "[" + Formatting.RED + "Debug" + Formatting.DARK_GRAY + "] " + Formatting.RESET + message);
    }
}
