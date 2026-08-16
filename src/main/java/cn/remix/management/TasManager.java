package cn.remix.management;

public final class TasManager {

    private static volatile float tasMultiplier = 1.0f;
    private static volatile float timerMultiplier = 1.0f;
    private static volatile boolean soundPitchEnabled = false;

    private TasManager() {
    }

    public static boolean isTasActive() {
        return tasMultiplier != 1.0f;
    }

    public static float getTasMultiplier() {
        return tasMultiplier;
    }

    public static void setTasMultiplier(float value) {
        tasMultiplier = value;
    }

    public static float getTimerMultiplier() {
        return timerMultiplier;
    }

    public static void setTimerMultiplier(float value) {
        timerMultiplier = value;
    }

    public static float getTickMultiplier() {
        return tasMultiplier * timerMultiplier;
    }

    public static boolean isTickRateActive() {
        return getTickMultiplier() != 1.0f;
    }

    public static boolean isSoundPitchEnabled() {
        return soundPitchEnabled;
    }

    public static void setSoundPitchEnabled(boolean enabled) {
        soundPitchEnabled = enabled;
    }

    public static float getSoundPitch() {
        return soundPitchEnabled ? tasMultiplier : 1.0f;
    }

    public static void resetTas() {
        tasMultiplier = 1.0f;
    }

    public static void resetTimer() {
        timerMultiplier = 1.0f;
    }

    public static boolean shouldRunAction(int actionTick) {
        if (!isTasActive()) {
            return true;
        }
        int interval = Math.max(1, Math.round(tasMultiplier));
        return actionTick % interval == 0;
    }
}