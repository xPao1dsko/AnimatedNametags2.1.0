package me.animatednametags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NametagCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "animatednametags.admin";

    private final AnimatedNametagsPlugin plugin;
    private final NametagManager manager;
    private final GuiManager gui;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public NametagCommand(AnimatedNametagsPlugin plugin, NametagManager manager, GuiManager gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu", "editor" -> {
                if (!sender.hasPermission(ADMIN)) {
                    send(sender, "no-permission");
                } else if (sender instanceof Player p) {
                    gui.openList(p);
                } else {
                    send(sender, "usage");
                }
            }
            case "reload" -> {
                if (!sender.hasPermission(ADMIN)) {
                    send(sender, "no-permission");
                    return true;
                }
                manager.reload();
                send(sender, "reloaded");
            }
            case "refresh" -> {
                if (!sender.hasPermission(ADMIN)) {
                    send(sender, "no-permission");
                    return true;
                }
                manager.refreshAll();
                send(sender, "refreshed");
            }
            case "preview" -> {
                if (!sender.hasPermission(ADMIN)) {
                    send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player p) || args.length < 2) {
                    send(sender, "usage");
                    return true;
                }
                if (args[1].equalsIgnoreCase("off")) {
                    manager.clearPreview(p);
                    send(sender, "preview-off");
                } else if (manager.setPreview(p, args[1])) {
                    sendWithTarget(sender, "preview-set", args[1]);
                } else {
                    send(sender, "format-not-found");
                }
            }
            case "list" -> {
                if (!sender.hasPermission(ADMIN)) {
                    send(sender, "no-permission");
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (NametagFormat f : manager.store().sorted()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(f.id).append(" (").append(f.priority).append(")");
                }
                sender.sendMessage(Component.text("Formatos: " + sb));
            }
            case "toggle" -> {
                Player target;
                if (args.length >= 2) {
                    if (!sender.hasPermission(ADMIN)) {
                        send(sender, "no-permission");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        send(sender, "player-not-found");
                        return true;
                    }
                } else if (sender instanceof Player self) {
                    if (!sender.hasPermission("animatednametags.toggle")) {
                        send(sender, "no-permission");
                        return true;
                    }
                    target = self;
                } else {
                    send(sender, "usage");
                    return true;
                }
                boolean enabled = manager.toggle(target);
                sendWithTarget(sender, enabled ? "toggled-on" : "toggled-off", target.getName());
            }
            case "self" -> {
                if (!(sender instanceof Player self)) {
                    send(sender, "usage");
                    return true;
                }
                if (!sender.hasPermission("animatednametags.self")) {
                    send(sender, "no-permission");
                    return true;
                }
                boolean visible = manager.toggleSelf(self);
                send(sender, visible ? "self-shown" : "self-hidden");
            }
            case "debug" -> {
                if (!sender.hasPermission(ADMIN)) {
                    send(sender, "no-permission");
                    return true;
                }
                if (sender instanceof Player p) {
                    sender.sendMessage(Component.text(manager.debug(p)));
                }
            }
            default -> send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("gui", "toggle", "self", "preview", "list", "reload", "refresh", "debug")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && sender.hasPermission(ADMIN)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (args[0].equalsIgnoreCase("toggle")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("preview")) {
                if ("off".startsWith(prefix)) {
                    out.add("off");
                }
                for (NametagFormat f : manager.store().sorted()) {
                    if (f.id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(f.id);
                    }
                }
            }
        }
        return out;
    }

    private void send(CommandSender sender, String key) {
        sendWithTarget(sender, key, "");
    }

    private void sendWithTarget(CommandSender sender, String key, String target) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + key, key);
        sender.sendMessage(mm.deserialize(prefix + msg, Placeholder.unparsed("target", target)));
    }
}
