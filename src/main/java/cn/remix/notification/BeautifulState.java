package cn.remix.notification;

public final class BeautifulState {
    public enum Phase {
        OPEN,
        ERASE,
        HOLD,
        COVER,
        CONTRACT,
        LAST_ERASE,
        DONE
    }

    public Phase phase = Phase.OPEN;
    public long phaseStart = System.currentTimeMillis();
}
