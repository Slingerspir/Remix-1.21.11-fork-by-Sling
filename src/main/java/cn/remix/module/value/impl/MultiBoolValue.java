package cn.remix.module.value.impl;

import cn.remix.module.value.Value;
import lombok.Getter;

import java.util.List;
import java.util.function.Supplier;

@Getter
public final class MultiBoolValue extends Value {
    private final List<BoolValue> values;

    public MultiBoolValue(String name, Supplier<Boolean> visible, BoolValue... values) {
        super(name, visible);
        this.values = List.of(values);
    }

    public MultiBoolValue(String name, BoolValue... values) {
        this(name, () -> true, values);
    }

    public boolean isEnabled(String name) {
        return this.values.stream()
                .anyMatch(bool -> bool.getName().equalsIgnoreCase(name) && bool.getValue());
    }

    public void setValue(String name, boolean state) {
        this.values.stream()
                .filter(bool -> bool.getName().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(bool -> bool.setValue(state));
    }
}