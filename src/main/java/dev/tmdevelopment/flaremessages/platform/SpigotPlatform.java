package dev.tmdevelopment.flaremessages.platform;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Spigot (and any server without Paper's text API): chat and action bar go through
 * Spigot's BungeeCord chat (click / hover work), titles and join / quit messages are sent
 * as text with hex colors (Bukkit only takes text there, so they have no click / hover).
 */
final class SpigotPlatform extends Platform {
    private final Logger logger;
    private Method parse;
    private boolean parseReturnsArray;
    private Class<?> baseComponent;
    private Method playerSpigot;
    private Method spigotSend;
    private Method spigotSendType;
    private Object actionBarType;
    private boolean warned;

    SpigotPlatform(Logger logger) {
        this.logger = logger;
        try {
            Class<?> serializer = Class.forName("net.md_5.bungee.chat.ComponentSerializer");
            this.baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> array = Array.newInstance(this.baseComponent, 0).getClass();
            try {
                this.parse = serializer.getMethod("deserialize", String.class);
                this.parseReturnsArray = false;
            } catch (NoSuchMethodException e) {
                this.parse = serializer.getMethod("parse", String.class);
                this.parseReturnsArray = true;
            }
            this.playerSpigot = Player.class.getMethod("spigot");
            Class<?> spigot = this.playerSpigot.getReturnType();
            this.spigotSend = spigot.getMethod("sendMessage", array);
            Class<?> type = Class.forName("net.md_5.bungee.api.ChatMessageType");
            this.spigotSendType = spigot.getMethod("sendMessage", type, array);
            this.actionBarType = type.getField("ACTION_BAR").get(null);
        } catch (Exception e) {
            // no BungeeCord chat: everything is sent as colored text
            this.parse = null;
            logger.warning("This server has no JSON chat support: clickable / hover texts are shown as plain colored text.");
        }
    }

    @Override
    public String name() {
        return "Spigot";
    }

    private Object components(Component message) throws Exception {
        Object parsed = this.parse.invoke(null, ComponentJson.write(message));
        if (this.parseReturnsArray) {
            return parsed;
        }
        Object array = Array.newInstance(this.baseComponent, 1);
        Array.set(array, 0, parsed);
        return array;
    }

    @Override
    public void send(CommandSender sender, Component message) {
        if (sender instanceof Player && this.parse != null) {
            try {
                this.spigotSend.invoke(this.playerSpigot.invoke(sender), this.components(message));
                return;
            } catch (Exception e) {
                this.warnOnce(e);
            }
        }
        // console: only the 16 classic colors, a console doesn't understand hex
        sender.sendMessage(sender instanceof Player ? this.legacy(message) : LEGACY_16.serialize(message));
    }

    @Override
    public void actionBar(Player player, Component message) {
        if (this.parse == null) {
            return;
        }
        try {
            this.spigotSendType.invoke(this.playerSpigot.invoke(player), this.actionBarType, this.components(message));
        } catch (Exception e) {
            this.warnOnce(e);
        }
    }

    @Override
    public void title(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(this.legacy(title), this.legacy(subtitle), fadeIn, stay, fadeOut);
    }

    @Override
    public void joinMessage(PlayerJoinEvent event, Component message) {
        event.setJoinMessage(message == null ? null : this.legacy(message));
    }

    @Override
    public void quitMessage(PlayerQuitEvent event, Component message) {
        event.setQuitMessage(message == null ? null : this.legacy(message));
    }

    private void warnOnce(Exception e) {
        if (!this.warned) {
            this.warned = true;
            this.logger.warning("Could not send a JSON message, colored text is used instead: " + e);
        }
    }
}
