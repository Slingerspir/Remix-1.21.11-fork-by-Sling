package injection;

import cn.remix.Client;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.management.TasManager;
import cn.remix.module.impl.player.FastUse;
import cn.remix.util.IMinecraft;
import cn.remix.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.InactivityFpsLimiter;
import net.minecraft.client.world.ClientWorld;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient implements IMinecraft {
    @Final
    @Shadow
    public GameOptions options;
    @Shadow
    private int itemUseCooldown;
    @Shadow
    private int attackCooldown;

    @Unique
    private int tasActionTicks;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;instance:Lnet/minecraft/client/MinecraftClient;", shift = At.Shift.AFTER, opcode = Opcodes.PUTSTATIC))
    private void preInit(CallbackInfo ci) {
        Client.instance = new Client();
        Client.logger = LogManager.getLogger(Client.name);
    }

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;inGameHud:Lnet/minecraft/client/gui/hud/InGameHud;", shift = At.Shift.AFTER, opcode = Opcodes.PUTFIELD))
    private void postInit(CallbackInfo ci) {
        Client.instance.init();
    }

    @Inject(method = "stop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;close()V", shift = At.Shift.AFTER))
    private void stop(CallbackInfo ci) {
        instance.shutdown();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isOnGround()) {
            Util.offGroundTicks = 0;
            Util.onGroundTicks++;
        } else {
            Util.onGroundTicks = 0;
            Util.offGroundTicks++;
        }

        instance.getEventManager().call(new TickEvent());

        if (TasManager.isTasActive()) {
            tasActionTicks++;
            if (!TasManager.shouldRunAction(tasActionTicks)) {
                if (attackCooldown > 0) attackCooldown++;
            }
        }
    }

    @Inject(method = "getTargetMillisPerTick", at = @At("HEAD"), cancellable = true)
    private void onGetTargetMillisPerTick(float tickTime, CallbackInfoReturnable<Float> cir) {
        if (mc.world != null && TasManager.isTickRateActive()) {
            float multiplier = TasManager.getTickMultiplier();
            if (multiplier <= 0.0f) {

                cir.setReturnValue(Float.MAX_VALUE);
            } else {
                cir.setReturnValue(tickTime / multiplier);
            }
        }
    }

    @Inject(method = "handleInputEvents", at = @At("TAIL"))
    private void handleInputEvents(CallbackInfo ci) {
        if (mc.player == null || mc.world == null || itemUseCooldown <= 0) return;

        FastUse fastUse = instance.getModuleManager().getModule(FastUse.class);
        if (fastUse != null && fastUse.isEnabled()) {
            itemUseCooldown = Math.min(itemUseCooldown, fastUse.getItemUseCooldown(mc.player.getMainHandStack()));
        }
    }

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At("HEAD"))
    private void setWorld(ClientWorld world, CallbackInfo ci) {
        instance.getEventManager().call(new WorldEvent(world));
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/InactivityFpsLimiter;update()I"))
    private int render(InactivityFpsLimiter instance) {
        return options.getMaxFps().getValue();
    }
}