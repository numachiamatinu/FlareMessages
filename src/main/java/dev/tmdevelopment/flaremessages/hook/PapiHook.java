package dev.tmdevelopment.flaremessages.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public final class PapiHook {
    private PapiHook() {
    }

    public static String apply(Player player, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable throwable) {
            return text;
        }
    }
}
