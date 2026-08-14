package cn.remix.config;

import cn.remix.Client;
import cn.remix.util.IMinecraft;
import lombok.Getter;

import java.io.File;

@Getter
public abstract class Config implements IMinecraft {
    private final String name;
    private final File file;

    public Config(final String name) {
        final File directory = new File(Client.name, "configs");

        if (!directory.exists() && !directory.mkdirs()) {
            Client.logger.debug("Failed to create configs directory: {}", directory.getPath());
        }

        this.name = name;
        this.file = new File(directory, name.toLowerCase() + ".json");
    }

    public abstract void save();
    public abstract void load();
}