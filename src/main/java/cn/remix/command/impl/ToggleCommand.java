package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.module.Module;
import cn.remix.util.Util;

import java.util.ArrayList;
import java.util.List;

public final class ToggleCommand extends Command {

    public ToggleCommand() {
        super(".toggle <module>", "toggle", "t");
    }

    @Override
    public void execute(String[] arguments) {
        if (arguments.length < 2) {
            Util.log(this.getUsage());
            return;
        }

        final String moduleName = arguments[1].replace(" ", "");

        for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
            if (module.getName().replace(" ", "").equalsIgnoreCase(moduleName)) {
                module.toggle();
                Util.log(String.format("Toggled %s %s.", module.getName(), (module.isEnabled() ? "on" : "off")));
                return;
            }
        }

        Util.log("Module not found.");
    }

    @Override
    public List<String> getCompletions(final String[] arguments) {
        List<String> completions = new ArrayList<>();

        if (arguments.length == 2) {
            for (Module module : Client.instance.getModuleManager().getModuleMap().values()) {
                completions.add(module.getName().replace(" ", ""));
            }
        }

        return completions;
    }
}