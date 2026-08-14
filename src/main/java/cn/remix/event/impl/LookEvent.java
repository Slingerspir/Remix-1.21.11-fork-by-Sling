package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LookEvent extends Event {
    private float[] rotation;
    private float[] lastRotation;
}