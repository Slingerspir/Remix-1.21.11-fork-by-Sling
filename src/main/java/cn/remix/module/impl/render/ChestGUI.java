package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.player.ChestStealer;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.animation.Easing;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.*;
import java.util.List;

public final class ChestGUI extends Module {

    
    private final BoolValue chest = new BoolValue("Chest", true);
    private final BoolValue enderChest = new BoolValue("Ender Chest", true);
    private final ModeValue disableVanillaGui = new ModeValue("Disable Vanilla Gui", "Chest Stealer", "Off", "Chest Stealer", "On");

    private final ModeValue whenShowHud = new ModeValue("When Show HUD", "Temp", "Temp", "Always");
    private final NumberValue rangeOpen = new NumberValue("Range Open", 8.0f, 1.0f, 32.0f, 1.0f);
    private final NumberValue rangeClosed = new NumberValue("Range Closed", 16.0f, 1.0f, 32.0f, 1.0f);
    private final NumberValue tempDuration = new NumberValue("Temp Duration", 2000, 300, 20000, 100, () -> whenShowHud.is("Temp"));
    private final NumberValue fadeDuration = new NumberValue("Fade Duration", 500, 100, 2000, 100);
    private final BoolValue drawSignWhenClosed = new BoolValue("Draw Sign When Closed", true);
    private final BoolValue excludeShop = new BoolValue("Exclude Shop", true);

    
    private final NumberValue slotGap = new NumberValue("Slot Gap", 2, -4, 12, 1);
    private final NumberValue hudScaleOpen = new NumberValue("HUD Scale Open", 1.0f, 0.2f, 3.0f, 0.1f);
    private final NumberValue hudScaleClosed = new NumberValue("HUD Scale Closed", 1.0f, 0.2f, 3.0f, 0.1f);
    private final NumberValue xOffset = new NumberValue("HUD X Offset", 0, -200, 200, 1);
    private final NumberValue yOffset = new NumberValue("HUD Y Offset", 0, -200, 200, 1);
    private final ColorValue accentColor = new ColorValue("Accent Color", new Color(100, 150, 255));
    private final BoolValue showItemCounts = new BoolValue("Show Item Counts", true);

    
    private final Map<BlockPos, ChestSnapshot> snapshots = new HashMap<>();
    private final Map<BlockPos, ScreenPoint> screenPoints = new HashMap<>();
    private final Map<BlockPos, Float> hoverAnimations = new HashMap<>();
    private BlockPos currentChest;
    private BlockPos pendingChest;
    private long pendingChestTime;
    private long openedAt;
    private long closedAt;
    private boolean wasOpen;

