package cn.remix.module.impl.misc;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;

public final class ClientSpoof extends Module {

    private final ModeValue brand = new ModeValue("Brand", "Vanilla",
            "Vanilla", "Forge", "Fabric", "Lunar", "Badlion", "Geyser");

    public ClientSpoof() {
        super("ClientSpoof", Category.Misc);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Send) return;
        if (!(event.getPacket() instanceof CustomPayloadC2SPacket packet)) return;
        if (!(packet.payload() instanceof BrandCustomPayload)) return;

        event.setPacket(new CustomPayloadC2SPacket(new BrandCustomPayload(getSpoofedBrand())));
    }

    private String getSpoofedBrand() {
        return switch (brand.getValue()) {
            case "Forge" -> "forge";
            case "Fabric" -> "fabric";
            case "Lunar" -> "lunarclient";
            case "Badlion" -> "badlion";
            case "Geyser" -> "geyser";
            default -> "vanilla";
        };
    }
}