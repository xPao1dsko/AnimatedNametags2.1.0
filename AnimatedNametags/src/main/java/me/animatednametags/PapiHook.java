package me.animatednametags;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Esta clase solo se carga si PlaceholderAPI esta presente.
 */
final class PapiHook {

    private PapiHook() {
    }

    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
