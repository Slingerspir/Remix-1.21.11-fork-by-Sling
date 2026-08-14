package cn.remix.util.misc;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public class MathUtil {
    private final SecureRandom random = new SecureRandom();

    public float getRandomInRange(float min, float max) {
        return random.nextFloat() * (max - min) + min;
    }

    public long getRandomInRange(long min, long max) {
        return (long) (random.nextFloat() * (max - min) + min);
    }
}