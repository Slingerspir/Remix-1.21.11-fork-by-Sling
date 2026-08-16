package injection.accessor;

import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TitleScreen.class)
public interface MixinTitleScreenAccessor {

    @Accessor("firstLaunch")
    void setFirstLaunch(boolean value);
}