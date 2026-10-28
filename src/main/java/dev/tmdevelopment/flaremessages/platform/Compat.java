package dev.tmdevelopment.flaremessages.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Small things that changed between 1.16.5 and 26.x, read without linking to version-only methods. */
public final class Compat {
    private static Method getPing;
    private static boolean pingLooked;

    private Compat() {
    }

    /** Player#getPing() exists since 1.17: before that the value is read from the server player. */
    public static int ping(Player player) {
        if (!pingLooked) {
            pingLooked = true;
            try {
                getPing = Player.class.getMethod("getPing");
            } catch (NoSuchMethodException ignored) {
                getPing = null;
            }
        }
        try {
            if (getPing != null) {
                return (Integer) getPing.invoke(player);
            }
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Field field = handle.getClass().getField("ping");
            return field.getInt(handle);
        } catch (Throwable t) {
            return -1;
        }
    }
}
