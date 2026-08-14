package cn.remix.util.render;

import cn.remix.util.IMinecraft;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

@UtilityClass
public final class Render3D implements IMinecraft {

    public void drawBox(MatrixStack stack, BlockPos pos, int color) {
        drawBox(stack, pos, color, false);
    }

    public void drawBox(MatrixStack stack, BlockPos pos, int color, boolean depthTest) {
        drawBox(stack, new Vec3d(pos.getX(), pos.getY(), pos.getZ()), color, depthTest);
    }

    public void drawBox(MatrixStack stack, Vec3d target, int color) {
        drawBox(stack, target, color, false);
    }

    public void drawBox(MatrixStack stack, Vec3d target, int color, boolean depthTest) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        Box box = new Box(
                target.x - cam.x,
                target.y - cam.y,
                target.z - cam.z,
                target.x + 1 - cam.x,
                target.y + 1 - cam.y,
                target.z + 1 - cam.z
        );

        fill(stack, box, color, depthTest);
    }

    public void drawBox(MatrixStack stack, Box box, int color) {
        drawBox(stack, box, color, false);
    }

    public void drawBox(MatrixStack stack, Box box, int color, boolean depthTest) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        fill(stack, box.offset(-cam.x, -cam.y, -cam.z), color, depthTest);
    }

    public void drawOutlinedBox(MatrixStack stack, Box box, double lineWidth, int color) {
        drawOutlinedBox(stack, box, lineWidth, color, false);
    }

    public void drawOutlinedBox(MatrixStack stack, Box box, double lineWidth, int color, boolean depthTest) {
        double t = Math.max(0.002, lineWidth);
        double h = t / 2.0;

        drawBox(stack, new Box(box.minX, box.minY - h, box.minZ - h, box.maxX, box.minY + h, box.minZ + h), color, depthTest);
        drawBox(stack, new Box(box.minX, box.minY - h, box.maxZ - h, box.maxX, box.minY + h, box.maxZ + h), color, depthTest);
        drawBox(stack, new Box(box.minX, box.maxY - h, box.minZ - h, box.maxX, box.maxY + h, box.minZ + h), color, depthTest);
        drawBox(stack, new Box(box.minX, box.maxY - h, box.maxZ - h, box.maxX, box.maxY + h, box.maxZ + h), color, depthTest);

        drawBox(stack, new Box(box.minX - h, box.minY, box.minZ - h, box.minX + h, box.maxY, box.minZ + h), color, depthTest);
        drawBox(stack, new Box(box.minX - h, box.minY, box.maxZ - h, box.minX + h, box.maxY, box.maxZ + h), color, depthTest);
        drawBox(stack, new Box(box.maxX - h, box.minY, box.minZ - h, box.maxX + h, box.maxY, box.minZ + h), color, depthTest);
        drawBox(stack, new Box(box.maxX - h, box.minY, box.maxZ - h, box.maxX + h, box.maxY, box.maxZ + h), color, depthTest);

        drawBox(stack, new Box(box.minX - h, box.minY - h, box.minZ, box.minX + h, box.minY + h, box.maxZ), color, depthTest);
        drawBox(stack, new Box(box.minX - h, box.maxY - h, box.minZ, box.minX + h, box.maxY + h, box.maxZ), color, depthTest);
        drawBox(stack, new Box(box.maxX - h, box.minY - h, box.minZ, box.maxX + h, box.minY + h, box.maxZ), color, depthTest);
        drawBox(stack, new Box(box.maxX - h, box.maxY - h, box.minZ, box.maxX + h, box.maxY + h, box.maxZ), color, depthTest);
    }

    public void drawLine(MatrixStack stack, Vec3d from, Vec3d to, int color) {
        drawLine(stack, from, to, color, false);
    }

    public void drawLine(MatrixStack stack, Vec3d from, Vec3d to, int color, boolean depthTest) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        Matrix4f mat = stack.peek().getPositionMatrix();

        buf.vertex(mat, (float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z)).color(color);
        buf.vertex(mat, (float) (to.x - cam.x), (float) (to.y - cam.y), (float) (to.z - cam.z)).color(color);

        (depthTest ? Pipelines.visibleLine : Pipelines.line).draw(buf.end());
    }

    private void fill(MatrixStack stack, Box box, int color, boolean depthTest) {
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f mat = stack.peek().getPositionMatrix();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        // bottom
        buf.vertex(mat, x0, y0, z0).color(color);
        buf.vertex(mat, x1, y0, z0).color(color);
        buf.vertex(mat, x1, y0, z1).color(color);
        buf.vertex(mat, x0, y0, z1).color(color);

        // top
        buf.vertex(mat, x0, y1, z0).color(color);
        buf.vertex(mat, x0, y1, z1).color(color);
        buf.vertex(mat, x1, y1, z1).color(color);
        buf.vertex(mat, x1, y1, z0).color(color);

        // north
        buf.vertex(mat, x0, y0, z0).color(color);
        buf.vertex(mat, x0, y1, z0).color(color);
        buf.vertex(mat, x1, y1, z0).color(color);
        buf.vertex(mat, x1, y0, z0).color(color);

        // south
        buf.vertex(mat, x0, y0, z1).color(color);
        buf.vertex(mat, x1, y0, z1).color(color);
        buf.vertex(mat, x1, y1, z1).color(color);
        buf.vertex(mat, x0, y1, z1).color(color);

        // west
        buf.vertex(mat, x0, y0, z0).color(color);
        buf.vertex(mat, x0, y0, z1).color(color);
        buf.vertex(mat, x0, y1, z1).color(color);
        buf.vertex(mat, x0, y1, z0).color(color);

        // east
        buf.vertex(mat, x1, y0, z0).color(color);
        buf.vertex(mat, x1, y1, z0).color(color);
        buf.vertex(mat, x1, y1, z1).color(color);
        buf.vertex(mat, x1, y0, z1).color(color);

        (depthTest ? Pipelines.visibleBox : Pipelines.box).draw(buf.end());
    }
}
