package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.management.RotationManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.TargetStrafe;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.player.MovementUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Fly extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Sentinel");
    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 3.5, .1, 10, .1);
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", .7, .1, 5, .1);
    private final BoolValue hud = new BoolValue("HUD", true);
    private final BoolValue hudUseServerRotation = new BoolValue("HUD Use Server Rotation", true, hud::getValue);
    private final NumberValue xRate = new NumberValue("X Rate", 0.5f, 0.0f, 1.0f, 0.01f, hud::getValue);
    private final NumberValue yRate = new NumberValue("Y Rate", 0.48f, 0.0f, 1.0f, 0.01f, hud::getValue);
    private final NumberValue hudOpacity = new NumberValue("HUD Opacity", 210, 0, 255, 5, hud::getValue);
    private final BoolValue disableSpeedModule = new BoolValue("Disable Speed Module", false);
    private int tick;

    public Fly() {
        super("Fly", Category.Move);
    }

    @Override
    public void onEnable() {
        tick = 0;
        Speed speed = getModule(Speed.class);
        if (disableSpeedModule.getValue() && speed.isEnabled()) {
            speed.setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        instance.getPacketManager().getBlink().dispatch(this);
        MovementUtil.stop();
    }

    public void renderHud(DrawContext context) {
        getAnimation().run(isEnabled() && hud.getValue() ? 1 : 0);
        float hudAlpha = getAnimation().getValue().floatValue();
        if (hudAlpha <= 0.01f || mc.player == null || mc.world == null) return;

        TrueTypeFont font = instance.getFontManager().getFont(14);
        int alpha = Math.round(hudOpacity.getValue() * hudAlpha);
        int primary = ColorUtil.applyAlpha(0xFFFFFFFF, alpha);
        int dim = ColorUtil.applyAlpha(0xFFB8C8D8, alpha);
        int accent = ColorUtil.applyAlpha(0xFF62FF4C, alpha);
        int tickColor = ColorUtil.applyAlpha(0xFF62FF4C, Math.round(alpha * 0.62f));

        float cx = mc.getWindow().getScaledWidth() * xRate.getValue();
        float cy = mc.getWindow().getScaledHeight() * yRate.getValue();
        Vec3d velocity = mc.player.getVelocity();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
        double vertical = velocity.y * 20.0;
        float[] hudRotations = getHudRotations();
        float heading = Math.floorMod(Math.round(hudRotations[0] * 100.0f), 36000) / 100.0f;
        float pitch = hudRotations[1];
        double altitude = mc.player.getY();
        int groundRelative = MathHelper.floor(altitude) - MathHelper.floor(findGroundY());

        drawCompass(context, font, cx, cy - 72.0f, heading, primary, tickColor, accent);
        drawPitchScale(context, font, cx - 150.0f, cy, pitch, primary, tickColor);
        drawAltitudeScale(context, font, cx + 150.0f, cy, altitude, primary, tickColor);

        Render2D.drawRect(context, cx - 78.0f, cy - 20.0f, 64.0f, 1.0f, accent);
        Render2D.drawRect(context, cx + 14.0f, cy - 20.0f, 64.0f, 1.0f, accent);
        Render2D.drawRect(context, cx - 5.0f, cy + 26.0f, 10.0f, 10.0f, accent);
        Render2D.drawRect(context, cx - 1.0f, cy + 21.0f, 2.0f, 5.0f, accent);
        drawCentered(font, context, "+", cx, cy, primary);
        drawCentered(font, context, "20 L---  ---R 20", cx, cy + 76.0f, accent);
        drawCentered(font, context, "E 100%", cx, cy + 94.0f, accent);

        font.drawStringWithShadow(context, String.format("%.2f", horizontal), cx - 142.0f, cy + 86.0f, primary);
        font.drawStringWithShadow(context, String.format("%+03.1f", vertical), cx + 100.0f, cy + 86.0f, vertical >= 0 ? accent : ColorUtil.applyAlpha(0xFFFF8B8B, alpha));
        font.drawStringWithShadow(context, "G " + groundRelative, cx + 100.0f, cy + 101.0f, primary);
        font.drawStringWithShadow(context, String.format("%.0f / %.0f", mc.player.getX(), mc.player.getZ()), cx - 142.0f, cy + 101.0f, primary);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || check()) return;
        setSuffix(mode.getValue());

        TargetStrafe ts = getModule(TargetStrafe.class);
        boolean strafing = ts.isEnabled() && ts.getTarget() != null && (!ts.getSpace().getValue() || mc.options.jumpKey.isPressed());

        double targetY = 0.0;
        if (!strafing) {
            if (mc.options.jumpKey.isPressed()) {
                targetY = verticalSpeed.getValue().doubleValue();
            } else if (mc.options.sneakKey.isPressed()) {
                targetY = -verticalSpeed.getValue().doubleValue();
            }
        }

        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (!strafing) {
                    mc.player.setVelocity(0.0, targetY, 0.0);
                } else {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
                }
                MovementUtil.strafe(horizontalSpeed.getValue().doubleValue());
            }

            case "Sentinel" -> {
                if (tick++ % 6 == 0) {
                    instance.getPacketManager().getBlink().start(this);
                    mc.player.setVelocity(strafing ? mc.player.getVelocity().x : 0.0, mc.player.getVelocity().y, strafing ? mc.player.getVelocity().z : 0.0);
                    MovementUtil.strafe(horizontalSpeed.getValue().doubleValue());
                } else if (!MovementUtil.isMoving()) {
                    mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
                } else {
                    instance.getPacketManager().getBlink().dispatch(this);
                }
                mc.player.setVelocity(mc.player.getVelocity().x, targetY, mc.player.getVelocity().z);
            }
        }
    }

    private boolean check() {
        return getModule(Disabler.class).isWaiting();
    }

    private void drawCompass(DrawContext context, TrueTypeFont font, float cx, float y, float heading, int primary, int tickColor, int accent) {
        float pixelsPerDegree = 2.9f;
        Render2D.drawRect(context, cx - 132.0f, y + 28.0f, 264.0f, 1.0f, tickColor);
        drawCentered(font, context, String.format("%03d", Math.floorMod(Math.round(heading), 360)), cx, y, primary);
        Render2D.drawOutline(context, cx - 31.0f, y - 3.0f, 62.0f, 22.0f, 1.0f, accent);
        drawCentered(font, context, "^", cx, y + 28.0f, accent);

        int start = (int) Math.floor((heading - 48.0f) / 5.0f) * 5;
        int end = (int) Math.ceil((heading + 48.0f) / 5.0f) * 5;
        for (int mark = start; mark <= end; mark += 5) {
            int wrapped = Math.floorMod(mark, 360);
            float offset = MathHelper.wrapDegrees(mark - heading);
            float x = cx + offset * pixelsPerDegree;
            boolean major = mark % 15 == 0;
            float tickHeight = major ? 12.0f : 6.0f;
            Render2D.drawRect(context, x, y + 30.0f, 1.0f, tickHeight, tickColor);
            if (major) {
                drawCentered(font, context, String.format("%03d", wrapped), x, y + 46.0f, primary);
                if (wrapped % 90 == 0) {
                    drawCentered(font, context, compassLabel(wrapped), x, y + 60.0f, accent);
                }
            }
        }
    }

    private void drawPitchScale(DrawContext context, TrueTypeFont font, float x, float cy, float pitch, int primary, int tickColor) {
        int start = (int) Math.floor((pitch - 32.0f) / 5.0f) * 5;
        int end = (int) Math.ceil((pitch + 32.0f) / 5.0f) * 5;
        for (int mark = start; mark <= end; mark += 5) {
            int clamped = MathHelper.clamp(mark, -90, 90);
            float y = cy + (pitch - clamped) * 3.5f;
            boolean major = clamped % 10 == 0;
            Render2D.drawRect(context, x + (major ? 0.0f : 8.0f), y, major ? 28.0f : 12.0f, 1.0f, tickColor);
            if (major) {
                font.drawStringWithShadow(context, Integer.toString(-clamped), x - 25.0f, y - 5.0f, primary);
            }
        }
        Render2D.drawOutline(context, x + 30.0f, cy - 7.0f, 44.0f, 16.0f, 1.0f, primary);
        font.drawStringWithShadow(context, String.format("%+.1f", -pitch), x + 34.0f, cy - 4.0f, primary);
    }

    private void drawAltitudeScale(DrawContext context, TrueTypeFont font, float x, float cy, double altitude, int primary, int tickColor) {
        int start = (int) Math.floor((altitude - 65.0) / 10.0) * 10;
        int end = (int) Math.ceil((altitude + 65.0) / 10.0) * 10;
        for (int mark = start; mark <= end; mark += 10) {
            float y = cy + (float) ((altitude - mark) * 1.25f);
            boolean major = mark % 50 == 0;
            Render2D.drawRect(context, x, y, major ? 28.0f : 14.0f, 1.0f, tickColor);
            if (major) {
                font.drawStringWithShadow(context, Integer.toString(mark), x + 35.0f, y - 5.0f, primary);
            }
        }
        Render2D.drawOutline(context, x - 8.0f, cy - 7.0f, 48.0f, 16.0f, 1.0f, primary);
        font.drawStringWithShadow(context, Integer.toString(MathHelper.floor(altitude)), x + 1.0f, cy - 4.0f, primary);
    }

    private String compassLabel(int degrees) {
        return switch (Math.floorMod(Math.round(degrees / 45.0f), 8)) {
            case 0 -> "S";
            case 1 -> "SW";
            case 2 -> "W";
            case 3 -> "NW";
            case 4 -> "N";
            case 5 -> "NE";
            case 6 -> "E";
            default -> "SE";
        };
    }

    private void drawCentered(TrueTypeFont font, DrawContext context, String text, float x, float y, int color) {
        font.drawStringWithShadow(context, text, x - font.getStringWidth(text) / 2.0f, y, color);
    }

    private float[] getHudRotations() {
        if (hudUseServerRotation.getValue() && RotationManager.currentRotations != null) {
            return RotationManager.currentRotations;
        }

        return new float[]{mc.player.getYaw(), mc.player.getPitch()};
    }

    private double findGroundY() {
        BlockPos.Mutable pos = mc.player.getBlockPos().mutableCopy();
        int bottom = mc.world.getBottomY();
        while (pos.getY() > bottom && mc.world.getBlockState(pos).isAir()) {
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }
}
