package dev.tmdevelopment.flaremessages.update;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.util.Text;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Reads the latest public release of the plugin on GitHub (read only: no key, no data sent).
 * GitHub's "latest" release is never a pre-release, so betas are not offered to stable servers.
 * The result is kept for 6 hours.
 */
public final class UpdateChecker {
    public static final String REPO = "numachiamatinu/FlareMessages";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES = "https://github.com/" + REPO + "/releases";
    private static final long KEEP_MS = 6L * 60L * 60L * 1000L;
    private static final int TIMEOUT_MS = 5000;
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SAFE_TAG = Pattern.compile("[A-Za-z0-9._+-]{1,40}");

    public enum State { CHECKING, LATEST, OUTDATED, FAILED, DISABLED }

    private final FlareMessages plugin;
    private final String current;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile State state = State.CHECKING;
    private volatile String latest = "";
    private volatile String url = RELEASES;
    private volatile long checkedAt;
    private volatile String logged = "";
    private final List<Consumer<State>> waiting = new ArrayList<>();

    public UpdateChecker(FlareMessages plugin) {
        this.plugin = plugin;
        this.current = plugin.getDescription().getVersion();
    }

    /** Called on startup and on /fmessages reload. */
    public void reload() {
        if (!this.enabled()) {
            this.state = State.DISABLED;
            return;
        }
        if (this.state == State.DISABLED) {
            this.state = State.CHECKING;
            this.checkedAt = 0L;
        }
        this.refresh();
    }

    public boolean enabled() {
        return this.plugin.getConfig().getBoolean("update-check", true);
    }

    /** Asks GitHub again only if the last answer is older than 6 hours. Never blocks the server. */
    public void refresh() {
        if (!this.enabled() || System.currentTimeMillis() - this.checkedAt < KEEP_MS) {
            return;
        }
        if (!this.running.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                this.check();
            } finally {
                this.running.set(false);
                this.answer();
            }
        });
    }

    /**
     * Gives the update state to {@code done} on the main thread: at once when the last answer is still good,
     * otherwise after asking GitHub. Returns true when it was answered at once.
     */
    public boolean get(Consumer<State> done) {
        if (!this.enabled()) {
            done.accept(State.DISABLED);
            return true;
        }
        if (this.state != State.CHECKING && System.currentTimeMillis() - this.checkedAt < KEEP_MS) {
            done.accept(this.state);
            return true;
        }
        synchronized (this.waiting) {
            this.waiting.add(done);
        }
        this.refresh();
        if (!this.running.get() && this.state != State.CHECKING) {
            this.answer(); // nothing to wait for (an old answer is kept until the next try)
            return true;
        }
        return false;
    }

    private void answer() {
        List<Consumer<State>> list;
        synchronized (this.waiting) {
            if (this.waiting.isEmpty()) {
                return;
            }
            list = new ArrayList<>(this.waiting);
            this.waiting.clear();
        }
        Runnable run = () -> {
            for (Consumer<State> c : list) {
                c.accept(this.state);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            run.run();
        } else if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(this.plugin, run);
        }
    }

    private void check() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "FlareMessages/" + this.current);
            if (c.getResponseCode() != 200) {
                this.failed(); // 404 = no release yet, 403 = too many requests from this IP
                return;
            }
            String body;
            try (InputStream in = c.getInputStream()) {
                body = new String(Text.readAll(in), StandardCharsets.UTF_8);
            }
            Matcher m = TAG.matcher(body);
            if (!m.find() || !SAFE_TAG.matcher(m.group(1)).matches()) {
                this.failed();
                return;
            }
            String tag = m.group(1);
            String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            this.latest = version;
            this.url = RELEASES + "/tag/" + tag;
            this.checkedAt = System.currentTimeMillis();
            if (compare(version, this.current) > 0) {
                this.state = State.OUTDATED;
                if (!version.equals(this.logged) && this.plugin.isEnabled()) {
                    this.logged = version;
                    Bukkit.getScheduler().runTask(this.plugin,
                            () -> this.send(Bukkit.getConsoleSender(), "update.console"));
                }
            } else {
                this.state = State.LATEST;
            }
        } catch (Exception e) {
            this.failed();
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private void failed() {
        // keep a good older answer; try again on the next refresh in 30 minutes
        this.checkedAt = System.currentTimeMillis() - KEEP_MS + 30L * 60L * 1000L;
        if (this.state == State.CHECKING) {
            this.state = State.FAILED;
        }
    }

    /** Clickable message to an admin who joins, when an update is out. */
    public void notifyJoin(Player player) {
        if (!this.enabled() || !player.hasPermission("flaremessages.admin")
                && !player.hasPermission("flaremessages.command.version")) {
            return;
        }
        this.refresh();
        if (this.state != State.OUTDATED) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline() && this.state == State.OUTDATED) {
                this.send(player, "update.join");
            }
        }, 40L);
    }

    /** Sends a message with {version}, {latest} and {url} filled in. */
    public void send(CommandSender sender, String key) {
        this.plugin.lang().send(sender, key, "%version%", this.current, "%latest%", this.latest, "%url%", this.url);
    }

    public State state() {
        return this.state;
    }

    public String current() {
        return this.current;
    }

    public static String javaVersion() {
        return System.getProperty("java.version", "?");
    }

    public static String serverSoftware() {
        String name = Bukkit.getName();
        return name == null ? "?" : name.toLowerCase(Locale.ROOT).equals("craftbukkit") ? "CraftBukkit" : name;
    }

    /** 1.0.10 > 1.0.9 · 1.1.0 > 1.1.0-beta1 · 1.1.0-beta2 > 1.1.0-beta1 */
    static int compare(String a, String b) {
        String[] pa = a.split("-", 2);
        String[] pb = b.split("-", 2);
        String[] na = pa[0].split("\\.");
        String[] nb = pb[0].split("\\.");
        for (int i = 0; i < Math.max(na.length, nb.length); ++i) {
            long x = i < na.length ? number(na[i]) : 0L;
            long y = i < nb.length ? number(nb[i]) : 0L;
            if (x != y) {
                return x > y ? 1 : -1;
            }
        }
        boolean preA = pa.length > 1;
        boolean preB = pb.length > 1;
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        if (!preA) {
            return 0;
        }
        long x = number(pa[1]);
        long y = number(pb[1]);
        return x == y ? pa[1].compareToIgnoreCase(pb[1]) : (x > y ? 1 : -1);
    }

    private static long number(String s) {
        String digits = s.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(digits.length() > 18 ? digits.substring(0, 18) : digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
