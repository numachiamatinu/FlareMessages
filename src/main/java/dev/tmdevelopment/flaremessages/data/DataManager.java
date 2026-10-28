package dev.tmdevelopment.flaremessages.data;

import dev.tmdevelopment.flaremessages.FlareMessages;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * data.yml: unique players counter ({join_number}).
 * Saved async every 5 minutes / on change, and sync on disable (same pattern as the other TMDevelopment plugins).
 */
public final class DataManager {
    private final FlareMessages plugin;
    private final File file;
    private final AtomicInteger uniqueJoins = new AtomicInteger();
    private final Object ioLock = new Object();
    private final AtomicLong saveSeq = new AtomicLong();
    private long writtenSeq = -1L;

    public DataManager(FlareMessages plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!this.file.exists()) {
            // first install: start from the players the server already knows
            this.uniqueJoins.set(Bukkit.getOfflinePlayers().length);
            this.save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
        this.uniqueJoins.set(yml.getInt("unique-joins", 0));
    }

    public int uniqueJoins() {
        return this.uniqueJoins.get();
    }

    public int incrementUniqueJoins() {
        int value = this.uniqueJoins.incrementAndGet();
        this.save();
        return value;
    }

    public int size() {
        return this.uniqueJoins.get();
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("unique-joins", this.uniqueJoins.get());
        return yml;
    }

    public void save() {
        long seq = this.saveSeq.incrementAndGet();
        YamlConfiguration yml = this.snapshot();
        if (!this.plugin.isEnabled()) {
            this.write(yml, seq);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.write(yml, seq));
    }

    public void saveSync() {
        this.write(this.snapshot(), this.saveSeq.incrementAndGet());
    }

    private void write(YamlConfiguration yml, long seq) {
        synchronized (this.ioLock) {
            if (seq < this.writtenSeq) {
                return;
            }
            try {
                if (!this.plugin.getDataFolder().exists()) {
                    this.plugin.getDataFolder().mkdirs();
                }
                yml.save(this.file);
                this.writtenSeq = seq;
            } catch (Exception e) {
                this.plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
            }
        }
    }
}
