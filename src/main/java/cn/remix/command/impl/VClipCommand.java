package cn.remix.command.impl;

import cn.remix.command.Command;
import cn.remix.util.IMinecraft;
import cn.remix.util.Util;
import cn.remix.util.player.TeleportUtil;

import java.util.List;

public final class VClipCommand extends Command implements IMinecraft {
    public VClipCommand() {
        super(".vclip <distance|jump|glitch>", "vclip", "vc", "vtp");
    }

    @Override
    public void execute(String[] arguments) {
        if (mc.player == null) return;

        if (arguments.length < 2) {
            Util.log(getUsage());
            Util.log(".vclip jump");
            Util.log(".vclip glitch");
            return;
        }

        if (arguments[1].equalsIgnoreCase("jump")) {
            TeleportUtil.teleportJump();
            Util.log("Executed vertical teleport jump.");
            return;
        }

        if (arguments[1].equalsIgnoreCase("glitch")) {
            TeleportUtil.teleportGlitch();
            Util.log("Executed vertical glitch teleport.");
            return;
        }

        try {
            double distance = Double.parseDouble(arguments[1]);
            TeleportUtil.teleportDirect(mc.player.getX(), mc.player.getY() + distance, mc.player.getZ());
            Util.log("VClipped " + distance + " blocks.");
        } catch (NumberFormatException ignored) {
            Util.log("Distance must be a number.");
        }
    }

    @Override
    public List<String> getCompletions(String[] arguments) {
        return arguments.length == 2 ? List.of("jump", "glitch") : super.getCompletions(arguments);
    }
}
