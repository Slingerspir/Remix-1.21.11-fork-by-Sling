package cn.remix.config.impl;

import cn.remix.Client;
import cn.remix.config.Config;
import cn.remix.module.Module;
import cn.remix.module.value.Value;
import cn.remix.module.value.impl.*;
import cn.remix.ui.hud.Drag;
import com.google.gson.*;

import java.io.FileReader;
import java.io.PrintWriter;

public final class ModuleConfig extends Config {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ModuleConfig() {
        this("Default");
    }

    public ModuleConfig(final String name) {
        super(name);
    }

    @Override
    public void save() {
        try {
            final JsonObject jsonObject = new JsonObject();

            for (Module module : instance.getModuleManager().getModuleMap().values()) {
                final JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("enabled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());

                if (module instanceof Drag drag) {
                    moduleObject.addProperty("percentX", drag.percentX);
                    moduleObject.addProperty("percentY", drag.percentY);
                }

                if (!module.getValues().isEmpty()) {
                    moduleObject.add("values", this.serializeValues(module));
                }
                jsonObject.add(module.getName(), moduleObject);
            }

            try (final PrintWriter writer = new PrintWriter(this.getFile())) {
                writer.println(this.gson.toJson(jsonObject));
            }
        } catch (final Exception exception) {
            Client.logger.debug("Failed to save config: {}. Error: {}", this.getName(), exception.getMessage());
        }
    }

    @Override
    public void load() {
        if (!this.getFile().exists()) {
            return;
        }

        try (final FileReader fileReader = new FileReader(this.getFile())) {
            final JsonElement jsonElement = JsonParser.parseReader(fileReader);
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                return;
            }
            final JsonObject jsonObject = jsonElement.getAsJsonObject();

            for (final Module module : instance.getModuleManager().getModuleMap().values()) {
                if (!jsonObject.has(module.getName())) {
                    continue;
                }

                final JsonObject moduleObject = jsonObject.getAsJsonObject(module.getName());
                this.deserializeModule(module, moduleObject);
            }
        } catch (final Exception exception) {
            Client.logger.debug("Failed to load config: {}. Error: {}", this.getName(), exception.getMessage());
        }
    }

    private JsonObject serializeValues(final Module module) {
        final JsonObject valuesObject = new JsonObject();
        for (final Value value : module.getValues()) {
            switch (value) {
                case BoolValue bool -> valuesObject.addProperty(bool.getName(), bool.getValue());
                case NumberValue num -> valuesObject.addProperty(num.getName(), num.getValue());
                case ModeValue mode -> valuesObject.addProperty(mode.getName(), mode.getValue());
                case StringValue str -> valuesObject.addProperty(str.getName(), str.getValue());
                case ColorValue color -> valuesObject.addProperty(color.getName(), color.getValue().getRGB());
                case MultiBoolValue multi -> {
                    final JsonObject multiObject = new JsonObject();
                    for (final BoolValue child : multi.getValues()) {
                        multiObject.addProperty(child.getName(), child.getValue());
                    }
                    valuesObject.add(multi.getName(), multiObject);
                }
                default -> {}
            }
        }
        return valuesObject;
    }

    private void deserializeModule(final Module module, final JsonObject moduleObject) {
        if (moduleObject.has("enabled")) {
            final boolean shouldEnable = moduleObject.get("enabled").getAsBoolean();
            if (shouldEnable != module.isEnabled()) {
                module.toggle();
            }
        }

        if (moduleObject.has("key")) {
            module.setKey(moduleObject.get("key").getAsInt());
        }

        if (module instanceof Drag drag) {
            if (moduleObject.has("percentX")) {
                drag.percentX = moduleObject.get("percentX").getAsFloat();
            }
            if (moduleObject.has("percentY")) {
                drag.percentY = moduleObject.get("percentY").getAsFloat();
            }
        }

        if (moduleObject.has("values") && !module.getValues().isEmpty()) {
            final JsonObject valuesObject = moduleObject.getAsJsonObject("values");
            for (final Value value : module.getValues()) {
                if (!valuesObject.has(value.getName())) {
                    continue;
                }
                final JsonElement element = valuesObject.get(value.getName());
                try {
                    switch (value) {
                        case BoolValue bool -> bool.setValue(element.getAsBoolean());
                        case NumberValue num -> num.setValue(element.getAsFloat());
                        case ModeValue mode -> mode.setValue(element.getAsString());
                        case StringValue str -> str.setValue(element.getAsString());
                        case ColorValue color -> color.setValue(new java.awt.Color(element.getAsInt()));
                        case MultiBoolValue multi when element.isJsonObject() -> {
                            final JsonObject multiObject = element.getAsJsonObject();
                            for (final BoolValue child : multi.getValues()) {
                                if (multiObject.has(child.getName())) {
                                    child.setValue(multiObject.get(child.getName()).getAsBoolean());
                                }
                            }
                        }
                        default -> {}
                    }
                } catch (final Exception exception) {
                    Client.logger.debug("Failed to load value {}: {}", value.getName(), exception.getMessage());
                }
            }
        }
    }
}
