package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MotionEvent extends Event.StateEvent {
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean onGround;
    private boolean horizontalCollision;
}