    public ChestGUI() {
        super("ChestGui", Category.Render);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    public boolean shouldHide(GenericContainerScreen screen) {
        if (!isEnabled() || disableVanillaGui.is("Off") || isExcluded(screen)) return false;
        if (disableVanillaGui.is("Chest Stealer")) {
            ChestStealer stealer = getModule(ChestStealer.class);
            return stealer != null && stealer.isEnabled();
        }
        return true;
    }

    public void recordInteraction(BlockPos pos) {
        if (pos != null && isEnabledBlock(pos)) {
            pendingChest = pos;
            pendingChestTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        if (mc.currentScreen instanceof GenericContainerScreen screen && !isExcluded(screen)) {
            BlockPos detected = detectCurrentChest();
            if (pendingChest != null && now - pendingChestTime < 3000L && isEnabledBlock(pendingChest)) {
                detected = pendingChest;
            }
            if (detected != null) currentChest = detected;
            if (currentChest != null && isEnabledBlock(currentChest)) {
                GenericContainerScreenHandler handler = screen.getScreenHandler();

                
                Inventory inventory = handler.getInventory();
                int containerSize = inventory.size();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < containerSize; i++) {
                    items.add(inventory.getStack(i).copy());
                }

                
                String title = screen.getTitle().getString();
                if (title.isEmpty() || title.equals("Chest") || title.equals("container.chest")) {
                    int rows = handler.getRows();
                    if (rows == 3) title = "Chest";
                    else if (rows == 6) title = "Large Chest";
                    else title = "Container (" + rows + "x9)";
                }

                snapshots.put(currentChest, new ChestSnapshot(items, title, now));
                pendingChest = currentChest;
                pendingChestTime = now;
            }
            if (!wasOpen) openedAt = now;
            wasOpen = true;
            closedAt = now;
        } else {
            BlockPos nearby = detectCurrentChest();
            if (nearby != null) pendingChest = nearby;
            if (wasOpen) closedAt = now;
            wasOpen = false;
            currentChest = null;
        }

        long keep = Math.max(tempDuration.getValue().longValue() + fadeDuration.getValue().longValue(), 30000L);
        snapshots.entrySet().removeIf(entry -> now - entry.getValue().updatedAt() > keep && whenShowHud.is("Temp"));
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        screenPoints.clear();
        Iterator<Map.Entry<BlockPos, ChestSnapshot>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ChestSnapshot> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (!isEnabledBlock(pos)) {
                iterator.remove();
                continue;
            }
            Vec3d projected = ProjectUtil.worldSpaceToScreenSpace(
                    new Vec3d(pos.getX() + 0.5, pos.getY() + 1.35, pos.getZ() + 0.5),
                    event.getProjectionMatrix(), event.getModelViewMatrix());
            if (!projected.equals(Vec3d.ZERO) && projected.z >= -1.0 && projected.z <= 1.0) {
                screenPoints.put(pos, new ScreenPoint((float) projected.x, (float) projected.y));
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        for (BlockPos pos : snapshots.keySet()) {
            hoverAnimations.putIfAbsent(pos, 0.0f);
            float target = 0.0f;
            ScreenPoint point = screenPoints.get(pos);
            if (point != null) {
                float mx = event.getContext().getScaledWindowWidth() / 2f;
                float my = event.getContext().getScaledWindowHeight() / 2f;
                float dist = (float) Math.sqrt(Math.pow(mx - point.x(), 2) + Math.pow(my - point.y(), 2));
                if (dist < 50) target = 1.0f;
            }
            float current = hoverAnimations.get(pos);
            hoverAnimations.put(pos, current + (target - current) * 0.15f);
        }

        for (Map.Entry<BlockPos, ChestSnapshot> entry : snapshots.entrySet()) {
            BlockPos pos = entry.getKey();
            ScreenPoint point = screenPoints.get(pos);
            if (point == null) continue;

            boolean open = pos.equals(currentChest) && wasOpen;
            double distance = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
            float range = open ? rangeOpen.getValue().floatValue() : rangeClosed.getValue().floatValue();
            if (distance > range) continue;

            float alpha = getAlpha(entry.getValue(), open, now);
            long age = now - entry.getValue().updatedAt();

            if (open || whenShowHud.is("Always") || age <= tempDuration.getValue() + fadeDuration.getValue()) {
                if (alpha <= 0.01f) continue;
                float hover = hoverAnimations.getOrDefault(pos, 0.0f);
                drawContents(event.getContext(), entry.getValue(), point, open, alpha, hover);
            } else if (drawSignWhenClosed.getValue()) {
                float signAlpha = MathHelper.clamp((now - entry.getValue().updatedAt()) / fadeDuration.getValue(), 0.0f, 1.0f);
                drawClosedSign(event.getContext(), point, signAlpha);
            }
        }

        if (shouldShowStealingText()) {
            String text = "Stealing...";
            float x = event.getContext().getScaledWindowWidth() / 2f;
            float y = event.getContext().getScaledWindowHeight() / 2f - 28;
            float alpha = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
            int color = ColorUtil.applyAlpha(0xFFFFFFFF, Math.round(255 * alpha));
            event.getContext().drawCenteredTextWithShadow(mc.textRenderer, text, (int) x, (int) y, color);
        }
    }

    private void drawContents(DrawContext context, ChestSnapshot snapshot, ScreenPoint point, boolean open, float alpha, float hover) {
        int count = snapshot.items().size();
        int rows = Math.max(1, (count + 8) / 9);
        float gap = Math.max(0, slotGap.getValue());
        float cell = 16.0f + gap;
        float width = 9 * cell - gap + 20.0f;
        float height = rows * cell - gap + 50.0f;

        
        float scale = open ? hudScaleOpen.getValue() : hudScaleClosed.getValue();
        float maxFit = Math.min(
                (context.getScaledWindowWidth() - 20.0f) / width,
                (context.getScaledWindowHeight() - 20.0f) / height
        );
        if (maxFit > 0.05f) {
            scale = Math.min(scale, maxFit);
        }

        float x = point.x() + xOffset.getValue() - width * scale / 2.0f;
        float y = point.y() + yOffset.getValue() - height * scale - 12.0f;

        float offsetY = getVerticalOffset(snapshot, open, hover);
        y += offsetY;

        float scaleAnim = open ? 1.0f : 0.7f + 0.3f * (1.0f - (float) Math.min(1.0, (System.currentTimeMillis() - snapshot.updatedAt()) / 500.0));
        if (!open) scale *= scaleAnim;

        x = MathHelper.clamp(x, 10.0f, context.getScaledWindowWidth() - width * scale - 10.0f);
        y = MathHelper.clamp(y, 10.0f, context.getScaledWindowHeight() - height * scale - 10.0f);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        int alphaInt = Math.round(220 * alpha);
        int accent = accentColor.getValue().getRGB();

        drawBackground(context, width, height, alphaInt, accent);
        drawTitleBar(context, snapshot, width, alphaInt, accent);
        int hoveredSlot = drawItemGrid(context, snapshot, width, gap, cell, alphaInt, x, y, scale);
        drawFooter(context, snapshot, width, alphaInt);

        context.getMatrices().popMatrix();

        drawHoverTooltip(context, snapshot, hoveredSlot, x, y, scale);
    }

    private void drawBackground(DrawContext context, float width, float height, int alpha, int accent) {
        int bg = new Color(20, 25, 40, Math.round(alpha * 0.85f)).getRGB();
        Render2D.drawRect(context, 0, 0, width, height, bg);

        int bgInner = new Color(30, 35, 55, Math.round(alpha * 0.5f)).getRGB();
        Render2D.drawRect(context, 2, 2, width - 4, height - 4, bgInner);

        int highlight = new Color(255, 255, 255, Math.round(alpha * 0.12f)).getRGB();
        Render2D.drawRect(context, 4, 3, width - 8, 1.5f, highlight);

        Render2D.drawRect(context, 0, height - 2, width, 2, ColorUtil.applyAlpha(accent, Math.round(255 * alpha * 0.6f)));
        Render2D.drawRect(context, 0, 0, 2, height, ColorUtil.applyAlpha(accent, Math.round(255 * alpha * 0.3f)));
        Render2D.drawRect(context, width - 2, 0, 2, height, ColorUtil.applyAlpha(accent, Math.round(255 * alpha * 0.15f)));

        
        int topBar = ColorUtil.applyAlpha(accent, Math.round(255 * alpha * 0.55f));
        Render2D.drawRect(context, 0, 0, width, 2.5f, topBar);
    }

    private void drawTitleBar(DrawContext context, ChestSnapshot snapshot, float width, int alpha, int accent) {
        String title = snapshot.title();
        if (title.isEmpty()) title = "Chest";

        int count = snapshot.items().size();
        int nonEmpty = (int) snapshot.items().stream().filter(stack -> !stack.isEmpty()).count();
        String display = title + "  ·  " + nonEmpty + "/" + count;
        int color = new Color(255, 255, 255, Math.round(255 * (alpha / 255f))).getRGB();

        float titleX = 12;
        float titleY = 10;
        context.drawTextWithShadow(mc.textRenderer, display, (int) titleX, (int) titleY, color);

        int lineColor = ColorUtil.applyAlpha(accent, Math.round(255 * alpha * 0.4f));
        Render2D.drawRect(context, 10, 30, width - 20, 1.5f, lineColor);

        int shadowColor = new Color(255, 255, 255, Math.round(255 * alpha * 0.06f)).getRGB();
        Render2D.drawRect(context, 10, 32, width - 20, 0.5f, shadowColor);

        float closeX = width - 22;
        float closeY = 8;
        int closeBg = new Color(255, 50, 50, Math.round(100 * alpha)).getRGB();
        Render2D.drawRect(context, closeX, closeY, 14, 14, closeBg);
        int closeColor = new Color(255, 255, 255, Math.round(200 * alpha)).getRGB();
        context.drawTextWithShadow(mc.textRenderer, "x", (int) closeX + 4, (int) closeY + 1, closeColor);
    }

    private int drawItemGrid(DrawContext context, ChestSnapshot snapshot, float width, float gap, float cell, int alpha,
                             float panelX, float panelY, float scale) {
        int count = snapshot.items().size();
        int cols = 9;
        float startX = 10;
        float startY = 38;
        cell = Math.max(16.5f, cell);

        int rows = (count + cols - 1) / cols;
        if (rows < 1) rows = 1;

        float usedWidth = cols * cell - gap;
        float usedHeight = rows * cell - gap;

        int slotAreaBg = new Color(15, 20, 30, Math.round(60 * alpha / 255f)).getRGB();
        Render2D.drawRect(context, startX - 2, startY - 2, usedWidth + 4, usedHeight + 4, slotAreaBg);

        int gridBorder = new Color(50, 60, 80, Math.round(40 * alpha / 255f)).getRGB();
        Render2D.drawOutline(context, startX - 1, startY - 1, usedWidth + 2, usedHeight + 2, 0.5f, gridBorder);

        int hoveredSlot = -1;
        float mouseX = (float) mc.mouse.getScaledX(mc.getWindow());
        float mouseY = (float) mc.mouse.getScaledY(mc.getWindow());

        for (int i = 0; i < count; i++) {
            ItemStack stack = snapshot.items().get(i);
            int col = i % cols;
            int row = i / cols;

            float itemX = startX + col * cell;
            float itemY = startY + row * cell;

            int slotBg = new Color(40, 45, 65, Math.round(120 * alpha / 255f)).getRGB();
            Render2D.drawRect(context, itemX, itemY, cell, cell, slotBg);

            int slotBorder = new Color(60, 70, 100, Math.round(80 * alpha / 255f)).getRGB();
            Render2D.drawOutline(context, itemX, itemY, cell, cell, 0.5f, slotBorder);

            if (stack.isEmpty()) {
                int emptyGlow = new Color(60, 70, 100, Math.round(30 * alpha / 255f)).getRGB();
                Render2D.drawRect(context, itemX + 2, itemY + 2, cell - 4, cell - 4, emptyGlow);
            }

            if (!stack.isEmpty()) {
                context.drawItem(stack, Math.round(itemX + 2), Math.round(itemY + 2));

                if (showItemCounts.getValue() && stack.getCount() > 1) {
                    String countStr = String.valueOf(stack.getCount());
                    int countColor = new Color(255, 255, 255, Math.round(220 * alpha / 255f)).getRGB();
                    context.drawTextWithShadow(mc.textRenderer, countStr,
                            Math.round(itemX + cell - 4 - mc.textRenderer.getWidth(countStr)),
                            Math.round(itemY + cell - 10), countColor);
                }

                float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1000.0 + i);
                if (pulse > 0.85f && stack.getRarity() != null) {
                    int glow = new Color(255, 255, 255, Math.round(20 * alpha / 255f * (pulse - 0.85f) * 6.67f)).getRGB();
                    Render2D.drawRect(context, itemX, itemY, cell, cell, glow);
                }

                
                float slotScreenX = panelX + (itemX + 2) * scale;
                float slotScreenY = panelY + (itemY + 2) * scale;
                float slotScreenSize = 16.0f * scale;
                if (mouseX >= slotScreenX && mouseX <= slotScreenX + slotScreenSize
                        && mouseY >= slotScreenY && mouseY <= slotScreenY + slotScreenSize) {
                    hoveredSlot = i;
                    int hoverColor = ColorUtil.applyAlpha(accentColor.getValue().getRGB(), Math.round(120 * alpha / 255f));
                    Render2D.drawOutline(context, itemX - 1, itemY - 1, cell + 2, cell + 2, 1.2f, hoverColor);
                }
            }
        }

        
        for (int col = 0; col <= cols; col++) {
            if (col == 0 || col == cols) continue;
            float lineX = startX + col * cell - gap / 2;
            int lineColor = new Color(50, 60, 80, Math.round(30 * alpha / 255f)).getRGB();
            Render2D.drawRect(context, lineX, startY, 0.5f, usedHeight, lineColor);
        }

        
        for (int row = 0; row <= rows; row++) {
            if (row == 0 || row == rows) continue;
            float lineY = startY + row * cell - gap / 2;
            int lineColor = new Color(50, 60, 80, Math.round(30 * alpha / 255f)).getRGB();
            Render2D.drawRect(context, startX, lineY, usedWidth, 0.5f, lineColor);
        }

        int bottomShadow = new Color(0, 0, 0, Math.round(40 * alpha / 255f)).getRGB();
        Render2D.drawRect(context, startX - 2, startY + usedHeight, usedWidth + 4, 3, bottomShadow);
        return hoveredSlot;
    }

    private void drawHoverTooltip(DrawContext context, ChestSnapshot snapshot, int slot, float panelX, float panelY, float scale) {
        if (slot < 0 || slot >= snapshot.items().size()) return;
        ItemStack stack = snapshot.items().get(slot);
        if (stack.isEmpty()) return;

        String name = stack.getName().getString();
        String text = stack.getCount() > 1 ? name + " x" + stack.getCount() : name;
        float mouseX = (float) mc.mouse.getScaledX(mc.getWindow());
        float mouseY = (float) mc.mouse.getScaledY(mc.getWindow());
        int textWidth = mc.textRenderer.getWidth(text);

        float tooltipX = MathHelper.clamp(mouseX + 10.0f, 4.0f, context.getScaledWindowWidth() - textWidth - 14.0f);
        float tooltipY = MathHelper.clamp(mouseY + 10.0f, 4.0f, context.getScaledWindowHeight() - 24.0f);

        int bg = new Color(15, 18, 28, 230).getRGB();
        Render2D.drawRect(context, tooltipX - 4, tooltipY - 3, textWidth + 8, 15, bg);
        Render2D.drawOutline(context, tooltipX - 4, tooltipY - 3, textWidth + 8, 15, 1.0f,
                ColorUtil.applyAlpha(accentColor.getValue().getRGB(), 160));
        context.drawTextWithShadow(mc.textRenderer, text, Math.round(tooltipX), Math.round(tooltipY), 0xFFFFFFFF);
    }

    private void drawFooter(DrawContext context, ChestSnapshot snapshot, float width, int alpha) {
        int count = snapshot.items().size();
        int cols = 9;
        int rows = (count + cols - 1) / cols;
        if (rows < 1) rows = 1;
        float gap = slotGap.getValue();
        float cell = 16.0f + gap;
        float footerY = 38 + rows * cell - gap + 4;

        int lineColor = new Color(255, 255, 255, Math.round(40 * alpha / 255f)).getRGB();
        Render2D.drawRect(context, 10, footerY, width - 20, 1, lineColor);

        float statsY = footerY + 8;

        String stats = count + " items";
        int color = new Color(200, 210, 230, Math.round(180 * alpha / 255f)).getRGB();
        context.drawTextWithShadow(mc.textRenderer, stats, 12, (int) statsY, color);

        String type = count > 27 ? "Double Chest" : "Single Chest";
        int typeColor = new Color(160, 170, 190, Math.round(120 * alpha / 255f)).getRGB();
        float typeX = width / 2 - mc.textRenderer.getWidth(type) / 2f;
        context.drawTextWithShadow(mc.textRenderer, type, (int) typeX, (int) statsY, typeColor);

        String tip = "Hover for details";
        int tipColor = new Color(180, 190, 210, Math.round(150 * alpha / 255f)).getRGB();
        float tipX = width - 12 - mc.textRenderer.getWidth(tip);
        context.drawTextWithShadow(mc.textRenderer, tip, (int) tipX, (int) statsY, tipColor);
    }

    private void drawClosedSign(DrawContext context, ScreenPoint point, float alpha) {
        float scale = hudScaleClosed.getValue();
        float x = point.x() + xOffset.getValue();
        float y = point.y() + yOffset.getValue() - 14.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        int alphaInt = Math.round(170 * alpha);

        int bg = new Color(20, 25, 40, alphaInt).getRGB();
        Render2D.drawRect(context, -12, -12, 24, 24, bg);

        int border = new Color(100, 150, 255, Math.round(120 * alpha)).getRGB();
        Render2D.drawOutline(context, -12, -12, 24, 24, 1.0f, border);

        float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 800.0);
        int pulseAlpha = Math.round(80 * alpha * pulse);
        int glow = new Color(100, 150, 255, pulseAlpha).getRGB();
        Render2D.drawRect(context, -14, -14, 28, 28, glow);

        int textColor = new Color(255, 255, 255, Math.round(255 * alpha)).getRGB();
        context.drawCenteredTextWithShadow(mc.textRenderer, "C", 0, -6, textColor);

        context.getMatrices().popMatrix();
    }

