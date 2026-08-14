package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.BlockShapeEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.MovementUtil;
import lombok.Getter;
import net.minecraft.util.shape.VoxelShapes;

@Getter
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class AirJump extends Module {

    private final ModeValue mode = new ModeValue("Mode", "JumpFreely", "JumpFreely", "DoubleJump", "GhostBlock", "Sentinel");
    private final NumberValue jumpPower = new NumberValue("Jump Power", 0.42f, 0.1f, 1.0f, 0.01f);
    private final NumberValue sentinelBoost = new NumberValue("Sentinel Boost", 0.5f, 0.1f, 2.0f, 0.05f, () -> mode.is("Sentinel"));

    private boolean doubleJump = true;
    private int tickCounter = 0;
    private boolean sentinelActive = false;

    public AirJump() {
        super("AirJump", Category.Move);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        doubleJump = true;
        tickCounter = 0;
        sentinelActive = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        instance.getPacketManager().getBlink().dispatch(this);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isOnGround()) {
            doubleJump = true;
            sentinelActive = false;
        }

        setSuffix(mode.getValue());
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        if (!MovementUtil.isMoving()) return;

        String currentMode = mode.getValue();
        switch (currentMode) {
            case "JumpFreely" -> handleJumpFreely();
            case "DoubleJump" -> handleDoubleJump();
            case "GhostBlock" -> {}
            case "Sentinel" -> handleSentinel();
            default -> {}
        }
    }

    private void handleJumpFreely() {
        if (mc.player == null) return;

        if (mc.options.jumpKey.isPressed() && !mc.player.isOnGround()) {
            mc.player.jump();
        }
    }

    private void handleDoubleJump() {
        if (mc.player == null) return;

        if (mc.options.jumpKey.isPressed() && !mc.player.isOnGround() && doubleJump) {
            mc.player.jump();
            doubleJump = false;
        }
    }

    @EventTarget
    public void onBlockShape(BlockShapeEvent event) {
        if (mc.player == null) return;
        if (!mode.is("GhostBlock")) return;

        if (event.getPos().getY() < mc.player.getBlockPos().getY() && mc.options.jumpKey.isPressed()) {
            event.setShape(VoxelShapes.fullCube());
        }
    }

    private void handleSentinel() {
        if (mc.player == null) return;

        boolean isMoving = MovementUtil.isMoving();
        float boost = sentinelBoost.getValue().floatValue();

        tickCounter++;

        if (tickCounter % 6 == 0) {
            instance.getPacketManager().getBlink().start(this);

            if (mc.options.jumpKey.isPressed() && !mc.player.isOnGround() && doubleJump) {
                mc.player.jump();
                doubleJump = false;
                sentinelActive = true;
            }

            if (sentinelActive) {
                MovementUtil.strafe(MovementUtil.getSpeed() + boost);
            }
        } else if (!isMoving) {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        } else {
            instance.getPacketManager().getBlink().dispatch(this);

            if (sentinelActive) {
                MovementUtil.strafe(MovementUtil.getSpeed() + boost * 0.5);
            }
        }

        if (mc.player.isOnGround()) {
            sentinelActive = false;
            doubleJump = true;
        }
    }


    public boolean allowJump() {
        if (!isEnabled()) return false;

        String currentMode = mode.getValue();
        return switch (currentMode) {
            case "JumpFreely", "Sentinel" -> true;
            case "DoubleJump" -> doubleJump || mc.player.isOnGround();
            default -> false;
        };
    }

    public boolean isDoubleJumpAvailable() {
        return doubleJump;
    }

    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName.equals("DoubleJump")) {
            return doubleJump ? "Ready" : "Used";
        }
        if (modeName.equals("Sentinel")) {
            return sentinelActive ? "Active" : "Ready";
        }
        return modeName;
    }
}