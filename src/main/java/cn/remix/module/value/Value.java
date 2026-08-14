package cn.remix.module.value;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Value {
    private final String name;
    private final Supplier<Boolean> visible;

    public final boolean isVisible() {
        return Boolean.TRUE.equals(this.visible.get());
    }
}