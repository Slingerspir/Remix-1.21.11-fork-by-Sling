package cn.remix.notification;

import cn.remix.module.Module;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class NotificationManager {
    private static final List<NotificationEntry> ENTRIES = new LinkedList<>();

    private NotificationManager() {
    }

    public static void success(String message) {
        push(Type.SUCCESS, message);
    }

    public static void error(String message) {
        push(Type.ERROR, message);
    }

    public static void info(String message) {
        push(Type.INFO, message);
    }

    public static void warning(String message) {
        push(Type.WARNING, message);
    }

    public static void module(String message, boolean enabled) {
        push(enabled ? Type.MODULE_ENABLE : Type.MODULE_DISABLE, message, null, enabled);
    }

    public static void module(Module module, boolean enabled) {
        module(module, enabled, module.getName() + (enabled ? " Enabled" : " Disabled"));
    }

    public static void module(Module module, boolean enabled, String message) {
        push(enabled ? Type.MODULE_ENABLE : Type.MODULE_DISABLE, message, module, enabled);
    }

    public static void push(Type type, String message) {
        push(type, message, null, false);
    }

    private static void push(Type type, String message, Module module, boolean enabled) {
        if (message == null || message.isBlank()) {
            return;
        }
        ENTRIES.add(0, new NotificationEntry(type, message, module, enabled));
    }

    public static List<NotificationEntry> entries() {
        return ENTRIES;
    }

    public static void prune(long durationMillis) {
        long now = System.currentTimeMillis();
        Iterator<NotificationEntry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            NotificationEntry entry = iterator.next();
            if (entry.isDead(now, durationMillis)) {
                iterator.remove();
            }
        }
    }

    public enum Type {
        SUCCESS("Success", new Color(24, 104, 68).getRGB()),
        ERROR("Error", new Color(132, 39, 45).getRGB()),
        INFO("Message", new Color(36, 139, 181).getRGB()),
        WARNING("Warning", new Color(181, 105, 34).getRGB()),
        MODULE_ENABLE("Module", new Color(24, 104, 68).getRGB()),
        MODULE_DISABLE("Module", new Color(132, 39, 45).getRGB());

        @Getter
        private final String label;
        @Getter
        private final int color;

        Type(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    @Getter
    public static final class NotificationEntry {
        private final Type type;
        private final String message;
        private final String moduleName;
        private final String categoryName;
        private final boolean enabled;
        @Setter
        private BeautifulState beautifulState;
        private final long createdAt = System.currentTimeMillis();
        private final EasingAnimation xAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 260);
        private final EasingAnimation yAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 260);

        private float x;
        private float y;
        private boolean positioned;

        private NotificationEntry(Type type, String message) {
            this(type, message, null, false);
        }

        private NotificationEntry(Type type, String message, Module module, boolean enabled) {
            this.type = type;
            this.message = message;
            this.moduleName = module == null ? null : module.getName();
            this.categoryName = module == null ? null : module.getCategory().getName();
            this.enabled = module != null && enabled;
        }

        public float progress(long now, long durationMillis) {
            if (durationMillis <= 0) {
                return 1.0f;
            }
            return MathHelper.clamp((now - createdAt) / (float) durationMillis, 0.0f, 1.0f);
        }

        public float alpha(long now, long durationMillis) {
            float progress = progress(now, durationMillis);
            if (progress < 0.1f) {
                return MathHelper.clamp(progress / 0.1f, 0.0f, 1.0f);
            }
            if (progress > 0.82f) {
                return MathHelper.clamp((1.0f - progress) / 0.18f, 0.0f, 1.0f);
            }
            return 1.0f;
        }

        public void setDestination(float targetX, float targetY) {
            if (!positioned) {
                xAnimation.setValue(targetX);
                xAnimation.setStartValue(targetX);
                xAnimation.setDestinationValue(targetX);
                yAnimation.setValue(targetY);
                yAnimation.setStartValue(targetY);
                yAnimation.setDestinationValue(targetY);
                x = targetX;
                y = targetY;
                positioned = true;
            }
            xAnimation.run(targetX);
            yAnimation.run(targetY);
            x = xAnimation.getValue().floatValue();
            y = yAnimation.getValue().floatValue();
        }

        private boolean isDead(long now, long durationMillis) {
            return now - createdAt > durationMillis + 500L;
        }
    }
}
