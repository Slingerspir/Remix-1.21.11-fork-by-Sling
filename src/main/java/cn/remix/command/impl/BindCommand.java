package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.module.Module;
import cn.remix.util.Util;
import cn.remix.util.misc.KeyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BindCommand extends Command {

    public BindCommand() {
        super(".bind <module> <key/none>", "bind", "b");
    }

    @Override
    public void execute(final String[] arguments) {
        if (arguments.length < 3) {
            Util.log(this.getUsage());
            return;
        }

        final String moduleName = arguments[1].replace(" ", "");
        final String keyName = arguments[2].toUpperCase(Locale.ROOT);
        boolean found = false;

        for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
            if (module.getName().replace(" ", "").equalsIgnoreCase(moduleName)) {
                int keyCode = KeyUtil.getKeyCode(keyName);
                module.setKey(keyCode);
                Util.log(keyCode == 0 ? String.format("Removed bind for %s.", module.getName()) : String.format("Bound %s to %s.", module.getName(), KeyUtil.getKeyName(keyCode)));
                found = true;
                break;
            }
        }

        if (!found) {
            Util.log("Module not found.");
        }
    }

    @Override
    public List<String> getCompletions(final String[] arguments) {
        List<String> completions = new ArrayList<>();

        if (arguments.length == 2) {
            for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
                completions.add(module.getName().replace(" ", ""));
            }
        } else if (arguments.length == 3) {
            completions.add("none");
        }

        return completions;
    }
}