package dev.tmdevelopment.flaremessages.listener;

import dev.tmdevelopment.flaremessages.FlareMessages;
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
    }
}
