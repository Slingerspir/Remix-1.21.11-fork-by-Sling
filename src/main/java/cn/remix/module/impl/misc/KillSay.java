package cn.remix.module.impl.misc;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.module.value.impl.StringValue;
import cn.remix.util.misc.TimerUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KillSay —— 击杀后在公屏喊话。
 *
 * <p>消息库放在 {@code Remix/killsay.txt}（与 configs 同级），一行一条、随机抽，
 * 支持占位符；文件不存在会自动生成带说明的默认文件，改完保存游戏内会**自动重读**。</p>
 *
 * <p>死亡判定用 {@link EntityStatusS2CPacket} 的 status 3（死亡音效/动画），这是服务端
 * 在玩家死亡时发的包。原先监听的是 {@code PlayerRemoveS2CPacket}（玩家从客户端视野移除），
 * 那个只要对方走远、退出服务器、切维度就会触发，会疯狂误报。</p>
 */
public final class KillSay extends Module {
    private final NumberValue delay = new NumberValue("Delay", 2000, 0, 15000, 100);
    /** 前缀：想填什么填什么，留空即无前缀；会自动补一个空格 */
    private final StringValue prefix = new StringValue("Prefix", "");
    /** 开 = 每条随机抽；关 = 按文件里的顺序轮流发 */
    private final BoolValue random = new BoolValue("Random", true);

    /** 打过之后多久内对方死亡才算自己的击杀 */
    private static final long KILL_WINDOW_MS = 30_000L;

    private static final List<String> DEFAULT_MESSAGES = List.of(
            "gg {Player}!", "nice fight {Player}", "ez {Player}", "rekt {Player}", "good game {Player}");

    private static final String DEFAULT_FILE = """
            # KillSay 击杀消息库
            # 一行一条，击杀后抽一条发出去。以 # 开头是注释，空行会被忽略。
            # 随机抽还是按顺序轮流发，由模块里的 Random 开关决定。
            #
            # 可用占位符：
            #   {Player}  被击杀玩家的名字
            #   {Me}      自己的名字
            #   {Kills}   本次会话累计击杀数
            #   {HP}      自己当前血量（整数）
            #   {Item}    主手物品名字
            #   {Server}  当前服务器地址（单机显示"单机"）
            #   %s        等价于 {Player}，兼容旧写法
            #
            # 改完直接保存即可，游戏内每秒检查一次，会自动重新读取，不用重启。
            #
            gg {Player}!
            nice fight {Player}
            ez {Player}
            rekt {Player}
            good game {Player}
            gg ez {Player}
            {Player} down
            rip {Player}
            too easy {Player}
            better luck next time {Player}
            {Player} eliminated
            skill issue {Player}
            kill #{Kills} - {Player}
            {Item} says hi to {Player}
            {Player} 下次别来了
            这波不亏，{Player} 先走一步
            {Player} 又送一个，现在 {Kills} 杀了
            """;

    private final Deque<String> queue = new ArrayDeque<>();
    private final Map<UUID, Attack> attacked = new HashMap<>();
    private final TimerUtil timer = new TimerUtil();

    private final List<String> messages = new ArrayList<>(DEFAULT_MESSAGES);
    private File messageFile;
    private long fileStamp = -1L;
    private int fileCheckTicks;
    private int nextIndex;
    private String lastSent;
    private int kills;

    private record Attack(String name, long at) {
    }

    public KillSay() {
        super("KillSay", Category.Misc);
    }

    @Override
    public void onEnable() {
        queue.clear();
        attacked.clear();
        kills = 0;
        timer.reset();
        fileStamp = -1L;
        fileCheckTicks = 0;
        nextIndex = 0;
        lastSent = null;
        reloadIfChanged();
    }

