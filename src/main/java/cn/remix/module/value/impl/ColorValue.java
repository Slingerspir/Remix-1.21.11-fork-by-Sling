package cn.remix.module.value.impl;

import cn.remix.module.value.Value;
import lombok.Getter;

import java.awt.*;
import java.util.function.Supplier;

@Getter
public final class ColorValue extends Value {
    private float hue = 0.0F;
    private float saturation = 1.0F;
    private float brightness = 1.0F;

    public ColorValue(String name, Color color, Supplier<Boolean> visible) {
        super(name, visible);
        this.setValue(color);
    }

    public ColorValue(String name, Color color) {
        this(name, color, () -> true);
    }

    public Color getValue() {
        return Color.getHSBColor(this.hue, this.saturation, this.brightness);
    }

    public void setValue(Color color) {
        var hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public void setHSB(float h, float s, float b) {
        this.hue = h;
        this.saturation = s;
        this.brightness = b;
    }
}