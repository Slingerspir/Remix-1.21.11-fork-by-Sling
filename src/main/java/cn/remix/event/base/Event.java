package cn.remix.event.base;

import lombok.Getter;

@Getter
public class Event {
    private boolean cancelled;

    public void setCancelled() {
        this.cancelled = true;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Getter
    public static class StateEvent extends Event {
        private boolean pre = true;

        public boolean isPost() {
            return !pre;
        }

        public void setPost() {
            pre = false;
        }
    }
}
