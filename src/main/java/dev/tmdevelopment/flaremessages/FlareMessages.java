package dev.tmdevelopment.flaremessages;

import dev.tmdevelopment.flaremessages.command.MessagesCommand;
import dev.tmdevelopment.flaremessages.config.Lang;
import dev.tmdevelopment.flaremessages.data.DataManager;
import dev.tmdevelopment.flaremessages.face.FaceService;
import dev.tmdevelopment.flaremessages.hook.PapiExpansion;
import dev.tmdevelopment.flaremessages.install.DependencyInstaller;
import dev.tmdevelopment.flaremessages.listener.PlayerListener;
import dev.tmdevelopment.flaremessages.platform.Platform;
import dev.tmdevelopment.flaremessages.service.AnnouncementService;
import dev.tmdevelopment.flaremessages.service.MessageService;
import dev.tmdevelopment.flaremessages.service.ScreenEffects;
import dev.tmdevelopment.flaremessages.service.WelcomeService;
import dev.tmdevelopment.flaremessages.update.UpdateChecker;
import dev.tmdevelopment.flaremessages.util.Headers;
import dev.tmdevelopment.flaremessages.util.Text;
import dev.tmdevelopment.flaremessages.util.Yaml;
import dev.tmdevelopment.flaremessages.stats.TMDStats;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlareMessages extends JavaPlugin {
    /** Oldest supported Minecraft version: 1.16.5 */
    private static final int[] MIN_VERSION = {1, 16, 5};
    private Platform platform;
    private TMDStats stats;
    private Lang lang;
    private DataManager data;
    private MessageService messages;
    private FaceService faces;
    private ScreenEffects effects;
    private WelcomeService welcome;
    private AnnouncementService announcements;
    private UpdateChecker updates;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        if (!supportedVersion()) {
            this.getLogger().severe("FlareMessages needs Minecraft 1.16.5 or newer (this server runs "
                    + Bukkit.getBukkitVersion() + "). The plugin is disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.platform = Platform.detect(this.getLogger());
        this.saveDefaultConfig();
        Yaml.clear();
        this.checkConfig();
        this.updateConfig();
        boolean papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        Text.setPapi(papi);
        this.lang = new Lang(this);
        this.lang.load();
        this.data = new DataManager(this);
        this.data.load();
        this.messages = new MessageService(this);
        this.messages.load();
        this.faces = new FaceService(this);
        this.faces.reload(this.getConfig());
        this.effects = new ScreenEffects(this);
        this.welcome = new WelcomeService(this);
        this.welcome.load();
        this.announcements = new AnnouncementService(this);
        this.announcements.load();
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        PluginCommand command = this.getCommand("fmessages");
        if (command != null) {
            MessagesCommand executor = new MessagesCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        if (papi) {
            new PapiExpansion(this).register();
        }
        new DependencyInstaller(this).run(name -> {
            if (name.equalsIgnoreCase("PlaceholderAPI") && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Text.setPapi(true);
                new PapiExpansion(this).register();
            }
        });
        this.effects.start();
        // players already online (server /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.faces.request(player);
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> this.data.save(), 6000L, 6000L);
        this.updates = new UpdateChecker(this);
        this.updates.reload();
        this.startStats();
        this.banner(papi, System.currentTimeMillis() - start);
    }

    @Override
    public void onDisable() {
        if (this.stats != null) {
            this.stats.shutdown();
        }
        if (this.announcements != null) {
            this.announcements.stop();
        }
        if (this.effects != null) {
            this.effects.shutdown();
        }
        if (this.faces != null) {
            this.faces.shutdown();
        }
        if (this.data != null) {
            this.data.saveSync();
        }
    }

    /** Adds to config.yml the options of new versions, without touching the existing ones. */
    private void checkConfig() {
        File file = new File(this.getDataFolder(), "config.yml");
        Yaml.load(this, file);
        if (!Yaml.broken(this, file)) {
            Headers.sync(this, file, "config.yml");
        }
    }

    private void updateConfig() {
        if (Yaml.broken(this, new File(this.getDataFolder(), "config.yml"))) {
            return; // broken file: defaults are used, never overwrite the admin's text
        }
        FileConfiguration config = this.getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            String parent = key.startsWith("auto-install.plugins.") ? "auto-install.plugins" : null;
            if (parent != null && config.isSet(parent) || config.isSet(key)) {
                continue;
            }
            config.set(key, defaults.get(key));
            changed = true;
        }
        if (changed && Yaml.keepsComments()) {
            this.saveConfig();
            this.getLogger().info("config.yml updated with the new options of this version.");
        } else if (changed) {
            // before 1.18 saving would delete every comment of the file: new options just use their default value
            this.getLogger().info("New options of this version use their default value (config.yml is not rewritten"
                    + " on this server version, to keep its comments).");
        }
    }

    /** Anonymous statistics (TMDStats): only on / off choices and counts, never names or text from the config. */
    private void startStats() {
        this.stats = new TMDStats(this, "flaremessages", this.getConfig().getBoolean("stats", true));
        this.stats.addValue("language", () -> {
            String language = this.getConfig().getString("language", "en");
            return "en".equalsIgnoreCase(language) ? "en" : "other";
        });
        this.stats.addValue("join_quit", () -> this.onOff("modules.join-quit"));
        this.stats.addValue("welcome", () -> this.onOff("modules.welcome"));
        this.stats.addValue("first_join", () -> this.onOff("modules.first-join"));
        this.stats.addValue("announcements", () -> this.onOff("modules.announcements"));
        this.stats.addValue("announcement_count", () -> this.announcements.size());
        this.stats.addValue("join_formats", () -> this.welcome.formatCount());
        this.stats.addValue("placeholderapi", () -> Text.papi() ? "On" : "Off");
    }

    private String onOff(String path) {
        return this.getConfig().getBoolean(path, true) ? "On" : "Off";
    }

    /** true on 1.16.5 and newer (also the new 26.x numbering). */
    static boolean supportedVersion() {
        String v = Bukkit.getBukkitVersion();
        int dash = v.indexOf('-');
        String[] parts = (dash > 0 ? v.substring(0, dash) : v).split("\\.");
        for (int i = 0; i < MIN_VERSION.length; ++i) {
            int n;
            try {
                n = i < parts.length ? Integer.parseInt(parts[i].replaceAll("\\D.*", "")) : 0;
            } catch (NumberFormatException e) {
                return true; // unknown format: don't block the server
            }
            if (n != MIN_VERSION[i]) {
                return n > MIN_VERSION[i];
            }
        }
        return true;
    }

    public void reloadAll() {
        Yaml.clear();
        this.checkConfig();
        this.reloadConfig();
        this.lang.load();
        this.messages.load();
        this.faces.reload(this.getConfig());
        this.welcome.load();
        this.announcements.load();
        this.updates.reload();
    }

    private void banner(boolean papi, long ms) {
        String g = "<gradient:#2F80ED:#56CCF2>";
        String[] lines = new String[]{
                "",
                "<gradient:#2F80ED:#56CCF2>  \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2557      \u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557</gradient>",
                "<gradient:#2F80ED:#56CCF2>  \u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d\u2588\u2588\u2551     \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d</gradient>",
                "<gradient:#2F80ED:#56CCF2>  \u2588\u2588\u2588\u2588\u2588\u2557  \u2588\u2588\u2551     \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255d\u2588\u2588\u2588\u2588\u2588\u2557  </gradient>",
                "<gradient:#2F80ED:#56CCF2>  \u2588\u2588\u2554\u2550\u2550\u255d  \u2588\u2588\u2551     \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2551\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u255d  </gradient>",
                "<gradient:#2F80ED:#56CCF2>  \u2588\u2588\u2551     \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557</gradient>",
                "<gradient:#2F80ED:#56CCF2>  \u255a\u2550\u255d     \u255a\u2550\u2550\u2550\u2550\u2550\u2550\u255d\u255a\u2550\u255d  \u255a\u2550\u255d\u255a\u2550\u255d  \u255a\u2550\u255d\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u255d</gradient>",
                "<gray>         M E S S A G E S  <dark_gray>|  <white>v" + this.getDescription().getVersion(),
                "",
                "<gray>  Developed by " + g + "<bold>TMDevelopment</bold></gradient>",
                "<gray>  Minecraft: <white>" + Bukkit.getBukkitVersion().split("-")[0] + " <dark_gray>(<gray>"
                        + UpdateChecker.serverSoftware() + "<dark_gray>) | <gray>Text API: <white>" + this.platform.name()
                        + " <dark_gray>| <gray>Unique players: <white>" + this.data.uniqueJoins(),
                "<gray>  Announcements: <white>" + this.announcements.size() + " <dark_gray>| <gray>Join formats: <white>"
                        + this.welcome.formatCount(),
                "<gray>  PlaceholderAPI: " + (papi ? "<green>hooked" : this.papiDownload() ? "<#56CCF2>downloading..." : "<red>not found")
                        + " <dark_gray>| <gray>SkinsRestorer: "
                        + (Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer") ? "<green>found" : "<red>not found"),
                Yaml.problems().isEmpty() ? "<gray>  Files: <green>all valid"
                        : "<gray>  Files: <red>" + Yaml.problems().size() + " with errors <dark_gray>(<gray>see above, defaults are used<dark_gray>)",
                "<gray>  Enabled in <white>" + ms + "ms",
                ""};
        for (String line : lines) {
            this.platform.send(Bukkit.getConsoleSender(), Text.parse(line, null));
        }
    }

    /** PlaceholderAPI is missing but auto-install is getting it (the banner says so instead of "not found"). */
    private boolean papiDownload() {
        return this.getConfig().getBoolean("auto-install.enabled", true)
                && this.getConfig().getBoolean("auto-install.plugins.PlaceholderAPI.enabled", true);
    }

    public Platform platform() {
        return this.platform;
    }

    public Lang lang() {
        return this.lang;
    }

    public DataManager data() {
        return this.data;
    }

    public MessageService messages() {
        return this.messages;
    }

    public FaceService faces() {
        return this.faces;
    }

    public ScreenEffects effects() {
        return this.effects;
    }

    public WelcomeService welcome() {
        return this.welcome;
    }

    public AnnouncementService announcements() {
        return this.announcements;
    }

    public UpdateChecker updates() {
        return this.updates;
    }
}