    private float getVerticalOffset(ChestSnapshot snapshot, boolean open, float hover) {
        float baseOffset;
        if (open) {
            double progress = MathHelper.clamp((System.currentTimeMillis() - openedAt) / (double) fadeDuration.getValue(), 0.0, 1.0);
            baseOffset = (float) ((1.0 - Easing.EASE_OUT_CUBIC.getFunction().apply(progress)) * 18.0);
        } else {
            long age = System.currentTimeMillis() - snapshot.updatedAt();
            double progress = MathHelper.clamp((age - tempDuration.getValue()) / (double) fadeDuration.getValue(), 0.0, 1.0);
            baseOffset = (float) (Easing.EASE_IN_EXPO.getFunction().apply(progress) * 28.0);
        }
        return baseOffset - hover * 5.0f;
    }

    private float getAlpha(ChestSnapshot snapshot, boolean open, long now) {
        float fade = fadeDuration.getValue();
        if (open) return MathHelper.clamp((now - openedAt) / fade, 0.0f, 1.0f);
        if (whenShowHud.is("Always")) return 1.0f;
        long elapsed = now - snapshot.updatedAt();
        if (elapsed <= tempDuration.getValue()) return 1.0f;
        return 1.0f - MathHelper.clamp((elapsed - tempDuration.getValue()) / fade, 0.0f, 1.0f);
    }

