package cn.remix.ui.music;

import cn.remix.management.MusicManager;
import cn.remix.util.IMinecraft;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import net.minecraft.client.MinecraftClient;

/**
 * 离屏 JavaFX WebView：不显示可见窗口，渲染面板 HTML；当 MC 需要时
 * 按 ~15fps 快照成 ARGB 帧并交给 FxMusicRuntime 输出，供 MC 合成进画面。
 * MediaPlayer 仍在此播放；MC 侧通过 FxMusicRuntime 投递输入。
 */
public final class MusicFxApp extends Application implements IMinecraft {
    public static final int PANEL_W = 400;
    public static final int PANEL_H = 560;

    private Stage stage;
    private WebView web;
    private WebEngine engine;
    private boolean uiReady;

    private volatile boolean overlayFx;
    private Timeline pump;
    private long frameVersion;

    // ---- 播放时钟（volatile，MC 线程直接读）----
    private volatile boolean playingFx;
    private volatile double baseSeconds;
    private volatile long playStartNanos;
    private volatile double durFx;
    private volatile double volumeFx = 0.8;
    /** 淡入淡出时真正下发给 MediaPlayer 的音量；-1 表示跟随 volumeFx */
    private volatile double fadeVolumeFx = -1;
    private Timeline fadeAnim;

    private javafx.scene.media.MediaPlayer player;
    private String currentUrl;
    private Runnable endedHandler;
    private Runnable failedHandler;
    /** 换歌令牌：回调靠它判断自己是不是"过期播放器"的回声 */
    private long playToken;
    private int retryCount;
    private double currentSeek;
    private boolean currentAutoplay;
    /** 同一首最多重试几次再判定失败 */
    private static final int MAX_RETRY = 2;

    // 最近一次 UI 数据（叠加层重新打开时补刷）
    private String lastListJson;
    private int lastListIdx = -1;
    private boolean hasList;
    private String lastTitle = "";
    private String lastArtist = "";
    private String lastCover = "";
    private int lastIndex;
    private int lastSize;
    private boolean hasTrack;

