package me.animatednametags;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Integracion con TAB mediante reflexion (sin dependencia en compilacion),
 * asi funciona con distintas versiones de TAB.
 */
public final class TabHook {

    private Object api;
    private Object nameTagManager;
    private Method getPlayer;
    private Method hideNametag;
    private Method showNametag;
    private boolean active;

    public boolean isActive() {
        return active;
    }

    public void init(Logger logger) {
        active = false;
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return;
            }

            Class<?> managerClass;
            try {
                managerClass = Class.forName("me.neznamy.tab.api.nametag.NameTagManager");
                nameTagManager = apiClass.getMethod("getNameTagManager").invoke(api);
            } catch (ReflectiveOperationException legacy) {
                managerClass = Class.forName("me.neznamy.tab.api.team.TeamManager");
                nameTagManager = apiClass.getMethod("getTeamManager").invoke(api);
            }

            if (nameTagManager == null) {
                logger.info("TAB detectado pero su feature de nametags esta desactivada. Nada que ocultar.");
                return;
            }

            getPlayer = apiClass.getMethod("getPlayer", UUID.class);
            hideNametag = managerClass.getMethod("hideNametag", tabPlayerClass);
            showNametag = managerClass.getMethod("showNametag", tabPlayerClass);
            active = true;
            logger.info("Integracion con TAB activada (nametags de TAB ocultos).");
        } catch (ClassNotFoundException e) {
            // TAB no esta instalado
        } catch (Throwable t) {
            logger.warning("No se pudo integrar con TAB: " + t.getMessage());
        }
    }

    public void hide(Player player) {
        invoke(hideNametag, player);
    }

    public void show(Player player) {
        invoke(showNametag, player);
    }

    private void invoke(Method method, Player player) {
        if (!active || method == null) {
            return;
        }
        try {
            Object tabPlayer = getPlayer.invoke(api, player.getUniqueId());
            if (tabPlayer != null) {
                method.invoke(nameTagManager, tabPlayer);
            }
        } catch (Throwable ignored) {
            // TAB puede estar recargandose
        }
    }
}
