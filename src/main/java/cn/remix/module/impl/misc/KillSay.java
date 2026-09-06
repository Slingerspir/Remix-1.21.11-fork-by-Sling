package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class KillSay extends Module {
    private final NumberValue delay = new NumberValue("Delay", 2000, 0, 15000, 100);
    private final ModeValue prefix = new ModeValue("Prefix", "None", "None", "@", "!", "/shout ");

    private final Deque<String> queue = new ArrayDeque<>();
    private final java.util.Map<java.util.UUID, String> attacked = new java.util.HashMap<>();
    private final TimerUtil timer = new TimerUtil();
    private final String[] messages = {
            "gg %s!", "nice fight %s", "ez %s", "rekt %s", "good game %s"
    };

    public KillSay() {
        super("KillSay", Category.Misc);
    }

    @Override
    public void onEnable() {
        queue.clear();
        attacked.clear();
        timer.reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getEntity() instanceof PlayerEntity player) {
            attacked.put(player.getUuid(), player.getName().getString());
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received) return;
        if (event.getPacket() instanceof PlayerRemoveS2CPacket packet) {
            for (java.util.UUID uuid : packet.profileIds()) {
                String name = attacked.remove(uuid);
                if (name == null) continue;

                String p = switch (prefix.getValue()) {
                    case "@" -> "@";
                    case "!" -> "!";
                    case "/shout " -> "/shout ";
                    default -> "";
                };
                queue.add(p + messages[(int) (Math.random() * messages.length)].formatted(name));
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;
        if (queue.isEmpty()) return;

        mc.getNetworkHandler().sendChatMessage(queue.poll());
        timer.reset();
    }
}
