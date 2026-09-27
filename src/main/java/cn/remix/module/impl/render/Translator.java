package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中文翻译模块。
 *
 * <p>开启后，ClickGUI、ModuleList、TabGUI、通知等界面上的模块名、设置项名称与分类名
 * 都会以中文显示；关闭后立即恢复英文原名。</p>
 *
 * <p>注意：本模块只影响“显示”，不会改动 {@link Module#getName()} 等真实字段。
 * 配置文件（config/Default.json）与聊天命令（{@code .toggle Aura}）仍然使用英文原名，
 * 因此切换语言不会破坏已有配置，也不会让命令失效。</p>
 */
public final class Translator extends Module {

    /** 翻译总开关。 */
    private final BoolValue translateModules = new BoolValue("Translate Modules", true);
    /** 是否翻译设置项（Bool / Number / Mode / Color / MultiBool）名称。 */
    private final BoolValue translateValues = new BoolValue("Translate Values", true);
    /** 是否翻译分类名称（Combat / Move / ...）。 */
    private final BoolValue translateCategory = new BoolValue("Translate Category", true);
    /** 是否把 ModuleList 的驼峰名字拆成单词后再翻译（关闭则按原样查表）。 */
    private final BoolValue splitCamelCase = new BoolValue("Split Camel Case", true);

    public Translator() {
        super("Translator", Category.Render);
        setEnabled(false);
    }

    /**
     * 以“显示名”为键同步一次样式后缀，方便在 ModuleList 右侧直接看到当前语言状态。
     */
    @Override
    public void onEnable() {
        setSuffix("中文");
    }

    @Override
    public void onDisable() {
        setSuffix("");
    }

    // ------------------------------------------------------------------
    // 静态查询接口
    // ------------------------------------------------------------------

    private static Translator instance() {
        if (instance == null || instance.getModuleManager() == null) {
            return null;
        }
        return instance.getModuleManager().getModule(Translator.class);
    }

    /** 翻译是否处于开启状态。任何渲染热路径都可以安全调用。 */
    public static boolean active() {
        Translator translator = instance();
        return translator != null && translator.isEnabled();
    }

    private static boolean activeValues() {
        Translator translator = instance();
        return translator != null && translator.isEnabled() && translator.translateValues.getValue();
    }

    private static boolean activeModules() {
        Translator translator = instance();
        return translator != null && translator.isEnabled() && translator.translateModules.getValue();
    }

    private static boolean activeCategory() {
        Translator translator = instance();
        return translator != null && translator.isEnabled() && translator.translateCategory.getValue();
    }

    private static boolean camelSplit() {
        Translator translator = instance();
        return translator == null || translator.splitCamelCase.getValue();
    }

    /**
     * 翻译模块显示名（同时负责把 ClassName 风格的名字拆成单词）。
     *
     * @param name 模块原始名称，例如 {@code NoSlowDown}
     * @return 翻译开启且命中词条时返回中文，否则返回{@code name} 本身
     */
    public static String module(String name) {
        if (name == null || name.isEmpty()) return name;
        if (!activeModules()) return name;

        String key = camelSplit() ? splitCamelCase(name) : name;
        String translated = MODULES.get(key);
        if (translated == null) {
            translated = MODULES.get(name);
        }
        return translated == null ? key : translated;
    }

    /**
     * 翻译设置项显示名。设置项名称本身就是空格分隔的英文，所以直接查表。
     *
     * @param name 设置项原始名称，例如 {@code Attack Range}
     */
    public static String value(String name) {
        if (name == null || name.isEmpty()) return name;
        if (!activeValues()) return name;

        String translated = VALUES.get(name);
        return translated == null ? name : translated;
    }

    /** 翻译分类显示名。 */
    public static String category(String name) {
        if (name == null || name.isEmpty()) return name;
        if (!activeCategory()) return name;

        String translated = CATEGORIES.get(name);
        return translated == null ? name : translated;
    }

    /** ClickGUI 里少量散装文案的翻译（Bind、Enabled 等）。 */
    public static String text(String literal) {
        if (literal == null || literal.isEmpty()) return literal;
        if (!active()) return literal;

        String translated = MISC.get(literal);
        return translated == null ? literal : translated;
    }

    /**
     * 把 {@code NoSlowDown} / {@code TpAuraPlus} / {@code HUD} 这类名字拆成
     * {@code No Slow Down} / {@code Tp Aura Plus} / {@code HUD}。
     *
     * <p>与 {@code ModuleList} 中原本使用的正则保持一致，只是补上了“全大写缩写不拆分”的处理。</p>
     */
    public static String splitCamelCase(String name) {
        if (name == null || name.isEmpty()) return name;
        // 全大写（HUD / ESP / TAS / MCF / VClip 之外的情况）原样返回
        if (name.equals(name.toUpperCase())) return name;
        return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
    }

    // ------------------------------------------------------------------
    // 词表
    // ------------------------------------------------------------------

    private static final Map<String, String> MODULES = new LinkedHashMap<>();
    private static final Map<String, String> VALUES = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, String> MISC = new LinkedHashMap<>();

    private static void m(String en, String zh) { MODULES.put(en, zh); }
    private static void v(String en, String zh) { VALUES.put(en, zh); }
    private static void c(String en, String zh) { CATEGORIES.put(en, zh); }
    private static void x(String en, String zh) { MISC.put(en, zh); }

    static {
        // ---------- 分类 ----------
        c("Combat", "战斗");
        c("Exploits", "漏洞");
        c("Exploit", "漏洞");
        c("Move", "移动");
        c("Movement", "移动");
        c("Player", "玩家");
        c("World", "世界");
        c("Misc", "杂项");
        c("Render", "渲染");

        // ---------- 模块名 ----------
        m("Action Recorder", "操作录制");
        m("Aim Assist", "瞄准辅助");
        m("Air Jump", "空中跳跃");
        m("Animation", "动画");
        m("Anti Blindness", "免疫失明");
        m("Anti Bot", "反机器人");
        m("Anti Cheat Detect", "反作弊检测");
        m("Anti Fireball", "反火球");
        m("Anti Hunger", "反饥饿");
        m("Anti Lava", "防岩浆");
        m("Anti Nausea", "免疫反胃");
        m("Anti Void", "防虚空");
        m("Aura", "杀戮光环");
        m("Auto Armor", "自动穿甲");
        m("Auto Clicker", "自动点击");
        m("Auto Gapple", "自动金苹果");
        m("Auto Heal", "自动治疗");
        m("Auto Mace", "自动重锤");
        m("Auto Tool", "自动选工具");
        m("Backtrack", "回溯");
        m("Better Chat", "聊天增强");
        m("Better Tab", "玩家列表增强");
        m("Blink", "闪现");
        m("Block Selection", "方块选择");
        m("Brightness", "亮度");
        m("Button Renderer", "按键显示");
        m("Chest ESP", "箱子透视");
        m("Chest Gui", "箱子界面");
        m("Chest Stealer", "自动偷箱");
        m("Click Gui", "点击界面");
        m("Click TP", "点击传送");
        m("Client Spoof", "客户端伪装");
        m("Clip HUD", "卡墙提示");
        m("Clipper", "卡墙");
        m("Clutch", "救场");
        m("Compass", "罗盘");
        m("Criticals", "刀刀暴击");
        m("Crosshair", "准星");
        m("Crystal Aura", "水晶光环");
        m("Damage Tint", "受伤染色");
        m("Derp", "摇头");
        m("Disabler", "反检测");
        m("Dynamic Island", "灵动岛");
        m("EC Disabler", "末影箱反检测");
        m("EC Fly", "末影箱飞行");
        m("Effect Display", "药水显示");
        m("ESP", "透视");
        m("Fast Use", "快速使用");
        m("Fast Web", "快速破坏蛛网");
        m("Fly", "飞行");
        m("Fly Plus", "飞行增强");
        m("Fullbright", "夜视");
        m("Ghost Hand", "幽灵手");
        m("Glow", "发光");
        m("Glow ESP", "发光透视");
        m("Gui Move", "界面行走");
        m("HUD", "界面");
        m("Indicator", "指示器");
        m("Inventory Manager", "背包管理");
        m("Item Physics", "物品物理");
        m("Item Tags", "物品标签");
        m("Keep Sprint", "保持疾跑");
        m("Kill Effects", "击杀特效");
        m("Kill Say", "击杀喊话");
        m("Legit No Fall", "合法防摔");
        m("Lightning Tracker", "闪电追踪");
        m("Liquid Glass", "液态玻璃");
        m("Liquid Glow", "液态光晕");
        m("Mace PVP", "重锤对战");
        m("MCF", "MCF 反检测");
        m("More Particles", "更多粒子");
        m("Motion Camera", "动态视角");
        m("Mouse Lock", "鼠标锁定");
        m("Music Player", "音乐播放器");
        m("Name Protect", "名字保护");
        m("Name Tags", "名牌");
        m("No Fall", "防摔");
        m("No Fall 2", "防摔二");
        m("No Hurt Cam", "无受伤抖动");
        m("No Render", "屏蔽渲染");
        m("No Slow Down", "无减速");
        m("Notification", "通知");
        m("Panic", "紧急关闭");
        m("Phase", "穿墙");
        m("Projectile", "投掷物预警");
        m("Protocol", "协议");
        m("Regen", "快速回血");
        m("Reset VL", "重置违规");
        m("Safe Walk", "安全行走");
        m("Scaffold", "自动搭桥");
        m("Snowball Aura", "雪球光环");
        m("Song Info", "歌曲信息");
        m("Spammer", "自动刷屏");
        m("Speed", "加速");
        m("Sprint", "疾跑");
        m("Step", "自动上台阶");
        m("Strafe", "平移");
        m("Stuck", "卡住提示");
        m("Sword Gapple", "自动切换金苹果");
        m("Sword Pearl", "自动切换珍珠");
        m("Target ESP", "目标透视");
        m("Target Glow", "目标发光");
        m("Targets", "目标设置");
        m("Target Strafe", "目标环绕");
        m("TAS", "TAS 工具");
        m("Teams", "队伍");
        m("Teleport", "传送");
        m("Time Changer", "时间修改");
        m("Timer", "变速齿轮");
        m("Title", "标题");
        m("Tp Aura", "传送光环");
        m("Tp Aura Plus", "传送光环增强");
        m("Tpaura Rise", "传送光环 Rise");
        m("Translator", "中文翻译");
        m("VClip", "垂直卡墙");
        m("Velocity", "反击退");
        m("World Tweaks", "世界微调");
        // 兜底：没有空格拆分的原始名字
        m("AutoBlock", "自动格挡");
        m("TpAura", "传送光环");
        m("TpAuraPlus", "传送光环增强");
        m("TpauraRise", "传送光环 Rise");
        m("NoFall2", "防摔二");
        m("ChestGUI", "箱子界面");
        m("ClickGUI", "点击界面");

        // ---------- 设置项名称 ----------
        v("1.9 Cooldown", "1.9 冷却");
        v("2D ESP", "2D 透视");
        v("Abilities Spoof", "能力伪装");
        v("Abilities Ticks", "能力生效刻");
        v("Accent", "强调值");
        v("Accent B", "强调色 B");
        v("Accent Color", "强调色");
        v("Accent G", "强调色 G");
        v("Accent R", "强调色 R");
        v("Account Timer", "账号计时");
        v("Accurate Box", "精确碰撞箱");
        v("Accurate Latency", "精确延迟");
        v("Actionbar", "动作栏提示");
        v("Aim Jitter", "瞄准抖动");
        v("Aim Range", "瞄准距离");
        v("All Items", "所有物品");
        v("Allow Empty Hand", "允许空手");
        v("Allow Live Send", "允许实时发送");
        v("Allow Negative", "允许负值");
        v("Allow Placing", "允许放置");
        v("Alpha", "透明度");
        v("Alt Control", "Alt 键控制");
        v("Amount", "数量");
        v("Animal", "动物");
        v("Animals", "动物");
        v("Animation", "动画");
        v("Animation Speed", "动画速度");
        v("Anti Teleport", "防传送");
        v("AntiClear", "防清空");
        v("AntiSpam", "防刷屏");
        v("Append Prefix", "附加前缀");
        v("Append Suffix", "附加后缀");
        v("Armor Bar", "护甲条");
        v("Arrows", "箭头");
        v("Attack Animals", "攻击动物");
        v("Attack Crystals", "攻击水晶");
        v("Attack Delay", "攻击延迟");
        v("Attack Invisible", "攻击隐身目标");
        v("Attack Mobs", "攻击怪物");
        v("Attack Player", "攻击玩家");
        v("Attack Range", "攻击距离");
        v("Attack Reaction", "攻击反应");
        v("Attack Times", "攻击次数");
        v("Aura", "光环");
        v("Aura Delay", "光环延迟");
        v("Aura Range", "光环距离");
        v("Aura Rotate", "光环转向");
        v("Aura Silent", "光环静默");
        v("Auto 3rd Person", "自动第三人称");
        v("Auto Armor", "自动穿甲");
        v("Auto Close", "自动关闭");
        v("Auto Disable", "自动关闭模块");
        v("Auto Elytra", "自动鞘翅");
        v("Auto Fly", "自动飞行");
        v("Auto Heal", "自动治疗");
        v("Auto Jump", "自动跳跃");
        v("Auto Mace", "自动重锤");
        v("Auto Pearl Combo", "自动珍珠连招");
        v("Auto Place", "自动放置");
        v("Auto Play", "自动播放");
        v("Auto Shield", "自动举盾");
        v("Auto Strafe", "自动平移");
        v("Auto Swap", "自动切换");
        v("Auto Switch", "自动切换");
        v("Auto Switch Mace", "自动切换重锤");
        v("Auto Totem", "自动图腾");
        v("Auto Wind Jump", "自动风弹跳");
        v("AutoBlock Mode", "自动格挡模式");
        v("AutoJump", "自动跳跃");
        v("Axe Slot", "斧头槽位");
        v("Background Alpha", "背景透明度");
        v("Background Opacity", "背景不透明度");
        v("Bar Width", "条宽度");
        v("Base Opacity", "基础不透明度");
        v("BaseSpeed", "基础速度");
        v("Blink", "闪现");
        v("Blink Time", "闪现时长");
        v("Block HUD", "方块提示");
        v("Block Limit", "方块上限");
        v("Block Mode", "格挡模式");
        v("Block Range", "放置距离");
        v("Block Slot", "方块槽位");
        v("Blocks Only", "仅方块");
        v("Blue", "蓝色");
        v("Blur", "模糊");
        v("Boost Once", "仅加速一次");
        v("Boost Speed", "加速倍率");
        v("Boost Ticks", "加速刻数");
        v("BoostDuration", "加速时长");
        v("BoostHeight", "加速高度");
        v("Border", "边框");
        v("Bounce Height", "弹跳高度");
        v("Bow", "弓");
        v("Bow Slot", "弓槽位");
        v("Box Layers", "方框层数");
        v("Box Size", "方框大小");
        v("Brand", "客户端标识");
        v("Break Shield", "破盾");
        v("Bypass Vanilla", "绕过原版");
        v("Cancel Bad Packets", "取消异常包");
        v("Cancel Ground", "取消地面状态");
        v("Cancel Ping Packets", "取消延迟包");
        v("Cancel Sprint", "取消疾跑");
        v("Chance", "概率");
        v("Check Down", "检测下方");
        v("Chest", "箱子");
        v("Click Only", "仅点击");
        v("Clip Value", "卡墙距离");
        v("Close Duration", "关闭时长");
        v("Color", "颜色");
        v("Color A", "颜色 A");
        v("Color B", "颜色 B");
        v("Color Setting", "配色模式");
        v("Color Text", "文字着色");
        v("Column Height", "列高");
        v("Combat Mode", "战斗模式");
        v("Compass Only", "仅罗盘");
        v("Contract Curve", "收缩曲线");
        v("Contract Duration", "收缩时长");
        v("Convert Packets", "转换数据包");
        v("Cooldown", "冷却");
        v("Cooldown Base", "冷却基数");
        v("Copy", "复制");
        v("Copy Notify", "复制提示");
        v("Cover", "遮罩");
        v("Cover Curve", "遮罩曲线");
        v("CPS", "每秒点击");
        v("Critical Range", "暴击距离");
        v("Curtain Alpha", "幕布透明度");
        v("CustomStrafeSpeed", "自定义平移速度");
        v("DamageBoost", "伤害提升");
        v("Dead", "死亡");
        v("Debug", "调试");
        v("Delay", "延迟");
        v("Delay Between", "间隔延迟");
        v("Delay Max", "最大延迟");
        v("Delay Min", "最小延迟");
        v("Derp Pitch", "摇头俯仰");
        v("Derp Spin Speed", "摇头旋转速度");
        v("Diamond", "钻石");
        v("Disable Color", "关闭颜色");
        v("Disable Effects", "禁用效果");
        v("Disable First Perspective", "禁用第一人称");
        v("Disable on Forward", "前进时关闭");
        v("Disable on Lagback", "回弹时关闭");
        v("Disable Speed Module", "关闭加速模块");
        v("Disable Text", "关闭文字");
        v("Disable Vanilla Gui", "禁用原版界面");
        v("Disabler Mode", "反检测模式");
        v("Display", "显示");
        v("Distance", "距离");
        v("Dive Range", "俯冲距离");
        v("Dodge", "闪避");
        v("Dodge Health", "闪避血量");
        v("Downwards", "向下");
        v("Draw Sign When Closed", "关闭时显示标志");
        v("Drop Old Armor", "丢弃旧护甲");
        v("Duration", "持续时间");
        v("Dynamic Opacity", "动态不透明度");
        v("Eggs", "鸡蛋");
        v("Elytra Bounce", "鞘翅弹跳");
        v("Elytra Glide", "鞘翅滑翔");
        v("Elytra In Offhand", "副手鞘翅");
        v("Enable Color", "开启颜色");
        v("Enable Text", "开启文字");
        v("Enchanted Gapples", "附魔金苹果");
        v("Ender Chest", "末影箱");
        v("Ender Chest Color", "末影箱颜色");
        v("Ender Pearl", "末影珍珠");
        v("EntityID", "实体 ID");
        v("Equip Mode", "装备模式");
        v("Equip Progress", "装备进度");
        v("Erase Curve", "擦除曲线");
        v("Erase Duration", "擦除时长");
        v("Exclude Shop", "排除商店");
        v("Explosion Smoke", "爆炸烟雾");
        v("Fade Duration", "淡出时长");
        v("Fake AutoBlock", "假自动格挡");
        v("Fall Distance", "下落距离");
        v("Feather", "羽毛");
        v("Filter Count", "过滤数量");
        v("Filters", "过滤器");
        v("Firework", "烟花");
        v("Fishing Rod Slot", "钓鱼竿槽位");
        v("Flag Height", "标记高度");
        v("Fly Range", "飞行距离");
        v("Food Limit", "食物阈值");
        v("Food Slot", "食物槽位");
        v("Force Ground", "强制地面");
        v("Force Lagback", "强制回弹");
        v("ForceUnicode", "强制 Unicode 字体");
        v("FoV", "视野角度");
        v("Freeze Tick", "冻结刻");
        v("Friend Color", "好友颜色");
        v("FUCK EC", "末影箱无视");
        v("Full Delay", "完全延迟");
        v("Full Release Packet", "完整释放包");
        v("Full Require Ammo", "需要弹药");
        v("Full Require Bow", "需要弓");
        v("Full Swing", "完整挥动");
        v("Gamma", "伽马值");
        v("Gap", "间隔");
        v("Glide Speed", "滑翔速度");
        v("Glide Ticks", "滑翔刻数");
        v("God Items", "神装物品");
        v("Gold", "金");
        v("Golden Apple", "金苹果");
        v("Green", "绿色");
        v("Group Window", "分组窗口");
        v("H2 Aim Range", "H2 瞄准距离");
        v("H2 Attack Amount", "H2 攻击次数");
        v("H2 Attack Animals", "H2 攻击动物");
        v("H2 Attack Invisible", "H2 攻击隐身目标");
        v("H2 Attack Mobs", "H2 攻击怪物");
        v("H2 Attack Player", "H2 攻击玩家");
        v("H2 Auto Count", "H2 自动次数");
        v("H2 Delay", "H2 延迟");
        v("H2 Delay Mode", "H2 延迟模式");
        v("H2 Drift", "H2 漂移");
        v("H2 Enemy Delay", "H2 敌方延迟");
        v("H2 FoV", "H2 视野角度");
        v("H2 Hurt Time", "H2 受伤时间");
        v("H2 Infinity Switch", "H2 无限切换");
        v("H2 Jitter", "H2 抖动");
        v("H2 Max APS", "H2 最大每秒攻击");
        v("H2 Max Delay", "H2 最大延迟");
        v("H2 Max MS", "H2 最大毫秒");
        v("H2 Max Range", "H2 最大距离");
        v("H2 Min APS", "H2 最小每秒攻击");
        v("H2 Prediction", "H2 预测");
        v("H2 Render", "H2 渲染");
        v("H2 Render Bar", "H2 渲染条");
        v("H2 Require Aura", "H2 需要光环");
        v("H2 Self Delay", "H2 自身延迟");
        v("H2 Sprint Check", "H2 疾跑检测");
        v("H2 Start Range", "H2 起始距离");
        v("H2 Switch Delay", "H2 切换延迟");
        v("H2 Switch Size", "H2 切换数量");
        v("H2 Through Walls", "H2 穿墙");
        v("H2 Wall Range", "H2 隔墙距离");
        v("Hand Only HUD", "仅手持提示");
        v("Hand Only Render Dest", "仅手持渲染目标");
        v("Heal Health", "治疗血量");
        v("Health", "血量");
        v("Health Bar", "血条");
        v("Health Percent", "血量百分比");
        v("Height", "高度");
        v("Hidden FOV", "隐藏视野");
        v("Hide FOV", "隐藏视野");
        v("Hide Potion Icons", "隐藏药水图标");
        v("Highlight", "高亮");
        v("Highlight Animation Speed", "高亮动画速度");
        v("Highlight Block", "高亮方块");
        v("Highlight Hurt", "受伤高亮");
        v("Highlight Target", "高亮目标");
        v("Hold Time", "保持时长");
        v("Horizontal", "水平");
        v("Horizontal Speed", "水平速度");
        v("HorizontalAcceleration", "水平加速度");
        v("HorizontalJumpOff", "水平起跳");
        v("Hostile", "敌对生物");
        v("Hosts", "主机");
        v("HUD", "界面");
        v("HUD Opacity", "界面不透明度");
        v("HUD Options", "界面选项");
        v("HUD Scale", "界面缩放");
        v("HUD Scale Closed", "收起时缩放");
        v("HUD Scale Open", "展开时缩放");
        v("HUD Style", "界面样式");
        v("HUD Use Server Rotation", "界面使用服务器转向");
        v("HUD X Offset", "界面 X 偏移");
        v("HUD Y Offset", "界面 Y 偏移");
        v("Hurt Time", "受伤时间");
        v("Icon B", "图标蓝色");
        v("Icon G", "图标绿色");
        v("Icon R", "图标红色");
        v("Ignore Pearls", "忽略珍珠");
        v("Ignore Teammates", "忽略队友");
        v("Indicators", "指示器");
        v("Infinite", "无限");
        v("Intensity", "强度");
        v("Inventory Only", "仅背包");
        v("Inventory Swap", "背包切换");
        v("Invisible", "隐身");
        v("Invisibles", "隐身目标");
        v("Iron", "铁");
        v("Item Check", "物品检测");
        v("Item Spoof", "物品伪装");
        v("Items", "物品");
        v("Jump Height", "跳跃高度");
        v("Jump Power", "跳跃力度");
        v("Jump Speed", "跳跃速度");
        v("JumpHeight", "跳跃高度");
        v("Keep Alive", "保活");
        v("Keep Alive Delay", "保活延迟");
        v("Keep Food", "保留食物");
        v("Keep Sprint", "保持疾跑");
        v("Keep Swing", "保持挥动");
        v("KeepAfterDeath", "死亡后保留");
        v("Lagback Direction", "回弹方向");
        v("Lagback Range", "回弹距离");
        v("Lagback Ticks", "回弹刻数");
        v("Last Close Delay", "上次关闭延迟");
        v("Last Erase Duration", "上次擦除时长");
        v("Latency Suffix", "延迟后缀");
        v("Lava Bucket Slot", "岩浆桶槽位");
        v("Lightning", "闪电");
        v("Lightning Amount", "闪电数量");
        v("Line Width", "线宽");
        v("Lock In Chat", "聊天中锁定");
        v("Lock In Inventory", "背包中锁定");
        v("Log", "记录");
        v("Log Hotbar", "记录快捷栏");
        v("Log Packets", "记录数据包");
        v("Log Style", "记录样式");
        v("Log Ticks", "记录刻数");
        v("Look Up Angle", "抬头角度");
        v("Main Color", "主颜色");
        v("Max Blocks", "最大方块数");
        v("Max CPS", "最大每秒点击");
        v("Max Delay", "最大延迟");
        v("Max Distance", "最大距离");
        v("Max Opacity", "最大不透明度");
        v("Max Packets", "最大数据包");
        v("Max Players", "最大玩家数");
        v("Max Stack", "最大堆叠");
        v("Max Ticks", "最大刻数");
        v("Max Visible", "最大可见数");
        v("Message", "消息");
        v("Min CPS", "最小每秒点击");
        v("Min Delay", "最小延迟");
        v("Min Distance", "最小距离");
        v("Min Fall", "最小下落距离");
        v("Min Packets", "最小数据包");
        v("Mob", "怪物");
        v("Mobs", "怪物");
        v("Mode", "模式");
        v("Module Notify", "模块通知");
        v("Monsters", "怪物");
        v("More Particles", "更多粒子");
        v("Motion", "动态");
        v("Move Distance", "移动距离");
        v("Movement Fix", "移动修正");
        v("MovementFix Mode", "移动修正模式");
        v("Multi Attack", "多重攻击");
        v("Name Tags", "名牌");
        v("Next Delay", "下次延迟");
        v("No Flying", "禁用飞行");
        v("No Move", "禁止移动");
        v("No Player Only", "不限于玩家");
        v("No Potion Icons", "无药水图标");
        v("No Scaffolding", "无脚手架");
        v("No Swing", "不挥动");
        v("Normal Gapples", "普通金苹果");
        v("Normal Speed", "正常速度");
        v("Normal Ticks", "正常刻数");
        v("Normalize Combat", "战斗标准化");
        v("Nostalgia", "怀旧");
        v("Notify Duration", "通知时长");
        v("Observe Only", "仅观察");
        v("Offset", "偏移");
        v("Offset XZ", "水平偏移");
        v("Offset Y", "垂直偏移");
        v("On Move", "移动时");
        v("OnGroundOnly", "仅在地面");
        v("Only Best", "仅最佳");
        v("Only Important", "仅重要");
        v("Only On Target", "仅对目标");
        v("Only Targeted", "仅被瞄准");
        v("Opacity", "不透明度");
        v("Open Curve", "展开曲线");
        v("Open Delay", "开启延迟");
        v("Open Duration", "开启时长");
        v("Opened Color", "已开启颜色");
        v("Other Color", "其他颜色");
        v("Out Of Range Selection", "超距选择");
        v("Outline", "轮廓");
        v("Packet Loss Chance", "丢包概率");
        v("Packets/Tick", "每刻数据包");
        v("Particle", "粒子");
        v("Particle Count", "粒子数量");
        v("Particle Speed", "粒子速度");
        v("Particles", "粒子");
        v("Pause", "暂停");
        v("Pause Chance", "暂停概率");
        v("Pause On Hurt", "受伤时暂停");
        v("Pearl Combo Height", "珍珠连招高度");
        v("Pearl Delay", "珍珠延迟");
        v("Pearl Slot", "珍珠槽位");
        v("Pearls", "珍珠");
        v("Permanent", "永久");
        v("Phase Speed", "穿墙速度");
        v("Pickaxe Slot", "镐子槽位");
        v("Pitch", "俯仰角");
        v("Pitch Check", "俯仰检测");
        v("Place Range", "放置距离");
        v("Player", "玩家");
        v("Player Color", "玩家颜色");
        v("Player Hider", "玩家隐藏");
        v("Players", "玩家");
        v("Playlist ID", "歌单 ID");
        v("Points", "点数");
        v("Position", "位置");
        v("Position Offset", "位置偏移");
        v("Potion Effects", "药水效果");
        v("Potions", "药水");
        v("Pre-Attack Delay", "攻击前延迟");
        v("Predictive", "预测");
        v("Prefer Baby", "优先幼年");
        v("Prefix", "前缀");
        v("Prefix Style", "前缀样式");
        v("Prev", "上一个");
        v("Prewarm Count", "预热次数");
        v("Prewarm Packets", "预热数据包");
        v("Priority", "优先级");
        v("Progress", "进度");
        v("Progress Bar", "进度条");
        v("Projectile Slot", "投掷物槽位");
        v("Protect Friends", "保护好友");
        v("Protect Others", "保护他人");
        v("Protect Self", "保护自己");
        v("Protect Y", "保护 Y 轴");
        v("PullDown", "下压");
        v("PullDownDuringFall", "下落时下压");
        v("Pulse", "脉冲");
        v("Pulse Speed", "脉冲速度");
        v("Radius", "半径");
        v("Random Offset", "随机偏移");
        v("Randomize Order", "随机顺序");
        v("Randomize Packets", "随机数据包");
        v("Randomize Position", "随机位置");
        v("Randomize Queue", "随机队列");
        v("Range", "距离");
        v("Range Closed", "收起时距离");
        v("Range Open", "展开时距离");
        v("Ray Cast", "射线检测");
        v("Reaction Delay", "反应延迟");
        v("Reboost Ticks", "再次加速刻数");
        v("ReboostTicks", "再次加速刻数");
        v("Red", "红色");
        v("Re-equip Elytra", "重新装备鞘翅");
        v("Regen Check", "回血检测");
        v("Regen Mode", "回血模式");
        v("Release Rate", "释放频率");
        v("Render", "渲染");
        v("Render Block", "渲染方块");
        v("Render Block Approach", "方块接近渲染");
        v("Render Block Color A", "方块渲染颜色 A");
        v("Render Block Color B", "方块渲染颜色 B");
        v("Render Box", "渲染方框");
        v("Render Landing", "渲染落点");
        v("Render Mode", "渲染模式");
        v("Render Path", "渲染路径");
        v("Render Pos", "渲染坐标");
        v("Render Pos Color", "坐标渲染颜色");
        v("Render Pos Scale", "坐标渲染缩放");
        v("Render Trail", "渲染轨迹");
        v("Render Trail Color", "轨迹渲染颜色");
        v("Require space key", "需要空格键");
        v("Resume", "恢复");
        v("Retrigger Delay", "重新触发延迟");
        v("Return Delay Ticks", "返回延迟刻数");
        v("RMB Condition", "右键条件");
        v("Rotate", "转向");
        v("Rotation Mode", "转向模式");
        v("Rotation Range", "转向距离");
        v("Rotation Speed", "转向速度");
        v("Rounded", "圆角");
        v("Safe Target Fallback", "目标失效回退");
        v("Scale", "缩放");
        v("Second Color", "次颜色");
        v("Self Color", "自身颜色");
        v("Send Feedback", "发送反馈");
        v("Send Spoof Packets", "发送伪装包");
        v("Sentinel", "哨兵");
        v("Sentinel Boost", "哨兵加速");
        v("Sentinel Ticks", "哨兵刻数");
        v("Sentinel Timer", "哨兵计时");
        v("SentinelSpeed", "哨兵速度");
        v("Server", "服务器");
        v("Shape", "形状");
        v("Shield Range", "举盾距离");
        v("Shift Only", "仅潜行");
        v("Shovel Slot", "铲子槽位");
        v("Show", "显示");
        v("Show Angerable", "显示可激怒");
        v("Show CPS", "显示每秒点击");
        v("Show FPS", "显示帧率");
        v("Show GameMode", "显示游戏模式");
        v("Show Health", "显示血量");
        v("Show Item Counts", "显示物品数量");
        v("Show Memory", "显示内存");
        v("Show Misc", "显示杂项");
        v("Show Mob", "显示怪物");
        v("Show Modules", "显示模块");
        v("Show Passive", "显示被动生物");
        v("Show Ping", "显示延迟");
        v("Show Player", "显示玩家");
        v("Show Speed", "显示速度");
        v("Show Time", "显示时间");
        v("Show User", "显示用户名");
        v("Show Volume", "显示音量");
        v("Shrink", "缩小");
        v("Silent", "静默");
        v("Silent Aim", "静默瞄准");
        v("Simulate Hover", "模拟悬停");
        v("Simulate Key Jitter", "模拟按键抖动");
        v("Simulate Packet Loss", "模拟丢包");
        v("Simulate Reaction", "模拟反应");
        v("Single Color", "单色");
        v("Size", "大小");
        v("SkipTicks", "跳过刻数");
        v("Sleep", "休眠");
        v("Slot Gap", "槽位间隔");
        v("Slow Speed", "减速倍率");
        v("Smoothness", "平滑度");
        v("Snowballs", "雪球");
        v("Sort Mode", "排序方式");
        v("Sorting", "排序");
        v("Sound", "音效");
        v("Sound Pitch", "音调");
        v("Spacing", "间距");
        v("Speed", "速度");
        v("Speed Check", "速度检测");
        v("Spin Speed", "旋转速度");
        v("Sprint", "疾跑");
        v("Stack Messages", "合并消息");
        v("Steal Mode", "偷取模式");
        v("Step Distance", "上台阶距离");
        v("Step Size", "上台阶高度");
        v("StrafeSpeed", "平移速度");
        v("StrafeStrength", "平移强度");
        v("Strength", "强度");
        v("Strict Provider Gate", "严格服务商校验");
        v("Style", "样式");
        v("Sweep Curve", "扫过曲线");
        v("Sweep Duration", "扫过时长");
        v("Swing", "挥动");
        v("Swing Mode", "挥动模式");
        v("Swing Speed", "挥动速度");
        v("Switch Back Delay", "切回延迟");
        v("Switch Delay", "切换延迟");
        v("Switch Mode", "切换模式");
        v("Switch To", "切换到");
        v("Sword Only HUD", "仅剑提示");
        v("Sword Only Render Dest", "仅剑渲染目标");
        v("Tab List", "玩家列表");
        v("Tab Size", "列表尺寸");
        v("TabGUI", "Tab 菜单");
        v("Target", "目标");
        v("Target ESP", "目标透视");
        v("Target Mode", "目标模式");
        v("Target Range", "目标距离");
        v("Target Timeout", "目标超时");
        v("Targets", "目标");
        v("Teammates", "队友");
        v("Telly Tick", "Telly 刻");
        v("Temp Duration", "临时时长");
        v("Text", "文字");
        v("Text B", "文字蓝色");
        v("Text Curve", "文字曲线");
        v("Text G", "文字绿色");
        v("Text Padding", "文字边距");
        v("Text R", "文字红色");
        v("Throw Range", "投掷距离");
        v("TicksToBoostOff", "加速关闭刻数");
        v("Time", "时间");
        v("Timer", "计时器");
        v("Times", "次数");
        v("Title Filter", "标题过滤");
        v("Totem Health", "图腾血量");
        v("Tower Mode", "搭塔模式");
        v("Trace Logger", "追踪日志");
        v("Tracer", "连线");
        v("Tracking Buffer", "追踪缓冲");
        v("Transaction Spoof", "事务伪装");
        v("Translation", "平移");
        v("Trigger Height", "触发高度");
        v("Try Miss Totem", "尝试漏图腾");
        v("Turn Off Mode", "关闭方式");
        v("Turn On Mode", "开启方式");
        v("Type", "类型");
        v("Unopened Color", "未开启颜色");
        v("Use Cooldown", "使用冷却");
        v("Use Golden Apple", "使用金苹果");
        v("Use Mace", "使用重锤");
        v("VClip", "垂直卡墙");
        v("Verbose", "详细输出");
        v("Vertical", "垂直");
        v("Vertical Distance", "垂直距离");
        v("Vertical Speed", "垂直速度");
        v("Villager", "村民");
        v("Void Level", "虚空高度");
        v("Void Protect", "虚空保护");
        v("Volume", "音量");
        v("Wall Range", "隔墙距离");
        v("Water Bucket Slot", "水桶槽位");
        v("Watermark", "水印");
        v("Weapon Slot", "武器槽位");
        v("Weather", "天气");
        v("When Show HUD", "显示条件");
        v("White Mode", "白色模式");
        v("Width", "宽度");
        v("Wind Charge Delay", "风弹延迟");
        v("Wind Jump Cooldown", "风弹跳冷却");
        v("World Time", "世界时间");
        v("X", "X 坐标");
        v("X Offset", "X 偏移");
        v("X Rate", "X 速率");
        v("Y", "Y 坐标");
        v("Y Offset", "Y 偏移");
        v("Y Rate", "Y 速率");

        // ---------- ClickGUI 散装文案 ----------
        x("Bind", "绑定");
        x("Enabled", "已开启");
        x("Disabled", "已关闭");
        x("ON", "开");
        x("OFF", "关");
    }
}
