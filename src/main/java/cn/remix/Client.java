package cn.remix;

import cn.remix.command.CommandManager;
import cn.remix.config.ConfigManager;
import cn.remix.event.base.EventManager;
import cn.remix.management.*;
import cn.remix.module.ModuleManager;
import cn.remix.module.impl.render.Title;
import cn.remix.ui.clickgui.ClickGuiScreen;
import cn.remix.ui.font.FontManager;
import cn.remix.util.IMinecraft;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

@Getter
public class Client implements IMinecraft {
    public static Client instance;
    public static Logger logger;

    public static String name = "Remix";
    public static String version = "v1.0.0";

    private EventManager eventManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private RotationManager rotationManager;
    private TargetManager targetManager;
    private FriendManager friendManager;
    private FontManager fontManager;
    private PacketManager packetManager;
    private IndicatorManager indicatorManager;
    private ClickGuiScreen clickGuiScreen;

    public void init() {
        eventManager = new EventManager();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        configManager = new ConfigManager();
        rotationManager = new RotationManager();
        targetManager = new TargetManager();
        friendManager = new FriendManager();
        fontManager = new FontManager();
        packetManager = new PacketManager();
        indicatorManager = new IndicatorManager();
        MusicManager.init();
        clickGuiScreen = new ClickGuiScreen();

        
        instance.getModuleManager().getModule(Title.class).apply();

        
        
    }

    public void shutdown() {
        configManager.saveAll();
    }
}
