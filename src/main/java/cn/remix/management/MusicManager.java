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

    private final String apiUrl = "https://meting.mikus.ink/api";
    private String server = "netease";
    private String playlistId = "0";

    private final List<Song> playlist = new ArrayList<>();
    private final List<LrcLine> lyrics = new ArrayList<>();
    private final Map<Double, String> translation = new HashMap<>();
    private int currentIndex = -1;
    private boolean playing;
    private double progressSeconds;
    private double volume = 0.8;
    private boolean translationEnabled = true;
    private boolean autoAdvancing;
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

        public Song(String name, String artist, String url, String pic, String lrc) {
            this.name = name;
            this.artist = artist;
            this.url = url;
            this.pic = pic;
            this.lrc = lrc;
        }

        public String getName() { return name; }
        public String getArtist() { return artist; }
        public String getUrl() { return url; }
        public String getPic() { return pic; }
        public String getLrc() { return lrc; }
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
            List<Song> loaded = new ArrayList<>();
            try {
                String url = apiUrl + "?server=" + server + "&type=playlist&id=" + playlistId;
                loaded = parsePlaylist(fetch(url));
            } catch (Exception ignored) {
            }
            final List<Song> list = loaded;
            mc.execute(() -> {
                if (token != loadToken) return;
                playlist.clear();
                playlist.addAll(list);
                lyrics.clear();
                translation.clear();
                currentIndex = -1;
                playing = false;
                progressSeconds = 0;
                coverTexture = null;
                coverPalette = new int[]{0xFF34D399, 0xFF4CC9F0, 0xFF22D3EE};
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
                out.add(new Song(name, artist, url, pic, lrc));
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
        playing = true;
        lastStartWall = System.currentTimeMillis();
        generation++;

        final Song song = playlist.get(index);
        final int track = index;

        FxMusicRuntime.showList(playlistJson(), index);
        FxMusicRuntime.showTrack(song.getName(), song.getArtist(), song.getPic(), index, playlist.size());
        FxMusicRuntime.play(song.getUrl(), progressSeconds, true, this::onTrackEnded);
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
        if (playlist.size() > 1 && System.currentTimeMillis() - lastStartWall < 800) {
            playing = false;
            FxMusicRuntime.stop();
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
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "Remix-Music-IO");
        t.setDaemon(true);
        t.start();
    }
}
