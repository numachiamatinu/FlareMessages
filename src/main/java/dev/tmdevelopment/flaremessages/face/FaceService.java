package dev.tmdevelopment.flaremessages.face;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.util.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads the skin async (never on the main thread), extracts the face (+ hat layer)
 * and caches it in memory and on disk (plugins/FlareMessages/faces).
 * Sources: profile texture (premium / SkinsRestorer) -> fallback URL by name -> Steve.
 */
public final class FaceService {

    private final FlareMessages plugin;
    private final Map<UUID, Face> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Face>> inflight = new ConcurrentHashMap<>();
    private final ExecutorService pool;
    private final File dir;

    private volatile String symbol = "█";
    private volatile String fallbackUrl = "https://mc-heads.net/skin/{name}";
    private volatile boolean useFallback = true;
    private volatile boolean overlay = true;
    private volatile long cacheMillis = TimeUnit.DAYS.toMillis(3);
    private volatile int timeoutMs = 4000;
    private volatile Face steve;

    public FaceService(FlareMessages plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "faces");
        AtomicInteger n = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FlareMessages-Face-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public void reload(ConfigurationSection cfg) {
        symbol = cfg.getString("face.symbol", "█");
        if (symbol == null || symbol.isEmpty()) symbol = "█";
        useFallback = cfg.getBoolean("face.fallback.enabled", true);
        fallbackUrl = cfg.getString("face.fallback.url", "https://mc-heads.net/skin/{name}");
        overlay = cfg.getBoolean("face.hat-layer", true);
        cacheMillis = TimeUnit.HOURS.toMillis(Math.max(0, cfg.getLong("face.cache-hours", 72)));
        timeoutMs = Math.max(500, cfg.getInt("face.timeout-ms", 4000));

        steve = new Face("steve", DefaultFaces.STEVE.clone(), 0L);
        steve.build(symbol);
        // the symbol may have changed: rebuild the rows
        for (Face f : cache.values()) f.build(symbol);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create the faces folder");
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    public Face steve() {
        return steve;
    }

    public @Nullable Face cached(UUID id) {
        return cache.get(id);
    }

    /** Cached face or Steve: never null, never blocking. */
    public Face cachedOrSteve(Player p) {
        Face f = cache.get(p.getUniqueId());
        return f != null ? f : steve;
    }

    /** Call on the main thread (reads the profile). Loading happens async. */
    public CompletableFuture<Face> request(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        String url = profileSkinUrl(player);
        if (url == null && useFallback) url = fallbackFor(id, name);

        Face current = cache.get(id);
        if (current != null && current.source().equals(url) && !expired(current)) {
            return CompletableFuture.completedFuture(current);
        }
        if (url == null) return CompletableFuture.completedFuture(current != null ? current : steve);

        CompletableFuture<Face> running = inflight.get(id);
        if (running != null && !running.isDone()) return running;

        final String source = url;
        CompletableFuture<Face> future = CompletableFuture.supplyAsync(() -> load(id, name, source), pool);
        inflight.put(id, future);
        future.whenComplete((f, ex) -> inflight.remove(id, future));
        return future;
    }

    private boolean expired(Face f) {
        return cacheMillis > 0 && f.createdAt() > 0 && System.currentTimeMillis() - f.createdAt() > cacheMillis;
    }

    private @Nullable String fallbackFor(UUID id, String name) {
        if (fallbackUrl == null || Text.blank(fallbackUrl)) return null;
        return fallbackUrl.replace("{name}", name).replace("{uuid}", id.toString().replace("-", ""));
    }

    private @Nullable String profileSkinUrl(Player p) {
        return SkinLookup.skinUrl(p);
    }

    private Face load(UUID id, String name, String source) {
        try {
            Face disk = readDisk(id, source);
            if (disk != null) {
                disk.build(symbol);
                cache.put(id, disk);
                return disk;
            }
            int[] px = download(source);
            if (px == null && useFallback) {
                String fb = fallbackFor(id, name);
                if (fb != null && !fb.equals(source)) px = download(fb);
            }
            if (px == null) {
                Face old = cache.get(id);
                return old != null ? old : steve;
            }
            Face face = new Face(source, px, System.currentTimeMillis());
            face.build(symbol);
            cache.put(id, face);
            writeDisk(id, face);
            return face;
        } catch (Throwable t) {
            plugin.getLogger().fine("Skin download failed for " + name + ": " + t);
            return steve;
        }
    }

    private int @Nullable [] download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "FlareMessages/" + plugin.getDescription().getVersion());
            if (c.getResponseCode() != 200) return null;
            byte[] body;
            try (InputStream in = c.getInputStream()) {
                body = Text.readAll(in);
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(body));
            if (img == null) return null;
            return extract(img, overlay);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Extracts the 8x8 face (base 8..15 + hat 40..47). Works with 64x32, 64x64 and HD skins. */
    static int @Nullable [] extract(BufferedImage img, boolean overlay) {
        int scale = img.getWidth() / 64;
        if (scale < 1 || img.getHeight() < 16 * scale) return null;
        int[] px = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = img.getRGB((8 + x) * scale, (8 + y) * scale);
                int rgb = base & 0xFFFFFF;
                if (overlay) {
                    int top = img.getRGB((40 + x) * scale, (8 + y) * scale);
                    int a = top >>> 24;
                    if (a > 0) rgb = blend(rgb, top & 0xFFFFFF, a);
                }
                px[y * 8 + x] = rgb;
            }
        }
        return px;
    }

    private static int blend(int under, int over, int alpha) {
        if (alpha >= 255) return over;
        float a = alpha / 255f;
        int r = Math.round(((over >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (1 - a));
        int g = Math.round(((over >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (1 - a));
        int b = Math.round((over & 0xFF) * a + (under & 0xFF) * (1 - a));
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ disk cache

    private @Nullable Face readDisk(UUID id, String source) {
        File f = new File(dir, id + ".face");
        if (!f.isFile()) return null;
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.size() < 3 || !lines.get(0).equals(source)) return null;
            long created = Long.parseLong(lines.get(1).trim());
            if (cacheMillis > 0 && System.currentTimeMillis() - created > cacheMillis) return null;
            String hex = lines.get(2).trim();
            if (hex.length() != 64 * 6) return null;
            int[] px = new int[64];
            for (int i = 0; i < 64; i++) px[i] = Integer.parseInt(hex.substring(i * 6, i * 6 + 6), 16);
            return new Face(source, px, created);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDisk(UUID id, Face face) {
        try {
            StringBuilder hex = new StringBuilder(64 * 6);
            for (int p : face.pixels()) hex.append(String.format("%06x", p));
            String content = face.source() + "\n" + face.createdAt() + "\n" + hex + "\n";
            Files.write(new File(dir, id + ".face").toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
