package cn.remix.command;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class Command {
    private final String[] aliases;
    private final String usage;

    public Command(String usage, String... aliases) {
        this.usage = usage;
        this.aliases = aliases;
    }

    public abstract void execute(final String[] arguments);

    public List<String> getCompletions(final String[] arguments) {
        return new ArrayList<>();
    }
}