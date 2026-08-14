package injection;

import cn.remix.event.impl.MoveInputEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput extends Input implements IMinecraft {

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        float forward = this.movementVector.y;
        float strafe = this.movementVector.x;
        boolean jump = this.playerInput.jump();
        boolean sneak = this.playerInput.sneak();

        MoveInputEvent event = new MoveInputEvent(forward, strafe, jump, sneak);
        instance.getEventManager().call(event);

        float newForward = event.getForward();
        float newStrafe = event.getStrafe();
        boolean newJump = event.isJumping();
        boolean newSneak = event.isSneaking();
        boolean movementChanged = newForward != forward || newStrafe != strafe;

        if (movementChanged || newJump != jump || newSneak != sneak) {
            if (movementChanged) {
                this.movementVector = new Vec2f(newStrafe, newForward);
            }
            this.playerInput = new PlayerInput(
                    movementChanged ? newForward > 0 : this.playerInput.forward(), movementChanged ? newForward < 0 : this.playerInput.backward(),
                    movementChanged ? newStrafe > 0 : this.playerInput.left(), movementChanged ? newStrafe < 0 : this.playerInput.right(), newJump, newSneak, this.playerInput.sprint());
        }
    }
}