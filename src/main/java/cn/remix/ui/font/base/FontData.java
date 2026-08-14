package cn.remix.ui.font.base;

import net.minecraft.util.Identifier;

public record FontData(Identifier atlasId, float u0, float v0, float u1, float v1, int width, int height, float advance, float offsetX, float offsetY) {}
