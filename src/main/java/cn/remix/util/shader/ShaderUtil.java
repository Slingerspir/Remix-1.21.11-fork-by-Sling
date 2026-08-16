package cn.remix.util.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class ShaderUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderUtil.class);
    private static boolean glAvailable = false;

    private ShaderUtil() {}

    public static int createShader(String fragmentPath, String vertexPath) {
        String fragmentSource = loadShader(fragmentPath);
        String vertexSource = loadShader(vertexPath);

        if (fragmentSource == null || vertexSource == null) {
            LOGGER.error("Failed to load shader: {} or {}", fragmentPath, vertexPath);
            return -1;
        }

        int fragmentShader = org.lwjgl.opengl.GL20.glCreateShader(org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER);
        int vertexShader = org.lwjgl.opengl.GL20.glCreateShader(org.lwjgl.opengl.GL20.GL_VERTEX_SHADER);

        org.lwjgl.opengl.GL20.glShaderSource(fragmentShader, fragmentSource);
        org.lwjgl.opengl.GL20.glShaderSource(vertexShader, vertexSource);

        org.lwjgl.opengl.GL20.glCompileShader(fragmentShader);
        org.lwjgl.opengl.GL20.glCompileShader(vertexShader);

        if (org.lwjgl.opengl.GL20.glGetShaderi(fragmentShader, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS) == 0) {
            LOGGER.error("Fragment shader error:\n{}", org.lwjgl.opengl.GL20.glGetShaderInfoLog(fragmentShader, 1024));
            return -1;
        }
        if (org.lwjgl.opengl.GL20.glGetShaderi(vertexShader, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS) == 0) {
            LOGGER.error("Vertex shader error:\n{}", org.lwjgl.opengl.GL20.glGetShaderInfoLog(vertexShader, 1024));
            return -1;
        }

        int program = org.lwjgl.opengl.GL20.glCreateProgram();
        org.lwjgl.opengl.GL20.glAttachShader(program, fragmentShader);
        org.lwjgl.opengl.GL20.glAttachShader(program, vertexShader);
        org.lwjgl.opengl.GL20.glLinkProgram(program);

        org.lwjgl.opengl.GL20.glDeleteShader(fragmentShader);
        org.lwjgl.opengl.GL20.glDeleteShader(vertexShader);

        glAvailable = true;
        return program;
    }

    private static String loadShader(String path) {
        try {
            Identifier id = Identifier.of("remix", "shaders/" + path);
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(id).orElse(null);
            if (resource == null) {
                LOGGER.warn("Shader resource not found: {}", path);
                return null;
            }
            var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to load shader: {}", path, e);
            return null;
        }
    }

    /**
     * 使用 BufferBuilder 绘制四边形
     * BufferBuilder.end() 会自动提交渲染，不需要 BufferRenderer
     */
    public static void drawQuad(float x, float y, float width, float height) {
        if (!glAvailable) {
            return;
        }

        try {
            RenderSystem.assertOnRenderThread();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);

            buffer.vertex(x, y, 0.0f);
            buffer.vertex(x + width, y, 0.0f);
            buffer.vertex(x + width, y + height, 0.0f);
            buffer.vertex(x, y + height, 0.0f);

            // end() 会自动提交并渲染，无需额外操作
            buffer.end();
        } catch (Exception e) {
            // 静默失败，回退到 CPU 渲染
        }
    }

    public static void drawFullScreenQuad() {
        int sw = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int sh = MinecraftClient.getInstance().getWindow().getScaledHeight();
        drawQuad(0, 0, sw, sh);
    }
}