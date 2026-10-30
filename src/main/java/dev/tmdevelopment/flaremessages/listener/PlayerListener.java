package dev.tmdevelopment.flaremessages.listener;

import dev.tmdevelopment.flaremessages.FlareMessages;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final FlareMessages plugin;

    public PlayerListener(FlareMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        this.plugin.welcome().handleJoin(event);
        this.plugin.updates().notifyJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.welcome().handleQuit(event);
        // one tick later (other plugins may still use the face in their quit message), if he didn't come back
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (Bukkit.getPlayer(id) == null) {
                this.plugin.faces().forget(id);
            }
        });
    }
}
