package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.*;
import net.minecraft.util.math.BlockPos;

import java.awt.*;
import java.util.List;
import java.util.Random;

public final class SafeWalk extends Module {
    private final BoolValue blocksOnly = new BoolValue("Blocks Only", true);
    private final BoolValue allowEmptyHand = new BoolValue("Allow Empty Hand", true);
    private final BoolValue disableOnForward = new BoolValue("Disable on Forward", true);
    private final BoolValue pitchCheck = new BoolValue("Pitch Check", true);
    private final ModeValue hudStyle = new ModeValue("HUD Style", "Aphorism Word", "Off", "Simple", "Aphorism Longer", "Aphorism Word");
    private final NumberValue hudScale = new NumberValue("HUD Scale", 1.5, 0.1, 3.0, 0.05);

    private static boolean sneaking;
    private static boolean previousSneaking;
    private static int aphorismIndex = -1;
    private static int aphorismInnerIndex;
    private final Random random = new Random();

    public SafeWalk() {
        super("SafeWalk", Category.Move);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        previousSneaking = sneaking = mc.options.sneakKey.isPressed();
        updateNextAphorism();
        aphorismInnerIndex = 0;
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        mc.options.sneakKey.setPressed(previousSneaking);
        sneaking = previousSneaking;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        updateNextAphorism();
        aphorismInnerIndex = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (isEdgeOfBlock()) {
            setSneakState(settingsMet());
        } else if (sneaking) {
            setSneakState(false);
        }

        if (sneaking && !settingsMet()) {
            setSneakState(false);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || hudStyle.is("Off") || !sneaking) return;

        String text = getHudText();
        TrueTypeFont font = instance.getFontManager().getFont(Math.max(10, Math.round(16.0f * hudScale.getValue())));
        int color = hudStyle.is("Simple") ? Color.BLUE.getRGB() : Color.HSBtoRGB((aphorismIndex % 15) / 15.0f, 0.85f, 1.0f);
        float x = (mc.getWindow().getScaledWidth() - font.getStringWidth(text)) / 2.0f;
        float y = mc.getWindow().getScaledHeight() / 2.0f + 10.0f;

        font.drawStringWithShadow(event.getContext(), text, x, y, color);
    }

    private void setSneakState(boolean state) {
        if (sneaking == state) return;
        if (!state && physicalSneakPressed()) return;

        mc.options.sneakKey.setPressed(state);
        if (state) {
            advanceAphorism();
        }
        sneaking = state;
    }

    private boolean settingsMet() {
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.getAbilities().flying || mc.player.isSpectator() || mc.player.isSubmergedInWater()) return false;
        if (getModule(Scaffold.class).isEnabled()) return false;

        if (blocksOnly.getValue() && !holdingBlocksPlus()) {
            if (!isEmptyMainhand() || !allowEmptyHand.getValue()) {
                return false;
            }
        }

        if (disableOnForward.getValue() && mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
            return false;
        }

