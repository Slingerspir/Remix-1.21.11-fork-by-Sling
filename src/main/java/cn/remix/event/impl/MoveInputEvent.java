package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MoveInputEvent extends Event {
    private float forward;
    private float strafe;
    private boolean jumping;
    private boolean sneaking;
}