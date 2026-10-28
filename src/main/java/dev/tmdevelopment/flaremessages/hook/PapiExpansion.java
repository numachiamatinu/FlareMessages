package dev.tmdevelopment.flaremessages.hook;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.face.Face;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * %flaremessages_join_number%      unique players that joined the server
 * %flaremessages_face_1% ... _8%   one row of the player's skin face (hex colors), for scoreboards, holograms...
 */
public final class PapiExpansion extends PlaceholderExpansion {
    private final FlareMessages plugin;

    public PapiExpansion(FlareMessages plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public String getIdentifier() {
        return "flaremessages";
    }

    @NotNull
    public String getAuthor() {
        return "TMDevelopment";
    }

    @NotNull
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase();
        if (key.equals("join_number") || key.equals("unique_joins")) {
            return String.valueOf(this.plugin.data().uniqueJoins());
        }
        if (player == null) {
            return "";
        }
        if (key.length() == 6 && key.startsWith("face_") && key.charAt(5) >= '1' && key.charAt(5) <= '8') {
            Face face = this.plugin.faces().cached(player.getUniqueId());
            if (face == null) {
                face = this.plugin.faces().steve();
            }
            return face.legacyRow(key.charAt(5) - '1');
        }
        return null;
    }
}