    // =====================================================================
    // 击杀判定
    // =====================================================================

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity player)) return;
        if (player == mc.player) return;
        attacked.put(player.getUuid(), new Attack(player.getName().getString(), System.currentTimeMillis()));
        purgeAttacked();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received) return;
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        // 玩家死亡时 ServerPlayerEntity.onDeath 会广播 PLAY_DEATH_SOUND(3)；
        // 另外兜底接受 ADD_DEATH_PARTICLES(60)，两个都只当"候选事件"，真正的判定靠下面的攻击记录
        byte status = packet.getStatus();
        if (status != EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES
                && status != EntityStatuses.ADD_DEATH_PARTICLES) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player)) return;
        if (player == mc.player) return;   // 自己死了不喊

        Attack attack = attacked.remove(player.getUuid());
        if (attack == null) return;        // 没打过他，不是我们的击杀
        if (System.currentTimeMillis() - attack.at() > KILL_WINDOW_MS) return;   // 打完好久了，不算

        kills++;
        queue.add(prefixText() + applyPlaceholders(pickMessage(), attack.name()));
    }

    private void purgeAttacked() {
        long now = System.currentTimeMillis();
        attacked.entrySet().removeIf(entry -> now - entry.getValue().at() > KILL_WINDOW_MS);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // 约每秒查一次文件有没有被改过，别每 tick 都去 stat
        if (++fileCheckTicks >= 20) {
            fileCheckTicks = 0;
            reloadIfChanged();
        }

        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;
        if (queue.isEmpty()) return;

        mc.getNetworkHandler().sendChatMessage(queue.poll());
        timer.reset();
    }

    // =====================================================================
    // 消息库：Remix/killsay.txt
    // =====================================================================

    private File messageFile() {
        if (messageFile == null) messageFile = new File(Client.name, "killsay.txt");
        return messageFile;
    }

    /** 文件被改动过就重读；不存在就先写一份带说明的默认文件。 */
    private void reloadIfChanged() {
        try {
            File file = messageFile();
            if (!file.exists()) {
                if (file.getParentFile() != null && !file.getParentFile().exists()) file.getParentFile().mkdirs();
                Files.write(file.toPath(), DEFAULT_FILE.getBytes(StandardCharsets.UTF_8));
            }
            long stamp = file.isFile() ? file.lastModified() : 0L;
            if (stamp == fileStamp) return;
            fileStamp = stamp;

            List<String> loaded = new ArrayList<>();
            for (String raw : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                loaded.add(line);
            }
            if (!loaded.isEmpty()) {
                messages.clear();
                messages.addAll(loaded);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 取一条消息：Random 开着就随机抽（并避开和上一条重复），关掉就按文件里的顺序轮流发。
     */
    private String pickMessage() {
        if (messages.isEmpty()) return DEFAULT_MESSAGES.get(0);

        if (!random.getValue()) {
            String msg = messages.get(Math.floorMod(nextIndex, messages.size()));
            nextIndex = Math.floorMod(nextIndex + 1, messages.size());
            lastSent = msg;
            return msg;
        }

        String pick = messages.get((int) (Math.random() * messages.size()));
        // 连着两条一样很出戏，撞上了就顺延到下一条
        if (messages.size() > 1 && pick.equals(lastSent)) {
            pick = messages.get((messages.indexOf(pick) + 1) % messages.size());
        }
        lastSent = pick;
        return pick;
    }

    /** 用户填的前缀；留空或写成 None 视为无前缀，非空会自动补一个空格。 */
    private String prefixText() {
        String raw = prefix.getValue() == null ? "" : prefix.getValue();
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) return "";
        return trimmed.endsWith(" ") ? raw : trimmed + " ";
    }

    private String applyPlaceholders(String template, String victim) {
        String out = template;
        out = out.replace("{Player}", victim).replace("{player}", victim).replace("%s", victim);
        out = out.replace("{Me}", mc.player != null ? mc.player.getName().getString() : "");
        out = out.replace("{Kills}", String.valueOf(kills));
        out = out.replace("{HP}", mc.player != null ? String.valueOf((int) Math.ceil(mc.player.getHealth())) : "?");
        out = out.replace("{Item}", mainHandName());
        out = out.replace("{Server}", serverAddress());
        return out;
    }

    private String mainHandName() {
        if (mc.player == null) return "";
        ItemStack stack = mc.player.getMainHandStack();
        return stack.isEmpty() ? "空手" : stack.getName().getString();
    }

    private String serverAddress() {
        try {
            if (mc.getCurrentServerEntry() != null) return mc.getCurrentServerEntry().address;
        } catch (Throwable ignored) {
        }
        return "单机";
    }
}
