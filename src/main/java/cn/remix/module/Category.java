package cn.remix.module;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Category {
    Combat("Combat"),
    Exploits("Exploit"),
    Move("Move"),
    Player("Player"),
    World("World"),
    Misc("Misc"),
    Render("Render");

    public final String name;
}