    @Override
    public void start(Stage primaryStage) {
        FxMusicRuntime.register(this);
        Platform.setImplicitExit(false);

        web = new WebView();
        web.setPrefSize(PANEL_W, PANEL_H);
        web.setContextMenuEnabled(false);
        engine = web.getEngine();

        StackPane root = new StackPane(web);
        Scene scene = new Scene(root, PANEL_W, PANEL_H);

        primaryStage.setTitle("Remix Music");
        primaryStage.setScene(scene);
        primaryStage.setX(-20000);
        primaryStage.setY(-20000);
        primaryStage.setOnCloseRequest((WindowEvent e) -> {
            e.consume();
            FxMusicRuntime.setOverlayVisible(false);
        });
        this.stage = primaryStage;

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                uiReady = true;
                bridgeFx();
                if (overlayFx) {
                    if (hasTrack) applyTrackFx(lastTitle, lastArtist, lastCover, lastIndex, lastSize);
                    if (hasList) applyListFx(lastListJson, lastListIdx);
                    startPump();
                }
            }
        });
        engine.loadContent(UI_HTML);
    }

    // ===================== 叠加层开关 =====================

    void setOverlayFx(boolean visible) {
        if (overlayFx == visible) return;
        overlayFx = visible;
        if (visible) {
            // 离屏显示以驱动渲染脉冲
            if (!stage.isShowing()) stage.show();
            stage.setX(-20000);
            stage.setY(-20000);
            if (uiReady) {
                if (hasTrack) applyTrackFx(lastTitle, lastArtist, lastCover, lastIndex, lastSize);
                if (hasList) applyListFx(lastListJson, lastListIdx);
            }
            startPump();
        } else {
            stopPump();
            if (stage.isShowing()) stage.hide();
        }
    }

    boolean isOverlayFx() {
        return overlayFx;
    }

    // ===================== 帧泵：快照 WebView 输出 =====================

    private void startPump() {
        if (pump != null) return;
        pump = new Timeline(new KeyFrame(Duration.millis(66), e -> pumpTick()));
        pump.setCycleCount(Animation.INDEFINITE);
        pump.play();
    }

    private void stopPump() {
        if (pump != null) {
            pump.stop();
            pump = null;
        }
    }

    private void pumpTick() {
        if (!overlayFx || !uiReady || stage.getScene() == null) return;
        syncClockFx();
        callJs("__time(" + timeJsonFx() + ")");
        try {
            WritableImage img = stage.getScene().snapshot(null);
            int w = (int) img.getWidth();
            int h = (int) img.getHeight();
            int[] px = new int[w * h];
            PixelReader pr = img.getPixelReader();
            pr.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
            FxMusicRuntime.publishFrame(new FxMusicRuntime.Frame(w, h, px, ++frameVersion));
        } catch (Throwable ignored) {
        }
    }

    // ===================== 输入转发 =====================

    void mouseFx(double nx, double ny, boolean press) {
        if (web == null) return;
        double x = Math.max(0, Math.min(PANEL_W - 1, nx));
        double y = Math.max(0, Math.min(PANEL_H - 1, ny));
        MouseEvent e;
        if (press) {
            e = new MouseEvent(MouseEvent.MOUSE_PRESSED, x, y, x, y, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, false, null);
        } else {
            e = new MouseEvent(MouseEvent.MOUSE_RELEASED, x, y, x, y, MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, true, false, false, null);
        }
        web.fireEvent(e);
    }

    void scrollFx(double dy) {
        callJs("var el=document.getElementById('list');if(el)el.scrollBy(0," + dy + ");");
    }

    // ===================== 播放 =====================

    void playFx(String url, double seekSeconds, boolean autoplay, Runnable ended) {
        playFx(url, seekSeconds, autoplay, ended, null);
    }

    void playFx(String url, double seekSeconds, boolean autoplay, Runnable ended, Runnable failed) {
        // 换歌令牌：旧播放器的异步回调（尤其是 onError）凭这个作废，绝不能影响新歌
        playToken++;
        retryCount = 0;
        fadeVolumeFx = -1;   // 新歌直接按目标音量播，不做淡入
        durFx = 0;           // 换歌了：旧时长立刻作废，等 setOnReady 拿到真实值
        endedHandler = ended;
        failedHandler = failed;
        currentSeek = seekSeconds;
        currentAutoplay = autoplay;
        startFx(url, playToken);
    }

    /** 建播放器并装载。retry 时会用同一个 token 再走一遍。 */
    private void startFx(String url, long token) {
        disposeFx();
        if (fadeAnim != null) {
            fadeAnim.stop();
            fadeAnim = null;
        }
        currentUrl = url;
        try {
            javafx.scene.media.Media media = new javafx.scene.media.Media(url);
            javafx.scene.media.MediaPlayer p = new javafx.scene.media.MediaPlayer(media);
            p.setVolume(volumeFx);
            // 回调全部带 token 校验：只有"当前这一轮"的播放器才有资格改状态
            p.setOnError(() -> handleError(token, url));
            p.setOnReady(() -> {
                if (token != playToken) return;
                Duration d = p.getMedia().getDuration();
                durFx = (d != null && !Duration.UNKNOWN.equals(d)) ? d.toSeconds() : 0;
                if (currentSeek > 0) p.seek(Duration.seconds(currentSeek));
                if (currentAutoplay) {
                    baseSeconds = Math.max(0, currentSeek);
                    playStartNanos = System.nanoTime();
                    playingFx = true;
                    p.play();
                } else {
                    playingFx = false;
                }
            });
            p.setOnEndOfMedia(() -> {
                if (token != playToken) return;
                playingFx = false;
                baseSeconds = durFx > 0 ? durFx : baseSeconds;
                Runnable h = endedHandler;
                if (h != null) mc.execute(h);
            });
            player = p;
        } catch (Throwable t) {
            // 构造就失败（URL 空/非法）也要走失败流程，别把状态卡住
            handleError(token, url);
        }
    }

    /**
     * 播放出错。分两种情况：
     * 1. token 不是当前轮次 —— 是被 dispose 掉的旧播放器在报错，直接丢掉。
     *    这正是"切歌偶尔放不出来还跳到下一首"的元凶：以前这里会去调用<b>当前</b>歌曲的
     *    ended 回调，于是新歌刚起就被当成"播完了"。
     * 2. 真的是当前这首出错 —— 先原地重试两次（很多失败只是加载被打断），
     *    仍不行才交给 failed 回调，由上层决定跳过还是停下。
     */
    private void handleError(long token, String url) {
        if (token != playToken) return;
        double resumeAt = getNowFx();   // 先记下播到哪了，重试时从这儿接上
        playingFx = false;
        if (retryCount < MAX_RETRY) {
            retryCount++;
            currentSeek = Math.max(currentSeek, resumeAt);
            final long t = token;
            PauseTransition wait = new PauseTransition(Duration.millis(400));
            wait.setOnFinished(e -> {
                if (t != playToken) return;   // 等的时候用户又切歌了
                startFx(url, t);
            });
            wait.play();
            return;
        }
        Runnable f = failedHandler;
        if (f != null) {
            mc.execute(f);
            return;
        }
        Runnable h = endedHandler;
        if (h != null) mc.execute(h);
    }

    void pauseFx() {
        if (player == null || !playingFx) return;
        // 先淡出再真正暂停，避免"啪"的断音
        fadeVolumeFx(0.0, () -> {
            syncClockFx();
            playingFx = false;
            if (player != null) player.pause();
        });
    }

    void resumeFx() {
        if (player == null) {
            String u = currentUrl;
            if (u != null) playFx(u, getNowFx(), true, endedHandler, failedHandler);
            return;
        }
        syncClockFx();
        playingFx = true;
        playStartNanos = System.nanoTime();
        fadeVolumeFx = 0.0;   // 从静音淡入
        applyVolumeFx();
        player.play();
        fadeVolumeFx(volumeFx, null);
    }

    void seekFx(double seconds) {
        baseSeconds = Math.max(0, seconds);
        if (playingFx) playStartNanos = System.nanoTime();
        if (player != null) player.seek(Duration.seconds(seconds));
    }

    void stopFx() {
        playToken++;   // 让还在排队的重试作废
        if (fadeAnim != null) {
            fadeAnim.stop();
            fadeAnim = null;
        }
        fadeVolumeFx = -1;
        playingFx = false;
        baseSeconds = 0;
        disposeFx();
    }

    void setVolumeFx(double v) {
        volumeFx = Math.max(0, Math.min(1, v));
        // 正在淡入淡出时只更新目标值，别把动画打断
        if (fadeAnim == null) applyVolumeFx();
    }

    private void applyVolumeFx() {
        if (player == null) return;
        double v = fadeVolumeFx >= 0 ? fadeVolumeFx : volumeFx;
        player.setVolume(Math.max(0, Math.min(1, v)));
    }

    /** 音量渐变：从当前实际音量滑到 to（约 260ms），结束后回调。 */
    private void fadeVolumeFx(double to, Runnable onDone) {
        if (fadeAnim != null) {
            fadeAnim.stop();
            fadeAnim = null;
        }
        final double from = fadeVolumeFx >= 0 ? fadeVolumeFx : volumeFx;
        final long start = System.nanoTime();
        final long durNs = 260_000_000L;
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            double p = Math.min(1.0, (System.nanoTime() - start) / (double) durNs);
            double eased = p * p * (3.0 - 2.0 * p);   // smoothstep
            fadeVolumeFx = from + (to - from) * eased;
            applyVolumeFx();
            if (p >= 1.0) {
                // 淡入结束就交还给 volumeFx；淡出到 0 则保持静音
                fadeVolumeFx = to > 0.0 ? -1.0 : 0.0;
                applyVolumeFx();
                if (fadeAnim != null) {
                    fadeAnim.stop();
                    fadeAnim = null;
                }
                if (onDone != null) onDone.run();
            }
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
        fadeAnim = tl;
    }

    private void syncClockFx() {
        if (player != null && playingFx) {
            Duration cur = player.getCurrentTime();
            if (cur != null && !Duration.UNKNOWN.equals(cur)) {
                baseSeconds = cur.toSeconds();
                playStartNanos = System.nanoTime();
            }
        }
    }

    private void disposeFx() {
        if (player != null) {
            try {
                // 关键：先把回调摘掉再 dispose。否则被丢弃的播放器仍可能在加载失败时
                // 异步回调进来，而回调读的是"当前"的 endedHandler，会把新歌误判成播完。
                player.setOnError(null);
                player.setOnReady(null);
                player.setOnEndOfMedia(null);
                player.stop();
                player.dispose();
            } catch (Throwable ignored) {
            }
            player = null;
        }
    }

    double getNowFx() {
        return playingFx ? baseSeconds + (System.nanoTime() - playStartNanos) / 1e9 : baseSeconds;
    }

    double getDurationFx() {
        // setOnReady 时若媒体还没解析出时长（Duration.UNKNOWN），这里再补读一次，
        // 直到拿到真正的曲目时长为止 —— 界面上的总时长就靠它
        if (durFx <= 0 && player != null) {
            try {
                Duration d = player.getMedia().getDuration();
                if (d != null && !Duration.UNKNOWN.equals(d) && d.toSeconds() > 0) {
                    durFx = d.toSeconds();
                }
            } catch (Throwable ignored) {
            }
        }
        return durFx;
    }

    boolean isPlayingFx() {
        return playingFx;
    }

    // ===================== WebView 数据推送 =====================

    private String timeJsonFx() {
        return "{\"t\":" + getNowFx() + ",\"d\":" + durFx + ",\"p\":" + playingFx + "}";
    }

    void applyListFx(String listJson, int currentIndex) {
        lastListJson = listJson;
        lastListIdx = currentIndex;
        hasList = true;
        if (uiReady) callJs("__list(" + listJson + "," + currentIndex + ")");
    }

    void applyTrackFx(String title, String artist, String cover, int index, int size) {
        lastTitle = title == null ? "" : title;
        lastArtist = artist == null ? "" : artist;
        lastCover = cover == null ? "" : cover;
        lastIndex = index;
        lastSize = size;
        hasTrack = true;
        if (!uiReady) return;
        callJs("__track({title:" + jq(lastTitle) + ",artist:" + jq(lastArtist) + ",cover:" + jq(lastCover)
                + ",index:" + index + ",size:" + size + "})");
    }

    private static String jq(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private void callJs(String js) {
        try {
            engine.executeScript(js);
        } catch (Throwable ignored) {
        }
    }

    private void bridgeFx() {
        try {
            Object win = engine.executeScript("window");
            if (win instanceof JSObject) {
                ((JSObject) win).setMember("fx", new JsBridge());
            }
        } catch (Throwable ignored) {
        }
    }

    /** JS 调用的桥。这些方法在 FX 线程执行，游戏操作转发到 MC 线程。 */
    public static final class JsBridge {
        private static void mc(Runnable r) {
            MinecraftClient.getInstance().execute(r);
        }

        public void prev() {
            MusicManager m = MusicManager.getInstance();
            if (m != null) mc(m::previous);
        }

        public void next() {
            MusicManager m = MusicManager.getInstance();
            if (m != null) mc(m::next);
        }

        public void toggle() {
            MusicManager m = MusicManager.getInstance();
            if (m != null) mc(m::togglePlay);
        }

        public void playIndex(int i) {
            MusicManager m = MusicManager.getInstance();
            if (m != null) mc(() -> m.playIndex(i));
        }

        public void seekTo(double seconds) {
            MusicManager m = MusicManager.getInstance();
            if (m != null) mc(() -> m.seek(seconds));
        }

        public void volume(double v) {
            MusicManager m = MusicManager.getInstance();
            if (m != null) mc(() -> m.setVolume(v));
        }

        public void closeWin() {
            FxMusicRuntime.setOverlayVisible(false);
        }
    }

    // ===================== HTML =====================

    private static final String UI_HTML = """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
            <meta charset="utf-8">
            <style>
            *{box-sizing:border-box}
            body{margin:0;background:rgba(13,16,20,.0);color:#e9edf4;font-family:'Segoe UI','Microsoft YaHei',sans-serif;overflow:hidden;height:100vh}
            .app{display:flex;flex-direction:column;height:100vh}
            .head{display:flex;gap:12px;padding:14px 14px 8px;align-items:center}
            .cov{width:60px;height:60px;border-radius:10px;background:#1b2230;flex:none;object-fit:cover}
            .meta{display:flex;flex-direction:column;justify-content:center;overflow:hidden;min-width:0}
            .idx{font-size:11px;color:#5c6578;margin-bottom:4px}
            .t1{font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:250px}
            .t2{font-size:12px;color:#8b93a7;margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:250px}
            .bar{height:6px;background:#1d2331;border-radius:3px;margin:4px 14px 2px;cursor:pointer;position:relative}
            .fill{height:100%;width:0;background:linear-gradient(90deg,#34d399,#22d3ee);border-radius:3px}
            .time{display:flex;justify-content:space-between;font-size:11px;color:#6c7689;padding:2px 14px}
            .btnrow{display:flex;justify-content:center;align-items:center;gap:4px;padding:2px 0 4px}
            .btn{width:44px;height:34px;border:none;background:transparent;color:#d7deeb;font-size:18px;cursor:pointer;border-radius:8px}
            .btn:hover{background:#1b2332}
            .btn.main{width:54px;height:42px;font-size:24px;color:#34d399}
            .vol{display:flex;align-items:center;gap:8px;padding:0 18px 6px}
            .vol input{flex:1;accent-color:#34d399}
            #list{flex:1;overflow-y:auto;padding:2px 6px 12px;border-top:1px solid #171d29}
            #list::-webkit-scrollbar{width:6px}
            #list::-webkit-scrollbar-thumb{background:#232c3d;border-radius:3px}
            .row{display:flex;gap:10px;align-items:center;padding:5px 8px;border-radius:8px;cursor:pointer}
            .row:hover{background:#151b27}
            .row.cur{background:#10261f}
            .no{width:20px;color:#5c6578;font-size:11px;text-align:right;flex:none}
            .row.cur .no{color:#34d399}
            .rn{font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .ra{font-size:11px;color:#8b93a7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}
            .rtxt{min-width:0;flex:1}
            </style>
            </head>
            <body>
            <div class="app">
              <div class="head">
                <img id="cov" class="cov" src="">
                <div class="meta">
                  <div class="idx" id="idx"></div>
                  <div class="t1" id="title">未播放</div>
                  <div class="t2" id="artist">Remix Music</div>
                </div>
              </div>
              <div class="bar" id="bar" onclick="seek(event)"><div class="fill" id="fill"></div></div>
              <div class="time"><span id="cur">0:00</span><span id="end">0:00</span></div>
              <div class="btnrow">
                <button class="btn" onclick="fx.prev()">&#9198;</button>
                <button class="btn main" id="playbtn" onclick="fx.toggle()">&#9654;</button>
                <button class="btn" onclick="fx.next()">&#9197;</button>
              </div>
              <div class="vol"><span>&#128266;</span><input id="vol" type="range" min="0" max="1" step="0.01" value="0.8" oninput="fx.volume(parseFloat(this.value))"></div>
              <div id="list"></div>
            </div>
            <script>
            function fmt(s){s=Math.max(0,Math.floor(s));return Math.floor(s/60)+':'+String(s%60).padStart(2,'0')}
            function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
            function __list(json,idx){
              var el=document.getElementById('list'),h='';
              json.forEach(function(s,i){
                var c=(i===idx)?'cur':'';
                h+="<div class='row "+c+"' onclick='fx.playIndex("+i+")'>"
                  +"<span class='no'>"+(i===idx?'&#9835;':(i+1))+"</span>"
                  +"<div class='rtxt'><div class='rn'>"+esc(s.n)+"</div>"
                  +(s.a?"<div class='ra'>"+esc(s.a)+"</div>":"")+"</div></div>";
              });
              el.innerHTML=h;
            }
            function __track(t){
              document.getElementById('title').textContent=t.title||'未播放';
              document.getElementById('artist').textContent=t.artist||'';
              document.getElementById('idx').textContent=(t.size>0)?((t.index+1)+'/'+t.size):'';
              if(t.cover)document.getElementById('cov').src=t.cover;
            }
            function __time(t){
              document.getElementById('cur').textContent=fmt(t.t);
              var fill=document.getElementById('fill');
              if(t.d>0){document.getElementById('end').textContent=fmt(t.d);fill.style.width=Math.min(100,t.t/t.d*100)+'%';}
              document.getElementById('playbtn').textContent=t.p?'&#10074;&#10074;':'&#9654;';
            }
            function seek(ev){
              var b=document.getElementById('bar'),r=b.getBoundingClientRect();
              var ratio=Math.min(1,Math.max(0,(ev.clientX-r.left)/r.width));
              fx.seekTo(ratio*100000);
            }
            </script>
            </body>
            </html>
            """;
}
