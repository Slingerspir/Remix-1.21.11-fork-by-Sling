package cn.remix.module;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.module.impl.combat.*;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.impl.exploits.Phase;
import cn.remix.module.impl.exploits.Regen;
import cn.remix.module.impl.exploits.ResetVL;
import cn.remix.module.impl.misc.AntiCheatDetect;
import cn.remix.module.impl.misc.BetterChat;
import cn.remix.module.impl.misc.BetterTab;
import cn.remix.module.impl.misc.NameProtect;
import cn.remix.module.impl.move.*;
import cn.remix.module.impl.player.*;
import cn.remix.module.impl.render.*;
import cn.remix.module.impl.world.Clutch;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.impl.world.Timer;
import cn.remix.module.impl.world.WorldTweaks;
import cn.remix.module.value.Value;
import cn.remix.util.IMinecraft;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.*;

@Getter
public class ModuleManager implements IMinecraft {
    private final Map<String, Module> moduleMap = new LinkedHashMap<>();

    public ModuleManager() {
        instance.getEventManager().register(this);

        addModules(
                new HUD(),
                new Notification(),
                new ClickGui(),
                new Scaffold(),
                new Clutch(),
                new WorldTweaks(),
                new AntiBot(),
                new Aura(),
                new TpAura(),
                new Targets(),
                new Teams(),
                new Derp(),
                new Disabler(),
                new MCF(),
                new GuiMove(),
                new Teleport(),
                new Clipper(),
                new VClip(),
                new FastWeb(),
                new SafeWalk(),
                new TargetStrafe(),
                new DamageTint(),
                new BlockSelection(),
                new ClipHUD(),
                new Criticals(),
                new NoSlowDown(),
                new NoFall(),
                new AntiVoid(),
                new LegitNoFall(),
                new ModuleList(),
                new Speed(),
                new Fly(),
                new Velocity(),
                new AutoTool(),
                new FastUse(),
                new SwordGapple(),
                new ChestStealer(),
                new TargetHUD(),
                new InventoryManager(),
                new MotionCamera(),
                new AutoArmor(),
                new AntiHunger(),
                new Crosshair(),
                new LightningTracker(),
                new Regen(),
                new Brightness(),
                new NoHurtCam(),
                new ItemPhysics(),
                new KeepSprint(),
                new Animation(),
                new ESP(),
                new GlowESP(),
                new Indicator(),
                new ChestESP(),
                new AutoGapple(),
                new GhostHand(),
                new Step(),
                new MoreParticles(),
                new KillEffects(),
                new Stuck(),
                new Strafe(),
                new ClickTP(),
                new AntiLava(),
                new Sprint(),
                new Fullbright(),
                new Backtrack(),
                new ResetVL(),
                new Phase(),
                new ChestGUI(),
                new MouseLock(),
                new BetterChat(),
                new NameProtect(),
                new BetterTab(),
                new AirJump(),
                new AntiCheatDetect(),
                new FlyPlus(),
                new Timer()
        );

        sortModules();
    }

    public void addModules(Module... modulesArray) {
        for (Module module : modulesArray) {
            reflectModuleValues(module);
            moduleMap.put(module.getClass().getSimpleName(), module);
        }
    }

    private void reflectModuleValues(Module module) {
        try {
            Class<?> clazz = module.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Value.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object valueObject = field.get(module);
                        if (valueObject != null) {
                            module.getValues().add((Value) valueObject);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    private void sortModules() {
        List<Module> moduleList = new ArrayList<>(moduleMap.values());
        moduleList.sort(Comparator.comparing(Module::getName));
        moduleMap.clear();
        for (Module module : moduleList) {
            moduleMap.put(module.getClass().getSimpleName(), module);
        }
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        return clazz.cast(moduleMap.get(clazz.getSimpleName()));
    }

    @EventTarget
    private void onKeyInput(KeyInputEvent event) {
        if (event.getKey() == 0 || mc.currentScreen != null) return;

        for (Module module : moduleMap.values()) {
            if (module.getKey() == event.getKey()) {
                module.toggle();
            }
        }
    }
}
