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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;

/** welcome.yml: join / quit messages, welcome on every join, first join (player + broadcast). */
public final class WelcomeService {
    public static final String SILENT = "flaremessages.silent";
    public static final String STAFF = "flaremessages.staff";
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
    private MessageBlock milestone = MessageBlock.EMPTY;
    private final Set<Integer> milestoneNumbers = new HashSet<>();
    private int milestoneEvery = 0;
    private int milestoneDelay = 40;

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
        private final String joinSound;
        private final String quitSound;
        private final float volume;
        private final float pitch;

        Format(String id, String permission, int priority, Template join, Template quit,
               String joinSound, String quitSound, float volume, float pitch) {
            this.id = id;
            this.permission = permission;
            this.priority = priority;
            this.join = join;
            this.quit = quit;
            this.joinSound = joinSound == null ? "" : joinSound.trim();
            this.quitSound = quitSound == null ? "" : quitSound.trim();
            this.volume = volume;
            this.pitch = pitch;
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
                        m.compile(f.getString("join", "")), m.compile(f.getString("quit", "")),
                        f.getString("join-sound", ""), f.getString("quit-sound", ""),
                        (float) f.getDouble("sound-volume", 1.0), (float) f.getDouble("sound-pitch", 1.0)));
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

        this.milestoneNumbers.clear();
        this.milestone = MessageBlock.EMPTY;
        if (yml.getBoolean("milestones.enabled", false)) {
            this.milestone = MessageBlock.load(yml.getConfigurationSection("milestones.broadcast"), m);
            for (Integer n : yml.getIntegerList("milestones.numbers")) {
                if (n != null && n > 0) {
                    this.milestoneNumbers.add(n);
                }
            }
            this.milestoneEvery = Math.max(0, yml.getInt("milestones.every", 0));
            this.milestoneDelay = Math.max(0, yml.getInt("milestones.delay-ticks", 40));
        }
    }

    /** Join number that gets the milestone message (numbers list or every N). */
    public boolean isMilestone(int number) {
        return this.milestone != MessageBlock.EMPTY && number > 0
                && (this.milestoneNumbers.contains(number) || this.milestoneEvery > 0 && number % this.milestoneEvery == 0);
    }

    private boolean module(String id) {
        return this.plugin.getConfig().getBoolean("modules." + id, true);
    }

    /** No join / quit line: flaremessages.silent, or vanished (EssentialsX, SuperVanish, PremiumVanish...). */
    public boolean silent(Player player) {
        if (player.hasPermission(SILENT)) {
            return true;
        }
        return this.plugin.getConfig().getBoolean("join-quit.hide-vanished", true) && vanished(player);
    }

    /** Vanish of EssentialsX, SuperVanish, PremiumVanish... (they all set the "vanished" metadata). */
    public static boolean vanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** {online_staff}: players with flaremessages.staff online, not counting the ones in vanish. */
    public int onlineStaff() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(STAFF) && !vanished(p)) {
                ++n;
            }
        }
        return n;
    }

    /** Sound of the join / quit format, to everyone online except {@code except} (null = everyone). */
    private void formatSound(String sound, Format f, Player except) {
        if (sound.isEmpty()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != except) {
                this.plugin.messages().playSound(p, sound, f.volume, f.pitch);
            }
        }
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
        int number = first ? this.plugin.data().incrementUniqueJoins() : 0;
        boolean sendMilestone = first && this.isMilestone(number);
        MessageService m = this.plugin.messages();
        FileConfiguration cfg = this.plugin.getConfig();

        if (this.module("join-quit")) {
            if (cfg.getBoolean("join-quit.hide-join", false) || this.silent(player)) {
                this.plugin.platform().joinMessage(event, null);
            } else {
                Template msg = null;
                Format f = this.formatFor(player);
                if (first && !this.firstJoinMessage.isEmpty()) {
                    msg = this.firstJoinMessage;
                } else if (f != null) {
                    msg = f.join();
                }
                if (msg != null) {
                    this.plugin.platform().joinMessage(event,
                            msg.isEmpty() ? null : m.render(msg, player, this.plugin.faces().cached(player.getUniqueId())));
                    if (!msg.isEmpty() && f != null) {
                        this.formatSound(f.joinSound, f, null);
                    }
                }
            }
        }

        boolean sendWelcome = this.module("welcome") && this.welcome != MessageBlock.EMPTY && !(first && this.module("first-join") && this.replaceWelcome);
        boolean sendFirst = first && this.module("first-join")
                && (this.firstJoinPlayer != MessageBlock.EMPTY || this.firstJoinBroadcast != MessageBlock.EMPTY);
        CompletableFuture<Face> future = this.plugin.faces().request(player);
        if (!sendWelcome && !sendFirst && !sendMilestone) {
            return;
        }
        UUID id = player.getUniqueId();
        MessageBlock milestoneBlock = this.milestone;
        if (sendFirst || sendMilestone) {
            // the commands (starter kit, rank, milestone prize...) run right away and don't wait for the skin:
            // a new player who leaves while it loads is not new anymore when they come back
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    m.withJoinNumber(number, () -> {
                        if (sendFirst) {
                            m.runCommands(this.firstJoinPlayer, p);
                            m.runCommands(this.firstJoinBroadcast, p);
                        }
                        if (sendMilestone) {
                            m.runCommands(milestoneBlock, p);
                        }
                    });
                }
            });
        }
        boolean needsFace = sendWelcome && this.welcome.usesFace()
                || sendFirst && (this.firstJoinPlayer.usesFace() || this.firstJoinBroadcast.usesFace())
                || sendMilestone && milestoneBlock.usesFace();
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
                this.later(id, this.firstJoinDelay, target -> m.withJoinNumber(number, () -> {
                    m.show(this.firstJoinPlayer, target, f);
                    if (this.firstJoinBroadcast != MessageBlock.EMPTY) {
                        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
                        if (this.excludeNewPlayer) {
                            targets.remove(target);
                        }
                        m.broadcast(this.firstJoinBroadcast, targets, target, f);
                    }
                }));
            }
            if (sendMilestone) {
                this.later(id, this.milestoneDelay,
                        target -> m.withJoinNumber(number, () -> m.broadcast(milestoneBlock, Bukkit.getOnlinePlayers(), target, f)));
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
            if (this.plugin.getConfig().getBoolean("join-quit.hide-quit", false) || this.silent(player)) {
                this.plugin.platform().quitMessage(event, null);
            } else {
                Format f = this.formatFor(player);
                if (f != null) {
                    this.plugin.platform().quitMessage(event, f.quit().isEmpty() ? null
                            : this.plugin.messages().render(f.quit(), player, this.plugin.faces().cached(player.getUniqueId())));
                    if (!f.quit().isEmpty()) {
                        this.formatSound(f.quitSound, f, player);
                    }
                }
            }
        }
        this.plugin.effects().clear(player);
    }

    /** /fmes preview join|quit: the line (and sound) of the player's format, shown only to him. */
    public boolean previewJoinQuit(Player player, boolean join) {
        Format f = this.formatFor(player);
        Template t = f == null ? null : join ? f.join() : f.quit();
        if (t == null || t.isEmpty()) {
            return false;
        }
        this.plugin.platform().send(player, this.plugin.messages().render(t, player, this.plugin.faces().cachedOrSteve(player)));
        this.plugin.messages().playSound(player, join ? f.joinSound : f.quitSound, f.volume, f.pitch);
        return true;
    }

    public MessageBlock milestone() {
        return this.milestone;
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
