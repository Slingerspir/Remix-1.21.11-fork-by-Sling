package cn.remix.util.misc;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MouseUtil {

    public boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
