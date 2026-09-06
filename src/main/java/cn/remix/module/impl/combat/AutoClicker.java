package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.ItemUtil;
import injection.accessor.MinecraftClientAccessor;
import net.minecraft.util.hit.HitResult;

public final class AutoClicker extends Module {
    private final NumberValue cps = new NumberValue("CPS", 10, 5, 20, 1);
    private final BoolValue itemCheck = new BoolValue("Item Check", true);
    private final BoolValue onlyOnTarget = new BoolValue("Only On Target", false);

    private float counter = 0;

    public AutoClicker() {
        super("AutoClicker", Category.Combat);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        var item = mc.player.getMainHandStack();
        boolean holdingWeapon = ItemUtil.isSword(item) || item.getItem() instanceof net.minecraft.item.AxeItem || !itemCheck.getValue();
        boolean onBlock = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;
        boolean onTarget = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY;

        if (mc.options.attackKey.isPressed() && holdingWeapon && !onBlock && (!onlyOnTarget.getValue() || onTarget)) {
            counter += cps.getValue() / 20.0f;
            if (counter >= 1.0f / cps.getValue()) {
                mc.attackCooldown = 0;
                ((MinecraftClientAccessor) (Object) mc).invokeDoAttack();
                counter--;
            }
        } else {
            counter = 0;
        }
    }
}
