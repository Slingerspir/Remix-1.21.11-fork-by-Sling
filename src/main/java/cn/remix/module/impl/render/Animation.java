package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;

public class Animation extends Module {
    public final NumberValue swingSpeed = new NumberValue("Swing Speed", 0, -4, 20, 1);
    public final ModeValue swingMode = new ModeValue("Swing Mode", "Vanilla", "Vanilla", "Smooth");
    public final ModeValue blockMode = new ModeValue("Block Mode", "Flux", "Flux", "1.7", "Stella", "SideDown", "Leaked", "Styles", "Spin", "Screw", "Swang");
    public final BoolValue equipProgress = new BoolValue("Equip Progress", true);

    public Animation() {
        super("Animation", Category.Render);
        setEnabled(true);
        setHidden(true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(blockMode.getValue());
    }

    @Override
    public void onDisable() {
        setEnabled(true);
    }
}