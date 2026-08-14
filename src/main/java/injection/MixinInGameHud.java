package injection;

import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.impl.render.Crosshair;
import cn.remix.module.impl.render.HUD;
import cn.remix.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud implements IMinecraft {

    @Unique
    private GuiRenderState state;

    @Unique
    private static final long cache = 1_000_000_000L / 120L; // 120hz

    @Unique
    private long nano = System.nanoTime();

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (state == null) state = new GuiRenderState();

        long now = System.nanoTime();
        if (now - nano >= cache) {
            nano = now;
            state.clear();
            DrawContext cacheContext = new DrawContext(mc, state, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            instance.getEventManager().call(new Render2DEvent(cacheContext, tickCounter.getTickProgress(false)));
        }

        state.forEachSimpleElement(context.state::addSimpleElement, GuiRenderState.LayerFilter.ALL);
        state.forEachTextElement(context.state::addText);
        state.forEachItemElement(context.state::addItem);
        state.forEachSpecialElement(context.state::addSpecialElement);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        if (hud.isEnabled() && hud.getNoPotionIcons().getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (instance.getModuleManager().getModule(Crosshair.class).isEnabled()) {
            ci.cancel();
        }
    }
}


