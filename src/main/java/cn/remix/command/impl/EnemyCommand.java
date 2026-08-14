package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.management.FriendManager;
import cn.remix.util.Util;

import java.util.List;

public final class EnemyCommand extends Command {
    public EnemyCommand() {
        super(".enemy <add/list/remove> <name>", "enemy");
    }

    @Override
    public void execute(String[] arguments) {
        FriendManager manager = Client.instance.getFriendManager();
        if (arguments.length < 2) {
            Util.log(getUsage());
            return;
        }

        switch (arguments[1].toLowerCase()) {
            case "add" -> {
                if (arguments.length < 3) {
                    Util.log(getUsage());
                    return;
                }
                String name = arguments[2];
                boolean added = manager.addEnemy(name);
                Util.log(added ? "Added enemy: " + name : name + " is already an enemy.");
            }
            case "list" -> {
                if (FriendManager.getEnemies().isEmpty()) {
                    Util.log("Enemy list is empty.");
                    return;
                }
                Util.log("Enemies: " + String.join(", ", FriendManager.getEnemies()));
            }
            case "remove" -> {
                if (arguments.length < 3) {
                    Util.log(getUsage());
                    return;
                }
                String name = arguments[2];
                boolean removed = manager.removeEnemy(name);
                Util.log(removed ? "Removed enemy: " + name : name + " is not an enemy.");
            }
            default -> Util.log(getUsage());
        }
    }

    @Override
    public List<String> getCompletions(String[] arguments) {
        if (arguments.length == 2) {
            return List.of("add", "list", "remove");
        }
        if (arguments.length == 3 && "remove".equalsIgnoreCase(arguments[1])) {
            return FriendManager.getEnemies();
        }
        return List.of();
    }
}
