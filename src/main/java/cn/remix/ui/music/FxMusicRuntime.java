package cn.remix.ui.music;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * JavaFX 运行时门面：懒启动 FX 线程；把 MC 线程操作投递到 FX 线程。
 * 也承载"离屏 WebView → 帧输出"通道（叠加进游戏画面用）。
 * FX 未启动/启动失败时所有方法均为安全空操作。
 */
public final class FxMusicRuntime {
    /** FX 侧推给 MC 的一帧像素（ARGB）。 */
    public static final class Frame {
        public final int width;
        public final int height;
        public final int[] pixels;
        public final long version;

        public Frame(int width, int height, int[] pixels, long version) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.version = version;
        }
    }

    private static volatile MusicFxApp app;
    private static volatile boolean starting;
    private static volatile Throwable startupError;
    private static final Object LOCK = new Object();
    private static final CountDownLatch READY = new CountDownLatch(1);

    // 叠加层状态
    private static volatile boolean overlayVisible;
    private static volatile Frame lastFrame;

    private FxMusicRuntime() {}

    public static void ensureStarted() {
        if (app != null) return;
        synchronized (LOCK) {
            if (app != null || starting) return;
            starting = true;
        }
        Thread t = new Thread(() -> {
            try {
                javafx.application.Application.launch(MusicFxApp.class, new String[0]);
            } catch (Throwable e) {
                startupError = e;
                starting = false;
                READY.countDown();
                e.printStackTrace();
            }
        }, "Remix-FX");
        t.setDaemon(true);
        t.start();
    }

    static void register(MusicFxApp a) {
        app = a;
        starting = false;
        READY.countDown();
    }

    public static boolean isRunning() {
        return app != null;
    }

    public static Throwable getStartupError() {
        return startupError;
    }

    /** 在 FX 线程执行任务；若 FX 未就绪则等它就绪后异步执行。 */
    public static void runOnFx(Runnable task) {
        MusicFxApp a = app;
        if (a != null) {
            Platform.runLater(task);
            return;
        }
        ensureStarted();
        Thread t = new Thread(() -> {
            try {
                if (!READY.await(6, TimeUnit.SECONDS)) return;
            } catch (InterruptedException e) {
                return;
            }
            MusicFxApp x = app;
            if (x != null) Platform.runLater(task);
        }, "Remix-FX-wait");
        t.setDaemon(true);
        t.start();
    }

    // ===================== 叠加层（游戏内合成面板）=====================

    public static void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.setOverlayFx(visible);
        });
    }

    public static boolean isOverlayVisible() {
        return overlayVisible;
    }

    /** 最新一帧（ARGB），无则 null。 */
    public static Frame getFrame() {
        return lastFrame;
    }

    static void publishFrame(Frame f) {
        lastFrame = f;
    }

    /** 把游戏内的点击转发进 WebView（坐标为 WebView 虚拟坐标）。 */
    public static void forwardMouse(double nx, double ny, boolean press) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.mouseFx(nx, ny, press);
        });
    }

    /** 把滚轮转发进 WebView（滚动歌单列表）。 */
    public static void forwardScroll(double dyPixels) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.scrollFx(dyPixels);
        });
    }

    // ===================== 播放（投递 FX 线程操作 MediaPlayer）=====================

    public static void play(String url, double seekSeconds, boolean autoplay, Runnable ended) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.playFx(url, seekSeconds, autoplay, ended);
        });
    }

    public static void pause() {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.pauseFx();
        });
    }

    public static void resume() {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.resumeFx();
        });
    }

    public static void seek(double seconds) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.seekFx(seconds);
        });
    }

    public static void stop() {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.stopFx();
        });
    }

    public static void setVolume(double v) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.setVolumeFx(v);
        });
    }

    // ===================== 状态读取（volatile）=====================

    public static double getNowSeconds() {
        MusicFxApp a = app;
        return a != null ? a.getNowFx() : -1;
    }

    public static double getDurationSeconds() {
        MusicFxApp a = app;
        return a != null ? a.getDurationFx() : -1;
    }

    public static boolean isPlayingFx() {
        MusicFxApp a = app;
        return a != null && a.isPlayingFx();
    }

    // ===================== WebView UI 数据 =====================

    public static void showList(String listJson, int currentIndex) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.applyListFx(listJson, currentIndex);
        });
    }

    public static void showTrack(String title, String artist, String cover, int index, int size) {
        runOnFx(() -> {
            MusicFxApp a = app;
            if (a != null) a.applyTrackFx(title, artist, cover, index, size);
        });
    }
}
