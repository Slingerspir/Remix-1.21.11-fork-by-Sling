package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.Util;
import cn.remix.util.player.TeleportUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class Teleport extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Once", "Once", "RMB");
    private final ModeValue rmbCondition = new ModeValue("RMB Condition", "Sword Only", () -> mode.is("RMB"), "Off", "Hand Only", "Sword Only");
    private final BoolValue handOnlyRenderDest = new BoolValue("Hand Only Render Dest", true, () -> mode.is("RMB") && rmbCondition.is("Hand Only"));
    private final BoolValue handOnlyHud = new BoolValue("Hand Only HUD", true, () -> mode.is("RMB") && rmbCondition.is("Hand Only"));
    private final BoolValue swordOnlyRenderDest = new BoolValue("Sword Only Render Dest", true, () -> mode.is("RMB") && rmbCondition.is("Sword Only"));
    private final BoolValue swordOnlyHud = new BoolValue("Sword Only HUD", true, () -> mode.is("RMB") && rmbCondition.is("Sword Only"));
    private final NumberValue maxDistance = new NumberValue("Max Distance", 150.0, 3.0, 200.0, 1.0);
    private final NumberValue minDistance = new NumberValue("Min Distance", 5.0, 0.0, 50.0, 0.1);
    private final NumberValue maxPackets = new NumberValue("Max Packets", 175, 1, 300, 1);
    private final NumberValue stepSize = new NumberValue("Step Size", 2.0, 0.5, 10.0, 0.1);
    private final BoolValue sound = new BoolValue("Sound", true);
    private boolean wasUsePressed;

    public Teleport() {
        super("Teleport", Category.Move);
        setKey(GLFW.GLFW_KEY_KP_MULTIPLY);
    }

    @Override
    public void onEnable() {
        wasUsePressed = mc.options != null && mc.options.useKey.isPressed();
        if (mode.is("Once")) {
            teleportToTarget(true);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!mode.is("RMB") || mc.player == null || mc.world == null || mc.currentScreen != null) return;

        boolean usePressed = mc.options.useKey.isPressed();
        if (usePressed && !wasUsePressed && matchesRmbCondition()) {
            teleportToTarget(false);
        }
        wasUsePressed = usePressed;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!shouldRenderPreview() || !shouldRenderDest()) return;

        BlockHitResult hit = getPreviewHit();
        if (!isRenderableHit(hit)) return;

        Render3D.drawBox(event.getMatrixStack(), hit.getBlockPos(), ColorUtil.applyAlpha(getPreviewColor(hit), 70));
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!shouldRenderPreview() || !shouldRenderHud()) return;

        BlockHitResult hit = getPreviewHit();
        if (!isRenderableHit(hit)) return;

        double distance = getDistance(hit);
        String text = distance <= maxDistance.getValue() ? String.format("^ %.1fm", distance) : "that's too far.";
        TrueTypeFont font = instance.getFontManager().getFont(18);
        int x = mc.getWindow().getScaledWidth() / 2 + 10;
        int y = mc.getWindow().getScaledHeight() / 2 - Math.round(font.getHeight() / 2.0f);
        font.drawStringWithShadow(event.getContext(), text, x, y, getPreviewColor(hit));
    }

    private void teleportToTarget(boolean disableAfter) {
        PlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            if (disableAfter) setEnabled(false);
            return;
        }

        BlockHitResult hitResult = TeleportUtil.raycastBlock(player, maxDistance.getValue());
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            Util.log("No reachable block.");
            if (disableAfter) setEnabled(false);
            return;
        }

        BlockPos targetBlock = hitResult.getBlockPos().up();
        Vec3d target = Vec3d.ofBottomCenter(targetBlock);
        double distance = new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(hitResult.getBlockPos().toCenterPos());
        if (distance < minDistance.getValue()) {
            if (disableAfter) setEnabled(false);
            return;
        }

        int packets = TeleportUtil.teleportPath(player, target, stepSize.getValue(), maxPackets.getValue().intValue(), true);
        if (packets >= maxPackets.getValue().intValue() && new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(target) > stepSize.getValue()) {
            Util.log("Position is too far, teleport aborted.");
        } else {
            Util.log("Teleported to (" + targetBlock.getX() + ", " + targetBlock.getY() + ", " + targetBlock.getZ() + ").");
            if (sound.getValue()) {
                TeleportUtil.playTeleportSound();
            }
        }

        if (disableAfter) setEnabled(false);
    }

    private boolean shouldRenderPreview() {
        return mode.is("RMB")
                && (rmbCondition.is("Hand Only") || rmbCondition.is("Sword Only"))
                && matchesRmbCondition()
                && mc.player != null
                && mc.world != null
                && mc.currentScreen == null;
    }

    private boolean shouldRenderDest() {
        return (rmbCondition.is("Hand Only") && handOnlyRenderDest.getValue())
                || (rmbCondition.is("Sword Only") && swordOnlyRenderDest.getValue());
    }

    private boolean shouldRenderHud() {
        return (rmbCondition.is("Hand Only") && handOnlyHud.getValue())
                || (rmbCondition.is("Sword Only") && swordOnlyHud.getValue());
    }

    private boolean matchesRmbCondition() {
        if (mc.player == null) return false;
        if (rmbCondition.is("Hand Only")) {
            return mc.player.getMainHandStack().isEmpty() && mc.player.getOffHandStack().isEmpty();
        }
        if (rmbCondition.is("Sword Only")) {
            return mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
        }
        return true;
    }

    private BlockHitResult getPreviewHit() {
        if (mc.player == null) return null;
        return TeleportUtil.raycastBlock(mc.player, Math.max(maxDistance.getValue(), 512.0f));
    }

    private boolean isRenderableHit(BlockHitResult hit) {
        return hit != null
                && hit.getType() == HitResult.Type.BLOCK
                && !mc.world.isAir(hit.getBlockPos())
                && getDistance(hit) >= minDistance.getValue();
    }

    private double getDistance(BlockHitResult hit) {
        if (mc.player == null) return Double.MAX_VALUE;
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return playerPos.distanceTo(hit.getBlockPos().toCenterPos());
    }

    private int getPreviewColor(BlockHitResult hit) {
        return getDistance(hit) <= maxDistance.getValue() ? Color.YELLOW.getRGB() : Color.RED.getRGB();
    }
}
