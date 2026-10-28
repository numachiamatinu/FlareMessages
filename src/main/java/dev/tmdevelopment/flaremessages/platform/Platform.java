package dev.tmdevelopment.flaremessages.platform;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * How a message reaches the player. The rest of the plugin only builds components:
 * this layer decides how to show them on the running server (Paper or Spigot, 1.16.5 to 26.x).
 */
public abstract class Platform {
    /** Hex colors as §x§r§r§g§g§b§b: understood by every Spigot / Paper since 1.16. */
    protected static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    /** Only the 16 classic colors (hex is turned into the closest one): for consoles. */
    protected static final LegacyComponentSerializer LEGACY_16 = LegacyComponentSerializer.legacySection();

    /** Paper's own text system when available, Spigot's otherwise. */
    public static Platform detect(Logger logger) {
        try {
            PaperPlatform paper = PaperPlatform.create();
            if (paper != null) {
                return paper;
            }
        } catch (Throwable t) {
            logger.fine("Paper text API not usable: " + t);
        }
        return new SpigotPlatform(logger);
    }

    public abstract String name();

    public abstract void send(CommandSender sender, Component message);

    public abstract void actionBar(Player player, Component message);

    /** Times in ticks. */
    public abstract void title(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut);

    /** null = no join message. */
    public abstract void joinMessage(PlayerJoinEvent event, Component message);

    /** null = no quit message. */
    public abstract void quitMessage(PlayerQuitEvent event, Component message);

    /** Text with hex colors for the places that only take a String (boss bars). */
    public String legacy(Component message) {
        return LEGACY_HEX.serialize(message);
    }
}
