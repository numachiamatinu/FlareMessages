package dev.tmdevelopment.flaremessages.hook;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.face.Face;
import dev.tmdevelopment.flaremessages.service.MessageService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * %flaremessages_join_number%      unique players that joined the server
 * %flaremessages_online_staff%     staff online (flaremessages.staff), not counting the ones in vanish
 * %flaremessages_first_join_date%  day of the player's first join (date-format of config.yml)
 * %flaremessages_days_played%      full days since the player's first join
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
        if (key.equals("online_staff")) {
            return String.valueOf(this.plugin.welcome().onlineStaff());
        }
        if (player == null) {
            return "";
        }
        if (key.equals("first_join_date")) {
            return player.hasPlayedBefore() || player.isOnline() ? this.plugin.messages().firstJoinDate(player) : "";
        }
        if (key.equals("days_played")) {
            return player.hasPlayedBefore() || player.isOnline() ? String.valueOf(MessageService.daysPlayed(player)) : "";
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
