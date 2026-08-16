package cn.remix.util.shader.impl;

import cn.remix.util.shader.base.RiseShaderProgram;
import cn.remix.util.shader.base.ShaderUniforms;
import org.lwjgl.opengl.GL20;

import java.awt.Color;

public final class RQShader {

    private final RiseShaderProgram program = new RiseShaderProgram("rq.frag", "vertex.vsh");

    public void draw(float x, float y, float w, float h, float radius, Color color) {
        if (color == null || program.getProgramId() == -1) {
            return;
        }

        int id = program.getProgramId();
        program.use();

        ShaderUniforms.uniform2f(id, "u_size", w, h);
        ShaderUniforms.uniform1f(id, "u_radius", radius);
        ShaderUniforms.uniform4f(id, "u_color",
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                color.getAlpha() / 255.0f
        );
        ShaderUniforms.uniform4f(id, "u_edges", 1.0f, 1.0f, 1.0f, 1.0f);

        GL20.glEnable(GL20.GL_BLEND);
        GL20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        cn.remix.util.shader.ShaderUtil.drawQuad(x, y, w, h);
        GL20.glDisable(GL20.GL_BLEND);
        RiseShaderProgram.stop();
    }
}