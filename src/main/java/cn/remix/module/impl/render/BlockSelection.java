package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.player.TeleportUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.Render3D;
import net.minecraft.block.Block;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.awt.*;

public final class BlockSelection extends Module {
    private final BoolValue animation = new BoolValue("Animation", true);
    private final NumberValue animationSpeed = new NumberValue("Animation Speed", 0.2, 0.01, 1.0, 0.01);
    private final ModeValue renderMode = new ModeValue("Render Mode", "Outline", "Box", "Outline");
    private final ModeValue show = new ModeValue("Show", "Only Visible", "Only Visible", "Full", "Sneak Full");
    private final BoolValue accurateBox = new BoolValue("Accurate Box", true);
    private final NumberValue lineWidth = new NumberValue("Line Width", 3, 1, 10, 1, () -> renderMode.is("Outline"));
    private final ColorValue colorA = new ColorValue("Color A", Color.CYAN);
    private final ColorValue colorB = new ColorValue("Color B", new Color(120, 180, 255));
    private final NumberValue opacity = new NumberValue("Opacity", 80, 0, 255, 5);
    private final BoolValue outOfRangeSelection = new BoolValue("Out Of Range Selection", false);
    private final NumberValue maxDistance = new NumberValue("Max Distance", 128, 5, 512, 1);
    private final BoolValue blockHud = new BoolValue("Block HUD", true);
    private Box renderBox;
    private float rangeFade = 1.0f;

    public BlockSelection() {
        super("BlockSelection", Category.Render);
    }

    @Override
    public void onDisable() {
        renderBox = null;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        Target target = getTarget();
        if (target == null) {
            renderBox = null;
            return;
        }

        updateRangeFade(target.outOfRange);
        int rgb = target.outOfRange ? Color.RED.getRGB() : getWaveColor();
        Box targetBox = getBox(target.pos);
        renderBox = animation.getValue() && renderBox != null ? lerpBox(renderBox, targetBox, animationSpeed.getValue()) : targetBox;

        int renderColor = ColorUtil.applyAlpha(rgb, Math.round(opacity.getValue() * rangeFade));
        boolean depthTest = shouldDepthTest();
        if (renderMode.is("Outline")) {
            Render3D.drawOutlinedBox(event.getMatrixStack(), renderBox, lineWidth.getValue() * 0.01, renderColor, depthTest);
        } else {
            Render3D.drawBox(event.getMatrixStack(), renderBox, renderColor, depthTest);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!blockHud.getValue() || mc.player == null || mc.world == null) return;

        Target target = getTarget();
        if (target == null) return;

        Block block = mc.world.getBlockState(target.pos).getBlock();
        double distance = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(target.pos.toCenterPos());
        String text = block.getName().getString() + String.format(" %.1fm", distance);
        TrueTypeFont font = instance.getFontManager().getFont(16);
        float width = font.getStringWidth(text);
        float x = (mc.getWindow().getScaledWidth() - width) / 2.0f;
        float y = 12.0f;

        Render2D.drawRect(event.getContext(), x - 4.0f, y - 2.0f, width + 8.0f, font.getHeight() + 4.0f, new Color(0, 0, 0, 120).getRGB());
        font.drawStringWithShadow(event.getContext(), text, x, y, target.outOfRange ? Color.RED.getRGB() : getWaveColor());
    }

    private Target getTarget() {
        HitResult hitResult = mc.crosshairTarget;
        boolean outOfRange = false;

        if ((hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) && outOfRangeSelection.getValue()) {
            hitResult = TeleportUtil.raycastBlock(mc.player, maxDistance.getValue());
            outOfRange = true;
        }

        if (!(hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (mc.world.isAir(pos)) {
            return null;
        }

        double distance = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(pos.toCenterPos());
        outOfRange = outOfRange || distance > 5.0;
        return new Target(pos, outOfRange);
    }

    private Box getBox(BlockPos pos) {
        if (!accurateBox.getValue()) {
            return new Box(pos);
        }

        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) {
            return new Box(pos);
        }
        return shape.getBoundingBox().offset(pos);
    }

    private boolean shouldDepthTest() {
        if (show.is("Full")) {
            return false;
        }
        if (show.is("Sneak Full")) {
            return mc.player == null || !mc.player.isSneaking();
        }
        return true;
    }

    private Box lerpBox(Box from, Box to, float speed) {
        float t = Math.clamp(speed, 0.01f, 1.0f);
        return new Box(
                lerp(from.minX, to.minX, t),
                lerp(from.minY, to.minY, t),
                lerp(from.minZ, to.minZ, t),
                lerp(from.maxX, to.maxX, t),
                lerp(from.maxY, to.maxY, t),
                lerp(from.maxZ, to.maxZ, t)
        );
    }

    private double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private int getWaveColor() {
        float ratio = (float) ((Math.sin(System.currentTimeMillis() / 450.0) + 1.0) * 0.5);
        return ColorUtil.interpolate(colorA.getValue().getRGB(), colorB.getValue().getRGB(), ratio);
    }

    private void updateRangeFade(boolean outOfRange) {
        float target = !outOfRange || outOfRangeSelection.getValue() ? 1.0f : 0.0f;
        rangeFade += (target - rangeFade) * 0.22f;
    }

    private record Target(BlockPos pos, boolean outOfRange) {}
}
