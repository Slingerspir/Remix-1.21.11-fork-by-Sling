package cn.remix.module.impl.move;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;

public class KeepSprint extends Module {
    public final NumberValue motion = new NumberValue("Motion", 1, 0, 1, .1f);

    public KeepSprint() {
        super("KeepSprint", Category.Move);
    }
}
