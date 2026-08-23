package cn.remix.util.shader.impl;

import cn.remix.util.shader.base.RiseShaderProgram;
import cn.remix.util.shader.base.ShaderUniforms;
import cn.remix.util.shader.ShaderUtil;
import org.lwjgl.opengl.GL20;

import java.awt.Color;

public final class RGQShader {

    private final RiseShaderProgram program = new RiseShaderProgram("rgq.frag", "vertex.vsh");

    public void draw(float x, float y, float w, float h, float radius,
                     Color color1, Color color2, boolean horizontal) {
        int id = program.getProgramId();
        if (id == -1) return;

        program.use();

        ShaderUniforms.uniform2f(id, "u_size", w, h);
        ShaderUniforms.uniform1f(id, "u_radius", radius);
        ShaderUniforms.uniform4f(id, "u_first_color",
                color1.getRed() / 255.0f,
                color1.getGreen() / 255.0f,
                color1.getBlue() / 255.0f,
                color1.getAlpha() / 255.0f);
        ShaderUniforms.uniform4f(id, "u_second_color",
                color2.getRed() / 255.0f,
                color2.getGreen() / 255.0f,
                color2.getBlue() / 255.0f,
                color2.getAlpha() / 255.0f);
        ShaderUniforms.uniform1i(id, "u_direction", horizontal ? 1 : 0);
        ShaderUniforms.uniform4f(id, "u_edges", 1.0f, 1.0f, 1.0f, 1.0f);

        GL20.glEnable(GL20.GL_BLEND);
        GL20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        
        ShaderUtil.drawQuad(x, y, w, h);
        GL20.glDisable(GL20.GL_BLEND);
        RiseShaderProgram.stop();
    }
}