        return !pitchCheck.getValue() || mc.player.getPitch() >= 70.0f;
    }

    private boolean isEdgeOfBlock() {
        if (mc.player == null || mc.world == null || !mc.player.isOnGround()) return false;
        return mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.25, mc.player.getZ())).isAir();
    }

    private boolean physicalSneakPressed() {
        return InputUtil.isKeyPressed(
                mc.getWindow(),
                InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()
        );
    }

    private boolean isEmptyMainhand() {
        return mc.player.getMainHandStack().isEmpty();
    }

    private boolean holdingBlocksPlus() {
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        if (main.getItem() instanceof BlockItem) return true;
        return off.getItem() instanceof BlockItem && !isUsable(main.getItem());
    }

    private boolean isUsable(Item item) {
        ItemStack stack = item.getDefaultStack();
        return stack.contains(DataComponentTypes.EQUIPPABLE)
                || stack.contains(DataComponentTypes.FOOD)
                || item instanceof SnowballItem
                || item instanceof EnderEyeItem
                || item instanceof EnderPearlItem
                || item instanceof FireChargeItem
                || item instanceof WritableBookItem
                || item instanceof FlintAndSteelItem
                || item instanceof BowItem
                || item instanceof WrittenBookItem
                || item instanceof TridentItem
                || item instanceof CrossbowItem
                || item instanceof PotionItem
                || item instanceof BlockItem
                || item instanceof EntityBucketItem
                || item instanceof EndCrystalItem
                || item instanceof SpawnEggItem
                || item instanceof ExperienceBottleItem
                || item instanceof FireworkRocketItem
                || item instanceof EmptyMapItem
                || item instanceof FishingRodItem
                || item instanceof CompassItem
                || item instanceof HoeItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof SpyglassItem;
    }

    public static boolean canSafeWalk() {
        SafeWalk safeWalk = instance.getModuleManager().getModule(SafeWalk.class);
        return safeWalk != null && safeWalk.isEnabled() && safeWalk.settingsMet();
    }

    public static boolean doSneak() {
        return sneaking;
    }

    private String getHudText() {
        if (hudStyle.is("Simple")) {
            return "[Safe]";
        }

        if (aphorismIndex == -1) {
            updateNextAphorism();
            aphorismInnerIndex = 0;
        }

        String aphorism = aphorisms.get(aphorismIndex);
        if (hudStyle.is("Aphorism Longer")) {
            return aphorism.substring(0, Math.min(aphorismInnerIndex, aphorism.length()));
        }

        if (hudStyle.is("Aphorism Word")) {
            if (aphorismInnerIndex >= aphorism.length()) {
                aphorismInnerIndex = 0;
                updateNextAphorism();
                aphorism = aphorisms.get(aphorismIndex);
            }
            return String.valueOf(aphorism.charAt(aphorismInnerIndex));
        }

        return "";
    }

    private void advanceAphorism() {
        if (aphorismIndex == -1 || aphorismInnerIndex >= aphorisms.get(aphorismIndex).length()) {
            updateNextAphorism();
            aphorismInnerIndex = 0;
        }
        aphorismInnerIndex = Math.min(aphorismInnerIndex + 1, aphorisms.get(aphorismIndex).length());
    }

    private void updateNextAphorism() {
        aphorismIndex = random.nextInt(aphorisms.size());
    }

    private static final List<String> aphorisms = List.of(
            "洞庭非人境道路行虚空",
            "我亦东奔向吴国浮云四塞道路赊",
            "惟夫党人之偷乐兮路幽昧以险隘",
            "邅吾道夫昆仑兮路修远以周流",
            "曰黄昏以为期兮羌中道而改路",
            "路不周以左转兮指西海以为期",
            "路漫漫其修远兮吾将上下而求索",
            "酒困路长惟欲睡日高人渴漫思茶",
            "绿阴不减来时路添得黄鹂四五声",
            "绿野堂开占物华路人指道令公家",
            "一自胡尘入汉关十年伊洛路漫漫",
            "往日崎岖还记否路上人困蹇驴嘶",
            "轮台东门送君去去时雪满天山路",
            "斜月沉沉藏海雾碣石潇湘无限路",
            "蓬山此去无多路青鸟殷勤为探看",
            "雁来音信无凭路遥归梦难成",
            "关河冻合东西路肠断斑骓送陆郎",
            "衔霜当路发映雪拟寒开",
            "三十功名尘与土八千里路云和月",
            "四十三年望中犹记烽火扬州路",
            "地暖无秋色江晴有暮晖空馀蝉嘒嘒犹向客依依",
            "青山缭绕疑无路忽见千帆隐映来",
            "高楼临远水复道出繁花唯见相如宅蓬门度岁华",
            "平尽不平处尚嫌功未深应难将世路便得称师心",
            "停骖问前路路在秋云里苍苍县南道去途从此始绝顶忽上盘众山皆下视下视千万峰峰头如浪起",
            "荒村倚废营投宿旅魂惊断雁高仍急寒溪晓更清",
            "梁苑城西二十里一渠春水柳千条若为此路今重过十五年前旧板桥",
            "柳脸半眠丞相树佩马钉铃踏沙路断烬遗香袅翠烟烛骑啼乌上天去",
            "路逢故里物使我嗟行役不归渭北村又作江南客",
            "万里路长在六年身始归所经多旧馆大半主人非",
            "云开巫峡千峰出路转巴江一字流",
            "步入招提路因之访道林石龛苔藓积香径白云深",
            "山川函谷路尘土游子颜萧条去国意秋风生故关",
            "路旁时卖故侯瓜门前学种先生柳"
    );
}
