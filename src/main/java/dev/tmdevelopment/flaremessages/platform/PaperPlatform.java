package dev.tmdevelopment.flaremessages.platform;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import java.lang.reflect.Method;
import java.time.Duration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Paper (and forks like Purpur) since 1.16.5: messages go through the server's own Adventure,
 * so click / hover work everywhere, join and quit messages included.
 * Everything is looked up by reflection once, at startup: the plugin never links to Paper-only classes.
 */
final class PaperPlatform extends Platform {
    // built at runtime, so the name is never mistaken for the copy of Adventure inside this jar
    private static final String ADVENTURE = String.join(".", "net", "kyori", "adventure");

    private final Object gson;
    private final Method deserialize;
    private final Method sendMessage;
    private final Method sendActionBar;
    private final Method showTitle;
    private final Method titleOf;
    private final Method timesOf;
    private final Method joinMessage;
    private final Method quitMessage;

    private PaperPlatform(Object gson, Method deserialize, Method sendMessage, Method sendActionBar, Method showTitle,
                          Method titleOf, Method timesOf, Method joinMessage, Method quitMessage) {
        this.gson = gson;
        this.deserialize = deserialize;
        this.sendMessage = sendMessage;
        this.sendActionBar = sendActionBar;
        this.showTitle = showTitle;
        this.titleOf = titleOf;
        this.timesOf = timesOf;
        this.joinMessage = joinMessage;
        this.quitMessage = quitMessage;
    }

    /** @return null when the server has no usable Adventure (Spigot) */
    static PaperPlatform create() throws Exception {
        Class<?> audience;
        try {
            audience = Class.forName(ADVENTURE + ".audience.Audience");
        } catch (ClassNotFoundException e) {
            return null;
        }
        if (!audience.isAssignableFrom(Player.class) || !audience.isAssignableFrom(CommandSender.class)) {
            return null;
        }
        Class<?> component = Class.forName(ADVENTURE + ".text.Component");
        Class<?> title = Class.forName(ADVENTURE + ".title.Title");
        Class<?> times = Class.forName(ADVENTURE + ".title.Title$Times");
        Class<?> serializer = Class.forName(ADVENTURE + ".text.serializer.gson.GsonComponentSerializer");
        Object gson = serializer.getMethod("gson").invoke(null);
        Method deserialize = serializer.getMethod("deserialize", Object.class);
        Method timesOf;
        try {
            timesOf = times.getMethod("times", Duration.class, Duration.class, Duration.class);
        } catch (NoSuchMethodException e) {
            timesOf = times.getMethod("of", Duration.class, Duration.class, Duration.class);
        }
        PaperPlatform p = new PaperPlatform(gson, deserialize,
                audience.getMethod("sendMessage", component),
                audience.getMethod("sendActionBar", component),
                audience.getMethod("showTitle", title),
                title.getMethod("title", component, component, times),
                timesOf,
                PlayerJoinEvent.class.getMethod("joinMessage", component),
                PlayerQuitEvent.class.getMethod("quitMessage", component));
        // a first conversion now: if anything is off, Spigot mode is used instead of failing later
        p.toNative(Component.text("test"));
        return p;
    }

    private Object toNative(Component component) throws Exception {
        return this.deserialize.invoke(this.gson, ComponentJson.write(component));
    }

    @Override
    public String name() {
        return "Paper";
    }

    @Override
    public void send(CommandSender sender, Component message) {
        try {
            this.sendMessage.invoke(sender, this.toNative(message));
        } catch (Exception e) {
            sender.sendMessage(this.legacy(message));
        }
    }

    @Override
    public void actionBar(Player player, Component message) {
        try {
            this.sendActionBar.invoke(player, this.toNative(message));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void title(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Object times = this.timesOf.invoke(null, Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                    Duration.ofMillis(fadeOut * 50L));
            this.showTitle.invoke(player, this.titleOf.invoke(null, this.toNative(title), this.toNative(subtitle), times));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void joinMessage(PlayerJoinEvent event, Component message) {
        try {
            this.joinMessage.invoke(event, message == null ? null : this.toNative(message));
        } catch (Exception e) {
            event.setJoinMessage(message == null ? null : this.legacy(message));
        }
    }

    @Override
    public void quitMessage(PlayerQuitEvent event, Component message) {
        try {
            this.quitMessage.invoke(event, message == null ? null : this.toNative(message));
        } catch (Exception e) {
            event.setQuitMessage(message == null ? null : this.legacy(message));
        }
    }
}
