package cn.remix.module.value.impl;

import cn.remix.module.value.Value;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

@Getter
@Setter
public final class BoolValue extends Value {
    private boolean value;

    public BoolValue(String name, boolean value, Supplier<Boolean> visible) {
        super(name, visible);
        this.value = value;
    }

    public BoolValue(String name, boolean value) {
        this(name, value, () -> true);
    }

    public boolean getValue() {
        return this.value;
    }

    public void toggle() {
        this.value = !this.value;
    }
}