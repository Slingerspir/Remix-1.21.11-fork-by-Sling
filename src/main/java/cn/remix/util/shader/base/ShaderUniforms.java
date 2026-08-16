package cn.remix.util.shader.base;

import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public final class ShaderUniforms {

    private ShaderUniforms() {}

    public static void uniform1f(int program, String name, float v) {
        if (program == -1) return;
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1f(loc, v);
    }

    public static void uniform2f(int program, String name, float v1, float v2) {
        if (program == -1) return;
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform2f(loc, v1, v2);
    }

    public static void uniform4f(int program, String name, float v1, float v2, float v3, float v4) {
        if (program == -1) return;
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform4f(loc, v1, v2, v3, v4);
    }

    public static void uniform1i(int program, String name, int v) {
        if (program == -1) return;
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1i(loc, v);
    }

    public static void uniformFB(int program, String name, FloatBuffer buffer) {
        if (program == -1) return;
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc != -1) GL20.glUniform1fv(loc, buffer);
    }
}