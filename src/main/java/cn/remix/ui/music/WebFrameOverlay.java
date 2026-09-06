package cn.remix.ui.music;

import cn.remix.util.IMinecraft;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * 把 FxMusicRuntime 输出的 ARGB 帧上传成 MC 纹理并绘制（叠加进游戏画面）。
 * 用 AWT 编码 PNG → NativeImage.read（stb 解码），路径稳妥。只在 MC 线程调用。
 */
public final class WebFrameOverlay implements IMinecraft {
    private final Identifier textureId = Identifier.of("remix", "web/frame");
    private NativeImageBackedTexture texture;
    private int width;
    private int height;
    private long lastVersion = -1;

    public void render(DrawContext context, float x, float y, float drawWidth, float drawHeight) {
        FxMusicRuntime.Frame frame = FxMusicRuntime.getFrame();
        if (frame == null) return;
        if (texture == null || frame.version != lastVersion || width != frame.width || height != frame.height) {
            update(frame);
        }
        if (texture != null) {
            Render2D.drawTexture(context, textureId, x, y, drawWidth, drawHeight);
        }
    }

    private void update(FxMusicRuntime.Frame frame) {
        int w = frame.width;
        int h = frame.height;
        NativeImage img;
        try {
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            bi.setRGB(0, 0, w, h, frame.pixels, 0, w);
            ByteArrayOutputStream out = new ByteArrayOutputStream(w * h);
            ImageIO.write(bi, "png", out);
            img = NativeImage.read(out.toByteArray());
            if (img.getWidth() != w || img.getHeight() != h) {
                img.close();
                return;
            }
        } catch (Throwable t) {
            return;
        }
        width = w;
        height = h;
        if (texture == null) {
            texture = new NativeImageBackedTexture(() -> "remix_web_overlay", img);
            mc.getTextureManager().registerTexture(textureId, texture);
            texture.upload();
        } else {
            NativeImage old = texture.getImage();
            texture.setImage(img);
            texture.upload();
            if (old != null && old != img) old.close();
        }
        lastVersion = frame.version;
    }
}
