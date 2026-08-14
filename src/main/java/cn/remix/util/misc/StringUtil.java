package cn.remix.util.misc;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtil {
    private final int[] colorCodes = new int[32];

    static {
        for (int index = 0; index < 32; ++index) {
            int base = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + base;
            int green = (index >> 1 & 1) * 170 + base;
            int blue = (index & 1) * 170 + base;

            if (index == 6) {
                red += 85;
            }

            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            colorCodes[index] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        }
    }

    public int parseColorCode(char codeChar, int fallbackColor) {
        char lowerCode = Character.toLowerCase(codeChar);
        int codeIndex = "0123456789abcdef".indexOf(lowerCode);

        if (codeIndex != -1) {
            return colorCodes[codeIndex] | 0xFF000000;
        } else if (lowerCode == 'r') {
            return 0xFFFFFFFF;
        }
        return fallbackColor;
    }
}