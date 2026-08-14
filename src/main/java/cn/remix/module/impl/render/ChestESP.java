package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChestESP extends Module {
    private final ColorValue unopenedColor = new ColorValue("Unopened Color", Color.GREEN);
    private final ColorValue openedColor = new ColorValue("Opened Color", Color.RED);
    private final ColorValue enderChestColor = new ColorValue("Ender Chest Color", Color.MAGENTA);
    private final NumberValue baseOpacity = new NumberValue("Base Opacity", 60, 0, 255, 5);
    private final ModeValue dynamicOpacity = new ModeValue("Dynamic Opacity", "Off", "Off", "Far", "Near");
    private final List<BlockPos> openedChests = Collections.synchronizedList(new ArrayList<>());
    private final List<BlockEntity> chests = new ArrayList<>();

    public ChestESP() {
        super("ChestESP", Category.Render);
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

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == PacketEvent.Type.Received) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof BlockEventS2CPacket blockEvent) {
                if (blockEvent.getType() == 1 && blockEvent.getData() > 0) {
                    BlockPos pos = blockEvent.getPos();
                    if (!openedChests.contains(pos)) {
                        openedChests.add(pos);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        chests.clear();
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(playerChunkX + x, playerChunkZ + z);
                if (chunk != null) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
                            chests.add(blockEntity);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || chests.isEmpty()) return;

        for (BlockEntity chest : chests) {
            BlockPos pos = chest.getPos();
            int alpha = getAlpha(pos);
            int color;

            if (chest instanceof EnderChestBlockEntity) {
                color = ColorUtil.applyAlpha(enderChestColor.getValue().getRGB(), alpha);
            } else if (openedChests.contains(pos)) {
                color = ColorUtil.applyAlpha(openedColor.getValue().getRGB(), alpha);
            } else {
                color = ColorUtil.applyAlpha(unopenedColor.getValue().getRGB(), alpha);
            }

            Render3D.drawBox(event.getMatrixStack(), pos, color);
        }
    }

    private int getAlpha(BlockPos pos) {
        if (mc.player == null) return baseOpacity.getValue().intValue();

        float base = baseOpacity.getValue();
        double distance = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(pos.toCenterPos());
        float factor = switch (dynamicOpacity.getValue()) {
            case "Far" -> Math.clamp((float) ((distance - 20.0) / 30.0), 0.0f, 1.0f);
            case "Near" -> 1.0f - Math.clamp((float) ((distance - 5.0) / 45.0), 0.0f, 1.0f);
            default -> 1.0f;
        };
        return Math.round(base * factor);
    }

    private void reset() {
        openedChests.clear();
        chests.clear();
    }
}
