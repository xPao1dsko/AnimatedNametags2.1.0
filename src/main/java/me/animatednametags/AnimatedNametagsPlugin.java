package me.animatednametags;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnimatedNametagsPlugin extends JavaPlugin {

    private NametagManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Agrega al config.yml las claves nuevas de versiones recientes sin tocar las tuyas.
        getConfig().options().copyDefaults(true);
        // Claves de versiones anteriores que ya no existen.
        for (String old : new String[]{"attach-mode", "teleport-commands", "teleport-suspend-seconds",
                "follow-interval-ticks", "teleport-duration"}) {
            getConfig().set("settings." + old, null);
        }
        saveConfig();

        manager = new NametagManager(this);
        manager.start();

        GuiManager gui = new GuiManager(this, manager);
        getServer().getPluginManager().registerEvents(new PlayerListener(manager), this);
        getServer().getPluginManager().registerEvents(gui, this);

        NametagCommand command = new NametagCommand(this, manager, gui);
        PluginCommand pluginCommand = getCommand("animatednametags");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
