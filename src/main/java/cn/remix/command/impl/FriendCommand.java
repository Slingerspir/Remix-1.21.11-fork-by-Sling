package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.management.FriendManager;
import cn.remix.util.Util;

import java.util.List;

public final class FriendCommand extends Command {
    public FriendCommand() {
        super(".friend <add/list/remove> <name>", "friend");
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
                boolean added = manager.addFriend(name);
                Util.log(added ? "Added friend: " + name : name + " is already a friend.");
            }
            case "list" -> {
                if (FriendManager.getFriends().isEmpty()) {
                    Util.log("Friend list is empty.");
                    return;
                }
                Util.log("Friends: " + String.join(", ", FriendManager.getFriends()));
            }
            case "remove" -> {
                if (arguments.length < 3) {
                    Util.log(getUsage());
                    return;
                }
                String name = arguments[2];
                boolean removed = manager.removeFriend(name);
                Util.log(removed ? "Removed friend: " + name : name + " is not a friend.");
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
            return FriendManager.getFriends();
        }
        return List.of();
    }
}
