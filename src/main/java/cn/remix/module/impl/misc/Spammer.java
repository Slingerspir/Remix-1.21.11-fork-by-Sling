package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;

public final class Spammer extends Module {
    private final NumberValue delay = new NumberValue("Delay", 3000, 100, 15000, 100);
    private final ModeValue prefix = new ModeValue("Prefix", "None", "None", "@", "/shout ");
    private final ModeValue message = new ModeValue("Message", "Remix is the best!",
            "Remix is the best!", "gg", "hello everyone", "L");

    private final TimerUtil timer = new TimerUtil();

    public Spammer() {
        super("Spammer", Category.Misc);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;

        String p = prefix.is("None") ? "" : prefix.getValue();
        mc.getNetworkHandler().sendChatMessage(p + message.getValue());
        timer.reset();
    }
}
