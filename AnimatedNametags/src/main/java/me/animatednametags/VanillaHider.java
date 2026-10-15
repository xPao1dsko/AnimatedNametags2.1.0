package me.animatednametags;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Oculta el nametag vanilla metiendo a los jugadores en un team con
 * visibilidad de nametag = NEVER.
 */
public final class VanillaHider {

    private static final String TEAM_NAME = "ant_hidden";

    private Team team;

    public void enable() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.registerNewTeam(TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    public void disable() {
        if (team != null) {
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
                // ya estaba desregistrado
            }
            team = null;
        }
    }

    public void hide(Player player) {
        if (team == null) {
            return;
        }
        Scoreboard board = team.getScoreboard();
        Team current = board.getEntryTeam(player.getName());
        // Si esta en otro team (ej. TAB), no lo tocamos: de eso se encarga TabHook.
        if (current == null) {
            team.addEntry(player.getName());
        }
    }

    public void show(Player player) {
        if (team == null) {
            return;
        }
        try {
            team.removeEntry(player.getName());
        } catch (IllegalStateException ignored) {
            // team ya no existe
        }
    }
}
