package cn.remix.util;

import cn.remix.Client;
import net.minecraft.client.MinecraftClient;

public interface IMinecraft {
    MinecraftClient mc = MinecraftClient.getInstance();
    Client instance = Client.instance;
}
