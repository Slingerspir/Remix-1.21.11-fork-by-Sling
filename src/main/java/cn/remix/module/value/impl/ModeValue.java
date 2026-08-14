package cn.remix.module.value.impl;

import cn.remix.module.value.Value;
import lombok.Getter;

import java.util.Arrays;
import java.util.function.Supplier;

@Getter
public final class ModeValue extends Value {
    private final String[] modes;
    private String value;

    public ModeValue(String name, String defaultValue, Supplier<Boolean> visible, String... modes) {
        super(name, visible);
        this.modes = modes;
        this.value = defaultValue;
    }

    public ModeValue(String name, String defaultValue, String... modes) {
        this(name, defaultValue, () -> true, modes);
    }

    public boolean is(String mode) {
        return this.value.equalsIgnoreCase(mode);
    }

    public void setValue(String mode) {
        Arrays.stream(this.modes)
                .filter(m -> m.equalsIgnoreCase(mode))
                .findFirst()
                .ifPresent(m -> this.value = m);
    }
}