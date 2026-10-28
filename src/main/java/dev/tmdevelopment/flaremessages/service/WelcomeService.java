package dev.tmdevelopment.flaremessages.service;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.face.Face;
import dev.tmdevelopment.flaremessages.message.MessageBlock;
import dev.tmdevelopment.flaremessages.message.Template;
import dev.tmdevelopment.flaremessages.util.Headers;
import dev.tmdevelopment.flaremessages.util.Yaml;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** welcome.yml: join / quit messages, welcome on every join, first join (player + broadcast). */
public final class WelcomeService {
    private final FlareMessages plugin;
    private final File file;
    private final List<Format> formats = new ArrayList<>();
    private Template firstJoinMessage = Template.EMPTY;
    private MessageBlock welcome = MessageBlock.EMPTY;
    private int welcomeDelay = 5;
    private MessageBlock firstJoinPlayer = MessageBlock.EMPTY;
    private MessageBlock firstJoinBroadcast = MessageBlock.EMPTY;
    private boolean replaceWelcome = true;
    private boolean excludeNewPlayer = true;
    private int firstJoinDelay = 5;

    public WelcomeService(FlareMessages plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "welcome.yml");
    }

    public static final class Format {
        private final String id;
        private final String permission;
        private final int priority;
        private final Template join;
        private final Template quit;

        Format(String id, String permission, int priority, Template join, Template quit) {
            this.id = id;
            this.permission = permission;
            this.priority = priority;
            this.join = join;
            this.quit = quit;
        }

        public String id() {
            return this.id;
        }

        public String permission() {
            return this.permission;
        }

        public int priority() {
            return this.priority;
        }

        public Template join() {
            return this.join;
        }

        public Template quit() {
            return this.quit;
        }
    }

    public void load() {
        if (!this.file.exists()) {
            this.plugin.saveResource("welcome.yml", false);
        }
        Headers.sync(this.plugin, this.file, "welcome.yml");
        YamlConfiguration yml = Yaml.load(this.plugin, this.file);
        MessageService m = this.plugin.messages();

        this.formats.clear();
        ConfigurationSection fs = yml.getConfigurationSection("join-quit.formats");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                ConfigurationSection f = fs.getConfigurationSection(id);
                if (f == null) {
                    continue;
                }
                this.formats.add(new Format(id, f.getString("permission", ""), f.getInt("priority", 0),
                        m.compile(f.getString("join", "")), m.compile(f.getString("quit", ""))));
            }
        }
        this.formats.sort(Comparator.comparingInt(Format::priority).reversed());
        this.firstJoinMessage = m.compile(yml.getString("join-quit.first-join", ""));

        this.welcome = MessageBlock.load(yml.getConfigurationSection("welcome"), m);
        this.welcomeDelay = Math.max(0, yml.getInt("welcome.delay-ticks", 5));

        this.replaceWelcome = yml.getBoolean("first-join.replace-welcome", true);
        this.firstJoinDelay = Math.max(0, yml.getInt("first-join.delay-ticks", 5));
        this.firstJoinPlayer = MessageBlock.load(yml.getConfigurationSection("first-join.player"), m);
        this.firstJoinBroadcast = MessageBlock.load(yml.getConfigurationSection("first-join.broadcast"), m);
        this.excludeNewPlayer = yml.getBoolean("first-join.broadcast.exclude-new-player", true);
    }

    private boolean module(String id) {
        return this.plugin.getConfig().getBoolean("modules." + id, true);
    }

    public Format formatFor(Player player) {
        for (Format f : this.formats) {
            if (f.permission() == null || f.permission().isEmpty() || player.hasPermission(f.permission())) {
                return f;
            }
        }
        return null;
    }

    public void handleJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = !player.hasPlayedBefore();
        if (first) {
            this.plugin.data().incrementUniqueJoins();
        }
        MessageService m = this.plugin.messages();
        FileConfiguration cfg = this.plugin.getConfig();

        if (this.module("join-quit")) {
            if (cfg.getBoolean("join-quit.hide-join", false)) {
                this.plugin.platform().joinMessage(event, null);
            } else {
                Template msg = null;
                if (first && !this.firstJoinMessage.isEmpty()) {
                    msg = this.firstJoinMessage;
                } else {
                    Format f = this.formatFor(player);
                    if (f != null) {
                        msg = f.join();
                    }
                }
                if (msg != null) {
                    this.plugin.platform().joinMessage(event,
                            msg.isEmpty() ? null : m.render(msg, player, this.plugin.faces().cached(player.getUniqueId())));
                }
            }
        }

        boolean sendWelcome = this.module("welcome") && this.welcome != MessageBlock.EMPTY && !(first && this.module("first-join") && this.replaceWelcome);
        boolean sendFirst = first && this.module("first-join")
                && (this.firstJoinPlayer != MessageBlock.EMPTY || this.firstJoinBroadcast != MessageBlock.EMPTY);
        CompletableFuture<Face> future = this.plugin.faces().request(player);
        if (!sendWelcome && !sendFirst) {
            return;
        }
        boolean needsFace = sendWelcome && this.welcome.usesFace()
                || sendFirst && (this.firstJoinPlayer.usesFace() || this.firstJoinBroadcast.usesFace());
        int waitMs = Math.max(0, cfg.getInt("face.wait-before-welcome-ms", 1500));
        if (needsFace && !future.isDone() && waitMs > 0) {
            // wait for the skin at most waitMs, then Steve: the join is never slowed down
            CompletableFuture<Face> limited = new CompletableFuture<>();
            future.whenComplete((face, ex) -> limited.complete(face));
            Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () -> limited.complete(null), Math.max(1L, (waitMs + 49) / 50));
            future = limited;
        } else if (!future.isDone()) {
            future = CompletableFuture.completedFuture(null);
        }
        UUID id = player.getUniqueId();
        future.whenComplete((face, ex) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                return;
            }
            Face f = face != null ? face : this.plugin.faces().cachedOrSteve(p);
            if (sendWelcome) {
                this.later(id, this.welcomeDelay, target -> m.send(this.welcome, target, f));
            }
            if (sendFirst) {
                this.later(id, this.firstJoinDelay, target -> {
                    m.send(this.firstJoinPlayer, target, f);
                    if (this.firstJoinBroadcast != MessageBlock.EMPTY) {
                        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
                        if (this.excludeNewPlayer) {
                            targets.remove(target);
                        }
                        m.broadcast(this.firstJoinBroadcast, targets, target, f);
                        m.runCommands(this.firstJoinBroadcast, target);
                    }
                });
            }
        }));
    }

    private void later(UUID id, int delay, java.util.function.Consumer<Player> action) {
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                action.accept(p);
            }
        };
        if (delay <= 0) {
            task.run();
        } else {
            Bukkit.getScheduler().runTaskLater(this.plugin, task, delay);
        }
    }

    public void handleQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.module("join-quit")) {
            if (this.plugin.getConfig().getBoolean("join-quit.hide-quit", false)) {
                this.plugin.platform().quitMessage(event, null);
            } else {
                Format f = this.formatFor(player);
                if (f != null) {
                    this.plugin.platform().quitMessage(event, f.quit().isEmpty() ? null
                            : this.plugin.messages().render(f.quit(), player, this.plugin.faces().cached(player.getUniqueId())));
                }
            }
        }
        this.plugin.effects().clear(player);
    }

    public MessageBlock welcome() {
        return this.welcome;
    }

    public MessageBlock firstJoinPlayer() {
        return this.firstJoinPlayer;
    }

    public MessageBlock firstJoinBroadcast() {
        return this.firstJoinBroadcast;
    }

    public int formatCount() {
        return this.formats.size();
    }
}
