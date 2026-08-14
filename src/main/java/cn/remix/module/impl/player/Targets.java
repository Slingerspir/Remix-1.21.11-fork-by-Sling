package cn.remix.module.impl.player;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.MultiBoolValue;
import lombok.Getter;

@Getter
public class Targets extends Module {
    private final MultiBoolValue target = new MultiBoolValue("Target",
            new BoolValue("Player", true),
            new BoolValue("Dead", false),
            new BoolValue("Villager", false),
            new BoolValue("Invisible", false),
            new BoolValue("Mob", false),
            new BoolValue("Animal", false)
    );

    public Targets() {
        super("Targets", Category.Player);
        setEnabled(true);
        setHidden(true);
    }

    @Override
    public void onDisable() {
        setEnabled(true);
    }
}
