package cn.remix.module.impl.misc;

import cn.remix.module.Category;
import cn.remix.module.Module;

public final class Panic extends Module {
    public Panic() {
        super("Panic", Category.Misc);
    }

    @Override
    public void onEnable() {
        for (Module module : instance.getModuleManager().getModuleMap().values()) {
            if (module != this && module.isEnabled()) {
                module.setEnabled(false);
            }
        }
        setEnabled(false);
    }
}
