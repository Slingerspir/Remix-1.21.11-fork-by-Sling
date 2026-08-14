package injection;

import cn.remix.Client;
import cn.remix.event.impl.*;
import cn.remix.util.IMinecraft;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayerEntity implements IMinecraft {

    @Shadow
    protected int ticksLeftToDoubleTapSprint;

    @Shadow
    private double lastXClient;
    @Shadow
    private double lastYClient;
    @Shadow
    private double lastZClient;
    @Shadow
    private float lastYawClient;
    @Shadow
    private float lastPitchClient;

    @Shadow
    private boolean lastOnGround;
    @Shadow
    private boolean lastHorizontalCollision;
    @Shadow
    private boolean autoJumpEnabled;
    @Shadow
    private int ticksSinceLastPositionPacketSent;

    @Shadow @Final
    public ClientPlayNetworkHandler networkHandler;
    @Shadow @Final
    protected MinecraftClient client;

    @Shadow
    private void sendSprintingPacket() {}
    @Shadow
    protected abstract boolean isCamera();

    public MixinClientPlayerEntity(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        Client.instance.getEventManager().call(new UpdateEvent());
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void tickMovement(CallbackInfo ci) {
        Client.instance.getEventManager().call(new LivingUpdateEvent());
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void move(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        MoveEvent event = new MoveEvent(movement.x, movement.y, movement.z);
        Client.instance.getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.getX() != movement.x || event.getY() != movement.y || event.getZ() != movement.z) {
            super.move(movementType, new Vec3d(event.getX(), event.getY(), event.getZ()));
            ci.cancel();
        }
    }

    @Redirect(method = "applyMovementSpeedFactors", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean bypassVanillaItemSlowdown(ClientPlayerEntity player) {
        return false;
    }

    @Inject(method = "applyMovementSpeedFactors", at = @At("RETURN"), cancellable = true)
    private void applyCustomItemSlowdown(Vec2f input, CallbackInfoReturnable<Vec2f> cir) {
        if (this.isUsingItem() && !this.hasVehicle()) {
            SlowEvent event = new SlowEvent(0.2F, 0.2F);
            instance.getEventManager().call(event);

            if (!event.isCancelled()) {
                cir.setReturnValue(new Vec2f(cir.getReturnValue().x * event.getSideways(), cir.getReturnValue().y * event.getForward()));
                this.ticksLeftToDoubleTapSprint = 0;
            }
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void sendMovementPackets(final CallbackInfo ci) {
        this.sendSprintingPacket();

        if (this.isCamera()) {
            MotionEvent event = new MotionEvent(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch(), this.isOnGround(), this.horizontalCollision);
            Client.instance.getEventManager().call(event);

            if (!event.isCancelled()) {
                double d = event.getX() - this.lastXClient;
                double e = event.getY() - this.lastYClient;
                double f = event.getZ() - this.lastZClient;
                double g = event.getYaw() - this.lastYawClient;
                double h = event.getPitch() - this.lastPitchClient;

                ++this.ticksSinceLastPositionPacketSent;

                boolean isPosChanged = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) || this.ticksSinceLastPositionPacketSent >= 20;
                boolean isLookChanged = g != 0.0 || h != 0.0;

                if (isPosChanged && isLookChanged) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(new Vec3d(event.getX(), event.getY(), event.getZ()), event.getYaw(), event.getPitch(), event.isOnGround(), event.isHorizontalCollision()));
                } else if (isPosChanged) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(new Vec3d(event.getX(), event.getY(), event.getZ()), event.isOnGround(), event.isHorizontalCollision()));
                } else if (isLookChanged) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(event.getYaw(), event.getPitch(), event.isOnGround(), event.isHorizontalCollision()));
                } else if (this.lastOnGround != event.isOnGround() || this.lastHorizontalCollision != event.isHorizontalCollision()) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(event.isOnGround(), event.isHorizontalCollision()));
                }

                if (isPosChanged) {
                    this.lastXClient = event.getX();
                    this.lastYClient = event.getY();
                    this.lastZClient = event.getZ();
                    this.ticksSinceLastPositionPacketSent = 0;
                }

                if (isLookChanged) {
                    this.lastYawClient = event.getYaw();
                    this.lastPitchClient = event.getPitch();
                }

                this.lastOnGround = event.isOnGround();
                this.lastHorizontalCollision = event.isHorizontalCollision();
                this.autoJumpEnabled = this.client.options.getAutoJump().getValue();
            }

            event.setPost();
            Client.instance.getEventManager().call(event);
        }

        ci.cancel();
    }
}