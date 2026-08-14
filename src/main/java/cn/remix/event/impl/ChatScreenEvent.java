package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;

@Getter
@Setter
@AllArgsConstructor
public class ChatScreenEvent extends Event {
    private DrawContext context;
    private int mouseX, mouseY;
}
