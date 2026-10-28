package dev.tmdevelopment.flaremessages.face;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/**
 * Finds the skin url of an online player on any server from 1.16.5 to 26.x, without linking
 * to classes that only exist on some versions:
 * 1. Bukkit profile API (1.18.1+, Spigot and Paper)
 * 2. Paper profile API (Paper 1.16.5 - 1.18)
 * 3. the server's own game profile (Spigot 1.16.5 - 1.18)
 * It returns null when the profile has no skin (offline-mode servers): the plugin then downloads it by name.
 */
final class SkinLookup {
    private static final Pattern URL_IN_JSON = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");

    private SkinLookup() {
    }

    static String skinUrl(Player player) {
        String url = bukkitProfile(player);
        if (url == null) {
            url = paperProfile(player);
        }
        if (url == null) {
            url = gameProfile(player);
        }
        return url;
    }

    /** player.getPlayerProfile().getTextures().getSkin() */
    private static String bukkitProfile(Player player) {
        try {
            Object profile = call(player, "getPlayerProfile");
            Object textures = call(profile, "getTextures");
            Object skin = call(textures, "getSkin");
            return skin instanceof URL ? skin.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Paper: player.getPlayerProfile().getProperties() -> "textures" */
    private static String paperProfile(Player player) {
        try {
            Object profile = call(player, "getPlayerProfile");
            Object properties = call(profile, "getProperties");
            if (!(properties instanceof Collection)) {
                return null;
            }
            for (Object property : (Collection<?>) properties) {
                if ("textures".equals(call(property, "getName"))) {
                    return fromBase64((String) call(property, "getValue"));
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** CraftPlayer.getProfile() -> GameProfile.getProperties().get("textures") */
    private static String gameProfile(Player player) {
        try {
            Object profile = call(player, "getProfile");
            Object properties = call(profile, "getProperties");
            Method get = properties.getClass().getMethod("get", Object.class);
            Object values = get.invoke(properties, "textures");
            if (!(values instanceof Collection)) {
                return null;
            }
            for (Object property : (Collection<?>) values) {
                Object value;
                try {
                    value = call(property, "getValue");
                } catch (NoSuchMethodException e) {
                    value = call(property, "value"); // newer authlib (record)
                }
                if (value instanceof String) {
                    return fromBase64((String) value);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String fromBase64(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        int skin = json.indexOf("\"SKIN\"");
        Matcher m = URL_IN_JSON.matcher(skin >= 0 ? json.substring(skin) : json);
        return m.find() ? m.group(1) : null;
    }

    private static Object call(Object target, String method) throws Exception {
        if (target == null) {
            throw new NoSuchMethodException(method);
        }
        Method m = target.getClass().getMethod(method);
        if (!m.isAccessible()) {
            m.setAccessible(true);
        }
        return m.invoke(target);
    }
}
