package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

@Getter
@Setter
@AllArgsConstructor
public class CobwebEvent extends Event {
    private final BlockState state;
    private final BlockPos pos;
}