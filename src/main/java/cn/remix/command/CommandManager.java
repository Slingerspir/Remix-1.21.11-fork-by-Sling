package cn.remix.command;

import cn.remix.command.impl.BindCommand;
import cn.remix.command.impl.ConfigCommand;
import cn.remix.command.impl.EnemyCommand;
import cn.remix.command.impl.FriendCommand;
import cn.remix.command.impl.ToggleCommand;
import cn.remix.command.impl.VClipCommand;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.util.IMinecraft;
import cn.remix.util.Util;
import lombok.Getter;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public final class CommandManager implements IMinecraft {
    private final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        instance.getEventManager().register(this);

        addCommands(
                new ToggleCommand(),
                new BindCommand(),
                new FriendCommand(),
                new EnemyCommand(),
                new ConfigCommand(),
                new VClipCommand()
        );
    }

    public void addCommands(Command... commandsArray) {
        this.commands.addAll(Arrays.asList(commandsArray));
    }

    public List<String> getCompletions(String input) {
        List<String> suggestions = new ArrayList<>();
        String[] split = input.substring(".".length()).split(" ", -1);
        String label = split[0];

        if (split.length == 1) {
            for (Command command : commands) {
                for (String alias : command.getAliases()) {
                    if (alias.toLowerCase().startsWith(label.toLowerCase())) {
                        suggestions.add(alias);
                    }
                }
            }
        } else {
            for (Command command : commands) {
                for (String alias : command.getAliases()) {
                    if (alias.equalsIgnoreCase(label)) {
                        List<String> commandCompletions = command.getCompletions(split);
                        String currentArg = split[split.length - 1].toLowerCase();
                        if (commandCompletions != null) {
                            for (String s : commandCompletions) {
                                if (s.toLowerCase().startsWith(currentArg)) {
                                    suggestions.add(s);
                                }
                            }
                        }
                        return suggestions;
                    }
                }
            }
        }
        return suggestions;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == PacketEvent.Type.Send && event.getPacket() instanceof ChatMessageC2SPacket packet) {
            String message = packet.chatMessage();

            if (message.startsWith(".")) {
                event.setCancelled(true);
                String input = message.substring(".".length());

                if (input.isEmpty()) {
                    Util.log("No command entered.");
                    return;
                }

                String[] arguments = input.split(" ");
                String label = arguments[0];

                for (Command command : commands) {
                    for (String alias : command.getAliases()) {
                        if (alias.equalsIgnoreCase(label)) {
                            try {
                                command.execute(arguments);
                            } catch (Exception exception) {
                                Util.log("Execution error: " + exception.getMessage());
                            }
                            return;
                        }
                    }
                }
                Util.log(String.format("'%s' is not a command.", label));
            }
        }
    }
}
