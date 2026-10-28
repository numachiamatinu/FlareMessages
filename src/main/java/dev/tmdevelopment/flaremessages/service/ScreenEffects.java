package dev.tmdevelopment.flaremessages.service;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * ONE light task (every 10 ticks) keeps timed action bars visible and updates / removes boss bars.
 * When nothing is shown it does nothing. Main thread only, no locking needed.
 */
public final class ScreenEffects implements Runnable {
    private static final int PERIOD = 10;
    private static final int ACTIONBAR_RESEND = 40;
    private final FlareMessages plugin;
    private final Map<UUID, ActionEntry> actionbars = new HashMap<>();
    private final Map<UUID, List<BarEntry>> bossbars = new HashMap<>();
    private BukkitTask task;

    public ScreenEffects(FlareMessages plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (this.task == null) {
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this, PERIOD, PERIOD);
        }
    }

    private int maxBossbars() {
        return Math.max(1, this.plugin.getConfig().getInt("max-bossbars-per-player", 3));
    }

    public void actionbar(Player player, Component message, int seconds) {
        this.plugin.platform().actionBar(player, message);
        int ticks = seconds * 20;
        if (ticks > ACTIONBAR_RESEND) {
            this.actionbars.put(player.getUniqueId(), new ActionEntry(message, ticks));
        } else {
            this.actionbars.remove(player.getUniqueId());
        }
    }

    /** @param title text with hex colors (§x format) */
    public void bossbar(Player player, String title, BarColor color, BarStyle style, int seconds, boolean countdown) {
        List<BarEntry> list = this.bossbars.get(player.getUniqueId());
        if (list == null) {
            list = new ArrayList<>(2);
            this.bossbars.put(player.getUniqueId(), list);
        }
        int max = this.maxBossbars();
        while (list.size() >= max) {
            list.remove(0).bar.removeAll();
        }
        BossBar bar = Bukkit.createBossBar(title, color, style);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        list.add(new BarEntry(bar, Math.max(1, seconds * 20), countdown));
    }

    public void clear(Player player) {
        this.actionbars.remove(player.getUniqueId());
        List<BarEntry> list = this.bossbars.remove(player.getUniqueId());
        if (list != null) {
            for (BarEntry e : list) {
                e.bar.removeAll();
            }
        }
    }

    @Override
    public void run() {
        if (!this.actionbars.isEmpty()) {
            Iterator<Map.Entry<UUID, ActionEntry>> it = this.actionbars.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ActionEntry> en = it.next();
                ActionEntry e = en.getValue();
                e.remaining -= PERIOD;
                e.sinceSend += PERIOD;
                Player p = Bukkit.getPlayer(en.getKey());
                if (p == null || e.remaining <= 0) {
                    it.remove();
                    continue;
                }
                if (e.sinceSend >= ACTIONBAR_RESEND) {
                    e.sinceSend = 0;
                    this.plugin.platform().actionBar(p, e.message);
                }
            }
        }
        if (!this.bossbars.isEmpty()) {
            Iterator<Map.Entry<UUID, List<BarEntry>>> it = this.bossbars.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, List<BarEntry>> en = it.next();
                Player p = Bukkit.getPlayer(en.getKey());
                Iterator<BarEntry> bars = en.getValue().iterator();
                while (bars.hasNext()) {
                    BarEntry e = bars.next();
                    e.remaining -= PERIOD;
                    if (p == null || e.remaining <= 0) {
                        e.bar.removeAll();
                        bars.remove();
                    } else if (e.countdown) {
                        e.bar.setProgress(Math.max(0.0, Math.min(1.0, (double) e.remaining / e.total)));
                    }
                }
                if (en.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    public void shutdown() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (List<BarEntry> list : this.bossbars.values()) {
            for (BarEntry e : list) {
                e.bar.removeAll();
            }
        }
        this.bossbars.clear();
        this.actionbars.clear();
    }

    private static final class ActionEntry {
        final Component message;
        int remaining;
        int sinceSend;

        ActionEntry(Component message, int remaining) {
            this.message = message;
            this.remaining = remaining;
        }
    }

    private static final class BarEntry {
        final BossBar bar;
        final int total;
        final boolean countdown;
        int remaining;

        BarEntry(BossBar bar, int total, boolean countdown) {
            this.bar = bar;
            this.total = total;
            this.remaining = total;
            this.countdown = countdown;
        }
    }
}
