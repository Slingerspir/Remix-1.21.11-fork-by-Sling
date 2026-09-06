package cn.remix.util.shader.impl;

import cn.remix.util.IMinecraft;
import cn.remix.util.shader.ShaderUtil;
import cn.remix.util.shader.base.RiseShaderProgram;
import cn.remix.util.shader.base.ShaderUniforms;
import org.lwjgl.opengl.GL20;

import java.awt.*;

public final class FeatherShader implements IMinecraft {
    private final RiseShaderProgram program = new RiseShaderProgram("feather.frag", "vertex.vsh");

    /**
     * 绘制一个以矩形边缘为亮的环形羽化光晕。
     * @param centerX 屏幕缩放坐标中心 X
     * @param centerY 屏幕缩放坐标中心 Y
     * @param width   屏幕缩放宽度
     * @param height  屏幕缩放高度
     * @param radius  羽化半径（缩放像素，>0）
     * @param feather 内收羽化量（缩放像素，>=0）
     * @param color   光晕颜色（含 alpha）
     */
    public void draw(float centerX, float centerY, float width, float height, float radius, float feather, Color color) {
        if (program.getProgramId() == -1) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        float centerNdcX = (centerX / sw) * 2.0f - 1.0f;
        float centerNdcY = 1.0f - (centerY / sh) * 2.0f;
        float sizeNdcX = (width / sw) * 2.0f;
        float sizeNdcY = (height / sh) * 2.0f;
        float radiusNdc = (radius / Math.min(sw, sh)) * 2.0f;
        float featherNdc = (feather / Math.min(sw, sh)) * 2.0f;

        int vw = mc.getWindow().getFramebufferWidth();
        int vh = mc.getWindow().getFramebufferHeight();

        int id = program.getProgramId();
        program.use();

        ShaderUniforms.uniform2f(id, "u_viewport", vw, vh);
        ShaderUniforms.uniform2f(id, "u_center", centerNdcX, centerNdcY);
        ShaderUniforms.uniform2f(id, "u_size", sizeNdcX, sizeNdcY);
        ShaderUniforms.uniform1f(id, "u_radius", Math.max(0.0005f, radiusNdc));
        ShaderUniforms.uniform1f(id, "u_feather", Math.max(0.0f, featherNdc));
        ShaderUniforms.uniform4f(id, "u_color",
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                color.getAlpha() / 255.0f);

        GL20.glEnable(GL20.GL_BLEND);
        GL20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ShaderUtil.drawFullScreenQuad();
        GL20.glDisable(GL20.GL_BLEND);
        RiseShaderProgram.stop();
    }
}
