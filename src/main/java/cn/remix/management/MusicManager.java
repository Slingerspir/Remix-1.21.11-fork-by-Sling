package cn.remix.management;

import cn.remix.ui.music.FxMusicRuntime;
import cn.remix.util.IMinecraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 音乐数据层：歌单/歌词/翻译/封面全部异步获取，绝不阻塞游戏线程。
 * 实际播放统一委托给 JavaFX MediaPlayer（FxMusicRuntime）。
 * 可变字段只在 MC 线程读写（worker 解析到临时对象后 mc.execute 回投）。
 */
public final class MusicManager implements IMinecraft {
    private static MusicManager instance;

    public static MusicManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new MusicManager();
    }

    /** 公共 meting 实例经常挂/限流，做成可配置的，方便换成自建或别的镜像 */
    private String apiUrl = "https://meting.mikus.ink/api";
    private String server = "netease";
    private String playlistId = "0";

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        this.apiUrl = url.trim();
    }

    private final List<Song> playlist = new ArrayList<>();
    private final List<LrcLine> lyrics = new ArrayList<>();
    private final Map<Double, String> translation = new HashMap<>();
    private int currentIndex = -1;
    private boolean playing;
    private double progressSeconds;
    private double durationSeconds;
    private double volume = 0.8;
    private boolean translationEnabled = true;
    private boolean autoAdvancing;
    private int failureStreak;
    private long lastFailureAt;
    /** 因为限流没拉到歌单：解禁后自动重试一次 */
    private boolean reloadWanted;

    /** 每 tick 由模块驱动：限流解除后把之前失败的操作补上。 */
    public void tick() {
        if (reloadWanted && !isThrottled()) {
            reloadWanted = false;
            setSource(server, playlistId, playing);
        }
    }
    private long lastStartWall;
    private long generation;
    private int loadToken;

    private Identifier coverTexture;
    private int[] coverPalette = {0xFF34D399, 0xFF4CC9F0, 0xFF22D3EE};
    private boolean wantResume;
    private int resumeIndex = -1;
    private double resumeTime;
    private java.io.File stateFile;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final Pattern LRC = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]\\s*(.*)");

    public static final class Song {
        private final String name;
        private final String artist;
        private final String url;
        private final String pic;
        private final String lrc;
        /** 时长（秒）；0 = 未知。播放过之后会回填真实值 */
        private volatile double duration;
        private final String album;

        public Song(String name, String artist, String url, String pic, String lrc) {
            this(name, artist, url, pic, lrc, 0.0, "");
        }

        public Song(String name, String artist, String url, String pic, String lrc, double duration, String album) {
            this.name = name;
            this.artist = artist;
            this.url = url;
            this.pic = pic;
            this.lrc = lrc;
            this.duration = duration;
            this.album = album == null ? "" : album;
        }

        public String getName() { return name; }
        public String getArtist() { return artist; }
        public String getUrl() { return url; }
        public String getPic() { return pic; }
        public String getLrc() { return lrc; }
        public String getAlbum() { return album; }
        public double getDuration() { return duration; }
        public void setDuration(double seconds) { this.duration = seconds; }
    }

    public static final class LrcLine {
        private final double time;
        private final String text;

        public LrcLine(double time, String text) {
            this.time = time;
            this.text = text;
        }

        public double getTime() { return time; }
        public String getText() { return text; }
    }

    public List<Song> getPlaylist() { return playlist; }
    public List<LrcLine> getLyrics() { return lyrics; }
    public Map<Double, String> getTranslation() { return translation; }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isPlaying() { return playing; }
    public String getServer() { return server; }
    public String getPlaylistId() { return playlistId; }
    public Identifier getCoverTexture() { return coverTexture; }
    public int[] getCoverPalette() { return coverPalette; }
    public double getVolume() { return volume; }
    public long getGeneration() { return generation; }

    public void setTranslationEnabled(boolean on) {
        translationEnabled = on;
    }

    public boolean isTranslationEnabled() {
        return translationEnabled;
    }

    /** 取 time 附近（+0.3s）最匹配的翻译。 */
    public String translationAt(double time) {
        double bestKey = Double.NEGATIVE_INFINITY;
        String best = null;
        for (Map.Entry<Double, String> e : translation.entrySet()) {
            if (e.getKey() <= time + 0.3 && e.getKey() > bestKey) {
                bestKey = e.getKey();
                best = e.getValue();
            }
        }
        return best;
    }

    // ---------------- 歌单加载（异步） ----------------

    public void setSource(String server, String playlistId) {
        setSource(server, playlistId, false);
    }

    public void setSource(String server, String playlistId, boolean autoplay) {
        this.server = server;
        this.playlistId = playlistId;
        FxMusicRuntime.ensureStarted();
        FxMusicRuntime.stop();

        final int token = ++loadToken;
        runAsync(() -> {
            String url = apiUrl + "?server=" + server + "&type=playlist&id=" + playlistId;
            String json = null;
            try {
                json = get(url);
            } catch (Exception ignored) {
            }
            // 拿不到响应（限流 / 网络失败）就保持现状，绝不能把已有歌单清空
            final String body = json;
            mc.execute(() -> {
                if (token != loadToken) return;
                if (body == null) {
                    if (isThrottled()) {
                        reloadWanted = true;   // 解禁后自动重来一次
                        chat("接口限流中，约 " + getThrottleSeconds() + " 秒后自动重试（" + getThrottleMessage() + "）");
                    } else {
                        chat("歌单请求失败，请检查网络或接口地址");
                    }
                    return;
                }
                List<Song> list = parsePlaylist(body);
                playlist.clear();
                playlist.addAll(list);
                lyrics.clear();
                translation.clear();
                currentIndex = -1;
                playing = false;
                progressSeconds = 0;
                coverTexture = null;
                coverPalette = new int[]{0xFF34D399, 0xFF4CC9F0, 0xFF22D3EE};
                resetCaches();
                generation++;
                FxMusicRuntime.showList(playlistJson(), -1);
                if (wantResume && resumeIndex >= 0 && resumeIndex < playlist.size()) {
                    int ri = resumeIndex;
                    double rt = resumeTime;
                    wantResume = false;
                    resumeIndex = -1;
                    resumeTime = 0;
                    playIndex(ri, rt);
                } else if (autoplay && !playlist.isEmpty()) {
                    playIndex(0);
                }
            });
        });
    }

    // ---------------- 记忆/续播 ----------------

    /** 若存档与当前配置一致则恢复上次进度（总是先重载歌单）。返回是否命中。 */
    public boolean doResume(String cfgServer, String cfgId) {
        JsonObject o = loadState();
        if (o == null) return false;
        String s = o.has("server") ? o.get("server").getAsString() : null;
        String id = o.has("id") ? o.get("id").getAsString() : null;
        if (s == null || id == null || !s.equals(cfgServer) || !id.equals(cfgId)) return false;
        wantResume = true;
        resumeIndex = o.has("index") ? o.get("index").getAsInt() : 0;
        resumeTime = o.has("time") ? o.get("time").getAsDouble() : 0;
        server = s;
        playlistId = id;
        setSource(s, id, false);
        return true;
    }

    /** 保存当前进度（切歌时自动调用；模块也会节流调用）。 */
    public void rememberNow() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("server", server);
            o.addProperty("id", playlistId);
            o.addProperty("index", Math.max(0, currentIndex));
            o.addProperty("time", Math.round(getProgress() * 100.0) / 100.0);
            java.io.File f = stateFile();
            if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public void clearResume() {
        try {
            stateFile().delete();
        } catch (Exception ignored) {
        }
    }

    private JsonObject loadState() {
        try {
            java.io.File f = stateFile();
            if (!f.exists()) return null;
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private java.io.File stateFile() {
        if (stateFile == null) {
            try {
                stateFile = FabricLoader.getInstance().getConfigDir().resolve("remix_music.json").toFile();
            } catch (Throwable t) {
                stateFile = new java.io.File(System.getProperty("user.home"), "remix_music.json");
            }
        }
        return stateFile;
    }

    private List<Song> parsePlaylist(String json) {
        List<Song> out = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String name = o.has("title") ? o.get("title").getAsString()
                        : (o.has("name") ? o.get("name").getAsString() : "未知");
                String artist = o.has("author") ? o.get("author").getAsString()
                        : (o.has("artist") ? o.get("artist").getAsString() : "");
                String url = o.has("url") ? o.get("url").getAsString() : "";
                String pic = o.has("pic") ? o.get("pic").getAsString() : "";
                String lrc = o.has("lrc") ? o.get("lrc").getAsString() : "";
                String album = o.has("album") ? o.get("album").getAsString() : "";
                double dur = 0;
                for (String key : new String[]{"duration", "interval", "length", "time"}) {
                    if (!o.has(key)) continue;
                    try {
                        double v = o.get(key).getAsDouble();
                        if (v > 10000) v /= 1000.0;   // 有的接口给毫秒
                        if (v > 0 && v < 7200) dur = v;
                        break;
                    } catch (Exception ignored) {
                    }
                }
                out.add(new Song(name, artist, url, pic, lrc, dur, album));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------------- 播放控制 ----------------

    public void playIndex(int index) {
        playIndex(index, 0);
    }

    public void playIndex(int index, double seekSeconds) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        progressSeconds = Math.max(0, seekSeconds);
        durationSeconds = 0;   // 新歌时长未知，等媒体解析出来
        playing = true;
        lastStartWall = System.currentTimeMillis();
        generation++;

        final Song song = playlist.get(index);
        final int track = index;

        FxMusicRuntime.showList(playlistJson(), index);
        FxMusicRuntime.showTrack(song.getName(), song.getArtist(), song.getPic(), index, playlist.size());
        FxMusicRuntime.play(song.getUrl(), progressSeconds, true, this::onTrackEnded, this::onTrackFailed);
        rememberNow();

        // 歌词/翻译/封面异步加载，不阻塞游戏线程
        runAsync(() -> {
            final List<LrcLine> lyr = new ArrayList<>();
            final Map<Double, String> trans = new HashMap<>();
            try {
                String lrcUrl = song.getLrc();
                if (lrcUrl != null && !lrcUrl.isEmpty()) {
                    parseLrc(lyr, fetch(lrcUrl));
                    if (translationEnabled && !lyr.isEmpty()) {
                        trans.putAll(fetchTranslation(song, lrcUrl));
                    }
                }
            } catch (Exception ignored) {
            }

            NativeImage coverImg = null;
            int[] palette = null;
            try {
                String pic = song.getPic();
                if (pic != null && !pic.isEmpty()) {
                    String ref = server != null && server.equals("tencent") ? "https://y.qq.com/" : "https://music.163.com/";
                    HttpRequest req = HttpRequest.newBuilder(URI.create(pic))
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", ref)
                            .GET().build();
                    byte[] bytes = http.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
                    if (bytes != null && bytes.length > 0) {
                        coverImg = readImage(bytes);
                        if (coverImg != null) palette = dominantColors(coverImg);
                    }
                }
            } catch (Exception ignored) {
            }

            final NativeImage img = coverImg;
            final int[] pal = palette;
            mc.execute(() -> applyTrackLoaded(track, lyr, trans, img, pal));
        });
    }

    private void applyTrackLoaded(int track, List<LrcLine> lyr, Map<Double, String> trans, NativeImage img, int[] pal) {
        if (track != currentIndex) {
            if (img != null) img.close();
            return;
        }
        lyrics.clear();
        lyrics.addAll(lyr);
        lyrics.sort((a, b) -> Double.compare(a.getTime(), b.getTime()));
        translation.clear();
        translation.putAll(trans);

        if (img != null) {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "remix_music_cover", img);
            Identifier id = Identifier.of("remix", "textures/music/cover_" + System.currentTimeMillis());
            mc.getTextureManager().registerTexture(id, texture);
            texture.upload();
            coverTexture = id;
            if (pal != null) coverPalette = pal;
        }
    }

    public void togglePlay() {
        if (playlist.isEmpty()) return;
        if (playing) pause();
        else resume();
    }

    public void pause() {
        playing = false;
        progressSeconds = getProgress();
        FxMusicRuntime.pause();
    }

    public void resume() {
        if (currentIndex < 0 || playlist.isEmpty()) return;
        playing = true;
        FxMusicRuntime.resume();
    }

    public void next() {
        if (playlist.isEmpty()) return;
        playIndex((currentIndex + 1) % playlist.size());
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        int idx = currentIndex - 1;
        if (idx < 0) idx = playlist.size() - 1;
        playIndex(idx);
    }

    public void seek(double seconds) {
        if (currentIndex < 0 || playlist.isEmpty()) return;
        progressSeconds = Math.max(0, seconds);
        FxMusicRuntime.seek(progressSeconds);
    }

    public void stop() {
        playing = false;
        progressSeconds = 0;
        FxMusicRuntime.stop();
    }

    public void setVolume(double v) {
        volume = Math.max(0, Math.min(1, v));
        FxMusicRuntime.setVolume(volume);
    }

    private void onTrackEnded() {
        if (!playing || playlist.isEmpty()) {
            playing = false;
            return;
        }
        // 才开始 0.8 秒就"播完"了，说明这首媒体本身有问题（时长 0 / 流被打断），
        // 交给失败流程去重试或跳过，而不是直接把整个播放器停掉
        if (playlist.size() > 1 && System.currentTimeMillis() - lastStartWall < 800) {
            onTrackFailed();
            return;
        }
        if (autoAdvancing) return;
        autoAdvancing = true;
        try {
            playIndex((currentIndex + 1) % playlist.size());
        } finally {
            autoAdvancing = false;
        }
    }

    /**
     * 这首真的播不了（FX 侧已经原地重试过两次）。
     * 换下一首再试，但短时间内连续失败就停下来，避免坏掉的歌单被一路刷过去。
     */
    private void onTrackFailed() {
        long now = System.currentTimeMillis();
        failureStreak = (now - lastFailureAt < 4000) ? failureStreak + 1 : 1;
        lastFailureAt = now;

        if (failureStreak >= 3 || playlist.size() <= 1) {
            failureStreak = 0;
            playing = false;
            FxMusicRuntime.stop();
            chat("多首歌曲播放失败，已停止播放");
            return;
        }
        String name = currentIndex >= 0 && currentIndex < playlist.size() ? playlist.get(currentIndex).getName() : "当前歌曲";
        chat("《" + name + "》播放失败，已跳过");
        if (autoAdvancing) return;
        autoAdvancing = true;
        try {
            playIndex((currentIndex + 1) % playlist.size());
        } finally {
            autoAdvancing = false;
        }
    }

    private void chat(String message) {
        try {
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("§b[音乐] §f" + message), false);
            }
        } catch (Throwable ignored) {
        }
    }

    public double getProgress() {
        if (FxMusicRuntime.isRunning()) {
            double now = FxMusicRuntime.getNowSeconds();
            if (now >= 0) {
                progressSeconds = now;
                return now;
            }
        }
        return progressSeconds;
    }

    /**
     * 当前歌曲的真实时长（秒），来自 JavaFX MediaPlayer 解析出的媒体时长。
     * 还没解析出来时返回 0（界面应显示 --:-- 而不是编一个假时长）。
     */
    public double getDuration() {
        if (FxMusicRuntime.isRunning()) {
            double d = FxMusicRuntime.getDurationSeconds();
            if (d > 0) {
                durationSeconds = d;
                // 回填到歌单，这样列表里也能显示真实时长
                if (currentIndex >= 0 && currentIndex < playlist.size()) {
                    Song song = playlist.get(currentIndex);
                    if (Math.abs(song.getDuration() - d) > 1.0) song.setDuration(d);
                }
                return d;
            }
        }
        return durationSeconds;
    }

    // ---------------- 歌词 / 翻译 ----------------

    private static void parseLrc(List<LrcLine> out, String lrcText) {
        if (lrcText == null) return;
        for (String line : lrcText.split("\n")) {
            Matcher m = LRC.matcher(line);
            while (m.find()) {
                double min = Double.parseDouble(m.group(1));
                double sec = Double.parseDouble(m.group(2));
                double frac = m.group(3) != null ? Double.parseDouble(m.group(3)) / (m.group(3).length() == 2 ? 100.0 : 1000.0) : 0;
                String text = m.group(4);
                if (text != null && !text.isEmpty()) {
                    out.add(new LrcLine(min * 60 + sec + frac, text));
                }
            }
        }
    }

    private Map<Double, String> fetchTranslation(Song song, String lrcUrl) {
        Map<Double, String> out = new HashMap<>();
        try {
            String mid = extractId(lrcUrl, song);
            if (mid == null) return out;
            String transText = null;
            if (server != null && server.equals("tencent")) {
                // QQ：vkeys 返回 data.trans（带时间戳歌词文本）
                JsonObject root = JsonParser.parseString(fetch("https://api.vkeys.cn/v2/music/tencent/lyric?mid=" + mid)).getAsJsonObject();
                JsonObject data = root.has("data") ? root.getAsJsonObject("data") : null;
                if (data != null && data.has("trans") && data.get("trans").isJsonPrimitive()) {
                    transText = data.get("trans").getAsString();
                }
            } else {
                // 网易：官方接口顶层 tlyric.lyric（tv=-1 请求翻译）
                String body = fetch("https://music.163.com/api/song/lyric?id=" + mid + "&lv=-1&kv=-1&tv=-1");
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                if (root.has("tlyric") && root.get("tlyric").isJsonObject()) {
                    JsonObject tl = root.getAsJsonObject("tlyric");
                    if (tl.has("lyric")) transText = tl.get("lyric").getAsString();
                }
            }
            if (transText != null) {
                List<LrcLine> lines = new ArrayList<>();
                parseLrc(lines, transText);
                for (LrcLine l : lines) {
                    out.put(Math.round(l.getTime() * 100.0) / 100.0, l.getText());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String extractId(String lrcUrl, Song song) {
        // 十/网易 meting lrc url 都带 id=；QQ 有时 id 是 mid
        if (lrcUrl != null && lrcUrl.contains("id=")) {
            int i = lrcUrl.indexOf("id=") + 3;
            int end = lrcUrl.indexOf('&', i);
            return end == -1 ? lrcUrl.substring(i) : lrcUrl.substring(i, end);
        }
        return null;
    }

    // ---------------- 封面解码 ----------------

    private static NativeImage readImage(byte[] bytes) {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Throwable ignored) {
        }
        try {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            return NativeImage.read(out.toByteArray());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 封面主色（5bit 量化直方图，忽略暗/透明），3 色。 */
    private static int[] dominantColors(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int sx = Math.max(1, w / 26);
        int sy = Math.max(1, h / 26);
        Map<Integer, Integer> cnt = new HashMap<>();
        for (int y = 0; y < h; y += sy) {
            for (int x = 0; x < w; x += sx) {
                int c = img.getColorArgb(x, y);
                if (((c >>> 24) & 255) < 30) continue;
                int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
                if (r + g + b < 70) continue;
                int key = ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
                cnt.merge(key, 1, Integer::sum);
            }
        }
        int[] keys = new int[]{-1, -1, -1};
        int[] vals = new int[]{0, 0, 0};
        for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            int k = e.getKey();
            int v = e.getValue();
            if (v > vals[0]) {
                vals[2] = vals[1]; keys[2] = keys[1];
                vals[1] = vals[0]; keys[1] = keys[0];
                vals[0] = v; keys[0] = k;
            } else if (v > vals[1]) {
                vals[2] = vals[1]; keys[2] = keys[1];
                vals[1] = v; keys[1] = k;
            } else if (v > vals[2]) {
                vals[2] = v; keys[2] = k;
            }
        }
        int[] def = {0xFF34D399, 0xFF4CC9F0, 0xFF22D3EE};
        int[] res = new int[3];
        for (int i = 0; i < 3; i++) {
            if (keys[i] < 0) {
                res[i] = def[i];
            } else {
                int r = ((keys[i] >> 10) & 31) << 3;
                int g = ((keys[i] >> 5) & 31) << 3;
                int b = (keys[i] & 31) << 3;
                res[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return res;
    }

    // ---------------- UI 数据 ----------------

    private String playlistJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < playlist.size(); i++) {
            if (i > 0) sb.append(',');
            Song s = playlist.get(i);
            sb.append("{\"n\":").append(esc(s.getName()))
                    .append(",\"a\":").append(esc(s.getArtist())).append('}');
        }
        return sb.append(']').toString();
    }

    private static String esc(String s) {
        if (s == null) return "\"\"";
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

    private String fetch(String url) throws Exception {
        return get(url);
    }

    // ===================== 请求闸门（限流保护）=====================

    /** 被接口限流时，这段时间内不再发任何请求 */
    private volatile long throttledUntil;
    private volatile String throttleMessage = "";

    public boolean isThrottled() {
        return System.currentTimeMillis() < throttledUntil;
    }

    /** 距离解禁还有多少秒。 */
    public long getThrottleSeconds() {
        return Math.max(0L, (throttledUntil - System.currentTimeMillis()) / 1000L);
    }

    public String getThrottleMessage() {
        return throttleMessage;
    }

    /** 手动解除限流（换了接口地址时用）。 */
    public void clearThrottle() {
        throttledUntil = 0L;
        throttleMessage = "";
    }

    private void noteThrottle(int retryAfterSeconds, String message) {
        // 上限一小时：接口偶尔会返回离谱的 retryAfter，封太久不如定期再试
        long seconds = Math.max(1, Math.min(retryAfterSeconds, 3600));
        throttledUntil = System.currentTimeMillis() + seconds * 1000L;
        throttleMessage = message == null || message.isEmpty() ? "接口限流" : message;
    }

    /**
     * 统一出口：GET 一个 URL 并返回响应体。
     * 识别 429（公共 meting 接口被刷爆时会返回带 retryAfter 的限流 JSON），
     * 一旦限流就把整个数据层闸住 —— 否则后续连歌单都会被限流打断。
     * 返回 null 表示这次没拿到数据（被限流或请求失败）。
     */
    private String get(String url) throws Exception {
        if (isThrottled()) return null;
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        int code = resp.statusCode();
        if (code == 200) return body;
        // 只有明确的限流才闸住整个数据层；翻译接口偶尔 404 之类的不能连累别人
        if (code == 429 || code == 503) {
            int retry = 60;
            String msg = "接口限流";
            try {
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                if (o.has("retryAfter")) retry = o.get("retryAfter").getAsInt();
                if (o.has("message")) msg = o.get("message").getAsString();
            } catch (Exception ignored) {
            }
            noteThrottle(retry, msg);
        }
        return null;
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "Remix-Music-IO");
        t.setDaemon(true);
        t.start();
    }

    // ===================== 列表缩略图 =====================

    private final Map<String, Identifier> thumbnails = new HashMap<>();
    private final java.util.Set<String> thumbsPending = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private java.util.concurrent.ExecutorService thumbPool;

    /** 取列表行用的小封面；还没下载好就返回 null，并在后台排队下载。 */
    public Identifier getThumbnail(String url) {
        if (url == null || url.isEmpty()) return null;
        Identifier id = thumbnails.get(url);
        if (id != null) return id;
        if (isThrottled()) return null;   // 被限流时连封面请求也停掉
        if (thumbsPending.size() < 80 && thumbsPending.add(url)) {
            ensurePool();
            thumbPool.execute(() -> downloadThumb(url));
        }
        return null;
    }

    private void ensurePool() {
        if (thumbPool != null) return;
        thumbPool = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Remix-MusicThumb");
            t.setDaemon(true);
            return t;
        });
    }

    private void downloadThumb(String url) {
        NativeImage img = null;
        try {
            String ref = "tencent".equals(server) ? "https://y.qq.com/" : "https://music.163.com/";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", ref)
                    .GET().build();
            byte[] bytes = http.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
            if (bytes != null && bytes.length > 0) img = readImage(bytes);
        } catch (Throwable ignored) {
        }
        final NativeImage image = img;
        mc.execute(() -> {
            thumbsPending.remove(url);
            if (image == null) return;
            try {
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "remix_music_thumb", image);
                Identifier id = Identifier.of("remix", "textures/music/thumb_" + Integer.toHexString(url.hashCode()));
                mc.getTextureManager().registerTexture(id, tex);
                tex.upload();
                thumbnails.put(url, id);
            } catch (Throwable t) {
                try {
                    image.close();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // ===================== 歌词索引（搜索用）=====================

    /** 歌单下标 → 小写歌词全文 */
    private final Map<Integer, String> lyricIndex = new HashMap<>();
    private volatile int lyricIndexDone;
    private volatile int lyricIndexTotal;
    private volatile long lyricIndexVersion;
    private boolean lyricIndexRunning;
    /** 歌词索引最多抓多少首（公共接口限流很凶，别贪） */
    private static final int LYRIC_INDEX_CAP = 40;
    /** 每首之间的间隔，压住请求频率换稳定 */
    private static final long LYRIC_INDEX_INTERVAL_MS = 700L;
    /** 歌词搜索开关：关掉就不发任何索引请求 */
    private boolean lyricSearchEnabled = true;

    public void setLyricSearchEnabled(boolean enabled) {
        this.lyricSearchEnabled = enabled;
    }

    /**
     * 搜索歌单：歌名 / 歌手 / 专辑直接命中，歌词必须等后台索引抓到才参与匹配。
     * 返回的是歌单下标。
     */
    public List<Integer> search(String query) {
        List<Integer> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            for (int i = 0; i < playlist.size(); i++) out.add(i);
            return out;
        }
        for (int i = 0; i < playlist.size(); i++) {
            Song s = playlist.get(i);
            if (s.getName().toLowerCase().contains(q)
                    || s.getArtist().toLowerCase().contains(q)
                    || s.getAlbum().toLowerCase().contains(q)) {
                out.add(i);
                continue;
            }
            String lrc = lyricIndex.get(i);
            if (lrc != null && lrc.contains(q)) out.add(i);
        }
        return out;
    }

    /** 索引进度（已完成的歌数 / 总数），界面用它显示"正在索引歌词"。 */
    public int getLyricIndexDone() { return lyricIndexDone; }

    public int getLyricIndexTotal() { return lyricIndexTotal; }

    /** 索引每更新一批就 +1，界面据此重建结果列表。 */
    public long getLyricIndexVersion() { return lyricIndexVersion; }

    public boolean isLyricIndexing() { return lyricIndexRunning; }

    /**
     * 后台抓取歌单歌词做索引。
     *
     * <p><b>必须严格限速</b>：公共 meting 实例按 IP 限流，之前并发狂抓 300 首直接把这个 IP
     * 打成 429（retryAfter 近一小时），连歌单都拉不动。现在改成单线程顺序抓、每首之间留
     * {@link #LYRIC_INDEX_INTERVAL_MS} 毫秒、只索引前 {@link #LYRIC_INDEX_CAP} 首，
     * 一旦被限流立刻停下；等解禁后用户再搜一次会接着索引。
     */
    public void ensureLyricIndex() {
        if (!lyricSearchEnabled || lyricIndexRunning || playlist.isEmpty() || isThrottled()) return;
        lyricIndexRunning = true;

        final int cap = Math.min(playlist.size(), LYRIC_INDEX_CAP);
        lyricIndexTotal = cap;
        final List<int[]> keep = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        for (int i = 0; i < cap; i++) {
            String url = playlist.get(i).getLrc();
            if (url == null || url.isEmpty() || lyricIndex.containsKey(i)) continue;
            keep.add(new int[]{i});
            urls.add(url);
        }
        if (keep.isEmpty()) {
            lyricIndexRunning = false;
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                for (int n = 0; n < keep.size(); n++) {
                    if (isThrottled()) break;      // 被限流就收手，别再火上浇油
                    String text = null;
                    try {
                        text = fetch(urls.get(n));
                    } catch (Throwable ignored) {
                    }
                    if (text != null && !text.isEmpty()) {
                        final int idx = keep.get(n)[0];
                        final String t = text.toLowerCase();
                        mc.execute(() -> {
                            lyricIndex.put(idx, t);
                            lyricIndexVersion++;
                        });
                    }
                    mc.execute(() -> {
                        lyricIndexDone++;
                        lyricIndexVersion++;
                    });
                    try {
                        Thread.sleep(LYRIC_INDEX_INTERVAL_MS);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } finally {
                mc.execute(() -> lyricIndexRunning = false);
            }
        }, "Remix-MusicLrc");
        worker.setDaemon(true);
        worker.start();
    }

    /** 换歌单时清空索引与缩略图缓存。 */
    private void resetCaches() {
        lyricIndex.clear();
        lyricIndexDone = 0;
        lyricIndexTotal = 0;
        lyricIndexVersion++;
        lyricIndexRunning = false;
        for (Identifier id : thumbnails.values()) {
            try {
                mc.getTextureManager().destroyTexture(id);
            } catch (Throwable ignored) {
            }
        }
        thumbnails.clear();
        thumbsPending.clear();
    }
}
