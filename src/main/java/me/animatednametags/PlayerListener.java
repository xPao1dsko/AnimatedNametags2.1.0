package me.animatednametags;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerListener implements Listener {

    private final NametagManager manager;

    public PlayerListener(NametagManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.onJoin(event.getPlayer());
    }

    // Lunar Client registra el canal "lunarclient:apollo" al entrar.
    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (NametagManager.LUNAR_CHANNEL.equals(event.getChannel())) {
            manager.markLunar(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer());
    }

    // Tras un teleport se recrea el nametag en la nueva posicion.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        manager.onTeleported(event.getPlayer());
    }

    // Un jugador empieza a ver a otro (o a su nametag): se le monta el nametag en el cliente.
    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        manager.onTracked(event.getPlayer(), event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        manager.scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.remove(event.getEntity());
    }
}
