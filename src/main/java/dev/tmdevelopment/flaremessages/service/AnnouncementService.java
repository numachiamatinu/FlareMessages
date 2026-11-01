package dev.tmdevelopment.flaremessages.service;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.message.MessageBlock;
import dev.tmdevelopment.flaremessages.util.Headers;
import dev.tmdevelopment.flaremessages.util.Yaml;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** announcements.yml: automatic announcements (in order or random) with permission / world filters. */
public final class AnnouncementService {
    private final FlareMessages plugin;
    private final File file;
    private final Map<String, Announcement> announcements = new LinkedHashMap<>();
    private final List<Announcement> rotation = new ArrayList<>();
    private YamlConfiguration yml = new YamlConfiguration();
    private BukkitTask task;
    private int index;
    private int lastRandom = -1;

    public AnnouncementService(FlareMessages plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "announcements.yml");
    }

    public static final class Announcement {
        private final String id;
        private final MessageBlock block;
        private final String permission;
        private final Set<String> worlds;
        private final boolean inRotation;

        Announcement(String id, MessageBlock block, String permission, Set<String> worlds, boolean inRotation) {
            this.id = id;
            this.block = block;
            this.permission = permission;
            this.worlds = worlds;
            this.inRotation = inRotation;
        }

        public String id() {
            return this.id;
        }

        public MessageBlock block() {
            return this.block;
        }

        public String permission() {
            return this.permission;
        }

        public Set<String> worlds() {
            return this.worlds;
        }

        public boolean inRotation() {
            return this.inRotation;
        }
    }

    public void load() {
        this.stop();
        if (!this.file.exists()) {
            this.plugin.saveResource("announcements.yml", false);
        }
        Headers.sync(this.plugin, this.file, "announcements.yml");
        this.yml = Yaml.load(this.plugin, this.file);
        this.announcements.clear();
        this.rotation.clear();
        this.index = 0;
        this.lastRandom = -1;
        ConfigurationSection list = this.yml.getConfigurationSection("announcements");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection s = list.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                MessageBlock block = MessageBlock.load(s, this.plugin.messages());
                if (block == MessageBlock.EMPTY || !block.any()) {
                    continue;
                }
                Set<String> worlds = new HashSet<>();
                for (String w : s.getStringList("worlds")) {
                    worlds.add(w.toLowerCase(Locale.ROOT));
                }
                Announcement a = new Announcement(id, block, s.getString("permission", ""), worlds, s.getBoolean("in-rotation", true));
                this.announcements.put(id.toLowerCase(Locale.ROOT), a);
                if (a.inRotation()) {
                    this.rotation.add(a);
                }
            }
        }
        this.start();
    }

    public void start() {
        if (this.task != null || this.rotation.isEmpty() || !this.plugin.getConfig().getBoolean("modules.announcements", true)) {
            return;
        }
        long period = this.interval() * 20L;
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, period, period);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public void restart() {
        this.stop();
        this.start();
    }

    private void tick() {
        if (this.rotation.isEmpty() || Bukkit.getOnlinePlayers().size() < this.minPlayers()) {
            return;
        }
        // an announcement nobody online can see (permission / worlds) doesn't waste the turn: the next one goes
        if (this.random()) {
            List<Integer> visible = new ArrayList<>();
            for (int i = 0; i < this.rotation.size(); ++i) {
                if (this.seenBySomeone(this.rotation.get(i))) {
                    visible.add(i);
                }
            }
            if (visible.size() > 1) {
                visible.remove(Integer.valueOf(this.lastRandom));
            }
            if (visible.isEmpty()) {
                return;
            }
            int i = visible.get(ThreadLocalRandom.current().nextInt(visible.size()));
            this.lastRandom = i;
            this.broadcast(this.rotation.get(i));
            return;
        }
        for (int tries = 0; tries < this.rotation.size(); ++tries) {
            Announcement a = this.rotation.get(this.index % this.rotation.size());
            this.index = (this.index + 1) % this.rotation.size();
            if (this.seenBySomeone(a)) {
                this.broadcast(a);
                return;
            }
        }
    }

    private boolean seenBySomeone(Announcement a) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.sees(a, p)) {
                return true;
            }
        }
        return false;
    }

    private boolean sees(Announcement a, Player p) {
        return (a.permission().isEmpty() || p.hasPermission(a.permission()))
                && (a.worlds().isEmpty() || a.worlds().contains(p.getWorld().getName().toLowerCase(Locale.ROOT)));
    }

    public Announcement next() {
        if (this.random() && this.rotation.size() > 1) {
            int i;
            do {
                i = ThreadLocalRandom.current().nextInt(this.rotation.size());
            } while (i == this.lastRandom);
            this.lastRandom = i;
            return this.rotation.get(i);
        }
        Announcement a = this.rotation.get(this.index % this.rotation.size());
        this.index = (this.index + 1) % this.rotation.size();
        return a;
    }

    /** sent = received it · filtered = skipped by the permission / worlds of the announcement */
    public static final class Result {
        private final int sent;
        private final int filtered;

        Result(int sent, int filtered) {
            this.sent = sent;
            this.filtered = filtered;
        }

        public int sent() {
            return this.sent;
        }

        public int filtered() {
            return this.filtered;
        }
    }

    public Result broadcast(Announcement a) {
        List<Player> targets = new ArrayList<>();
        int filtered = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.sees(a, p)) {
                ++filtered;
                continue;
            }
            targets.add(p);
        }
        this.plugin.messages().broadcast(a.block(), targets, null, null);
        return new Result(targets.size(), filtered);
    }

    // ------------------------------------------------------------------ settings

    public int interval() {
        return Math.max(5, this.yml.getInt("settings.interval-seconds", 180));
    }

    public boolean random() {
        return this.yml.getBoolean("settings.random", false);
    }

    public int minPlayers() {
        return Math.max(0, this.yml.getInt("settings.min-players", 1));
    }

    public Announcement get(String id) {
        return this.announcements.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Announcement> all() {
        return new ArrayList<>(this.announcements.values());
    }

    public List<String> ids() {
        return new ArrayList<>(this.announcements.keySet());
    }

    public int rotationSize() {
        return this.rotation.size();
    }

    public boolean automatic() {
        return this.task != null;
    }

    public boolean hasRotation() {
        return !this.rotation.isEmpty();
    }

    public int size() {
        return this.announcements.size();
    }
}
