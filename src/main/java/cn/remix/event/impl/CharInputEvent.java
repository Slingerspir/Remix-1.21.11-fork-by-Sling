package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字符输入（对应原版 {@code Keyboard.onChar}），游戏内自绘界面要靠它做搜索框。 */
@Getter
@AllArgsConstructor
public class CharInputEvent extends Event {
    /** Unicode 码点 */
    private final int codepoint;

    public char asChar() {
        return (char) codepoint;
    }
}
