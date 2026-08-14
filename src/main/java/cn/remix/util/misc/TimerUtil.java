package cn.remix.util.misc;

import lombok.Setter;

@Setter
public class TimerUtil {
    public long time;

    public TimerUtil() {
        reset();
    }

    public void reset() {
        time = System.currentTimeMillis();
    }

    public boolean finished(long delay) {
        return System.currentTimeMillis() - delay >= time;
    }

    public boolean hasTimeElapsed(long time) {
        return System.currentTimeMillis() - this.time > time;
    }

    public boolean hasTimeElapsed(double time) {
        return hasTimeElapsed((long) time);
    }

    public long getTime() {
        return System.currentTimeMillis() - time;
    }

}
