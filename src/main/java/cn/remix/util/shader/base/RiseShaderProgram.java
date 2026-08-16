package cn.remix.util.shader.base;

import cn.remix.util.shader.ShaderUtil;

public final class RiseShaderProgram {

    private final int programId;

    public RiseShaderProgram(String fragment, String vertex) {
        this.programId = ShaderUtil.createShader(fragment, vertex);
    }

    public void use() {
        if (programId != -1) {
            org.lwjgl.opengl.GL20.glUseProgram(programId);
        }
    }

    public static void stop() {
        org.lwjgl.opengl.GL20.glUseProgram(0);
    }

    // ✅ 添加静态方法
    public static void drawQuad(float x, float y, float w, float h) {
        ShaderUtil.drawQuad(x, y, w, h);
    }

    public int getProgramId() {
        return programId;
    }
}