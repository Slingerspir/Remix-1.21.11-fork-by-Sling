package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;

@Setter
@Getter
@AllArgsConstructor
public class Render2DEvent extends Event {
    private final DrawContext context;
    private final float partialTicks;
}
