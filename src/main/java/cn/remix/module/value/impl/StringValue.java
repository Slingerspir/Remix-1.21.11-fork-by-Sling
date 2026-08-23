package cn.remix.module.value.impl;

import cn.remix.module.value.Value;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

@Getter
@Setter
public final class StringValue extends Value {
    private String value;

    public StringValue(String name, String value, Supplier<Boolean> visible) {
        super(name, visible);
        this.value = value;
    }

    public StringValue(String name, String value) {
        this(name, value, () -> true);
    }
}