    private BlockPos detectCurrentChest() {
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && isEnabledBlock(hit.getBlockPos())) {
            return hit.getBlockPos();
        }
        int radius = 5;
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!isEnabledBlock(pos)) continue;
                    double distanceSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (distanceSq < bestSq) {
                        bestSq = distanceSq;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean isEnabledBlock(BlockPos pos) {
        if (mc.world == null) return false;
        var block = mc.world.getBlockState(pos).getBlock();
        return (chest.getValue() && block instanceof ChestBlock) ||
                (enderChest.getValue() && block instanceof EnderChestBlock);
    }

    private boolean isExcluded(GenericContainerScreen screen) {
        if (!excludeShop.getValue()) return false;
        String title = screen.getTitle().getString().toLowerCase();
        return title.contains("shop") || title.contains("buy") || title.contains("upgrade") ||
                title.contains("store") || title.contains("商店") || title.contains("购买") || title.contains("升级");
    }

    private boolean shouldShowStealingText() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !shouldHide(screen)) return false;
        ChestStealer stealer = getModule(ChestStealer.class);
        return stealer != null && stealer.isEnabled() && isStealing();
    }

    private boolean isStealing() {
        if (!(mc.currentScreen instanceof GenericContainerScreen container)) return false;
        GenericContainerScreenHandler handler = container.getScreenHandler();
        Inventory inventory = handler.getInventory();
        int containerSize = inventory.size();
        for (int i = 0; i < containerSize; i++) {
            if (!inventory.getStack(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        snapshots.clear();
        screenPoints.clear();
        hoverAnimations.clear();
        currentChest = null;
        pendingChest = null;
        pendingChestTime = 0L;
        wasOpen = false;
        openedAt = closedAt = 0L;
    }

    private record ChestSnapshot(List<ItemStack> items, String title, long updatedAt) {}

    private record ScreenPoint(float x, float y) {}
}
