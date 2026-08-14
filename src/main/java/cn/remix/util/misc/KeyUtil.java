package cn.remix.util.misc;

import lombok.experimental.UtilityClass;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Locale;

@UtilityClass
public final class KeyUtil {

    public int getKeyCode(String keyName) {
        try {
            String name = keyName.toUpperCase(Locale.ROOT);
            if (name.equals("NONE")) {
                return 0;
            }

            Field field = GLFW.class.getField("GLFW_KEY_" + name);
            return field.getInt(null);
        } catch (Exception exception) {
            return 0;
        }
    }

    public String getKeyName(int keyCode) {
        if (keyCode <= 0) {
            return "None";
        }

        int scancode = GLFW.glfwGetKeyScancode(keyCode);
        String name = GLFW.glfwGetKeyName(keyCode, scancode);

        if (name == null) {
            try {
                for (Field field : GLFW.class.getFields()) {
                    if (field.getName().startsWith("GLFW_KEY_") && field.getInt(null) == keyCode) {
                        return field.getName().substring(9);
                    }
                }
            } catch (Exception exception) {
                return "UNKNOWN";
            }
        }

        return name != null ? name.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }
}