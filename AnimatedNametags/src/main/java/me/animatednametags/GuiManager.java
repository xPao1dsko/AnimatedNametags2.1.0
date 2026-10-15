package me.animatednametags;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * GUI de administracion: lista de formatos, editor de formato y ajustes globales.
 * Los cambios de formatos se guardan en formats.yml y se aplican al instante.
 */
public final class GuiManager implements Listener {

    private static final List<String> BG_PRESETS =
            List.of("default", "none", "40000000", "80000000", "80FF0000", "8000AAFF");
    private static final List<String> ALIGNS = List.of("center", "left", "right");
    private static final List<String> SNEAK_MODES = List.of("dim", "hide", "none");

    private static final String[][] TOGGLES = {
            {"settings.hide-vanilla-nametags", "Ocultar nametag vanilla"},
            {"settings.hide-tab-nametags", "Ocultar nametag de TAB"},
            {"settings.show-own-nametag", "Ver tu propio nametag"},
            {"settings.hide-when-invisible", "Ocultar con invisibilidad"},
            {"settings.see-through", "Ver a traves de bloques"},
            {"settings.shadow", "Sombra del texto"},
            {"settings.legacy-colors", "Colores legacy (&c)"},
            {"lunar.hide-own-nametag-for-lunar-users", "Ocultar propio a usuarios de Lunar"},
    };

    // slots del editor
    private static final int E_BACK = 0, E_PREVIEW = 8, E_ADD_LINE = 27;
    private static final int E_PERM = 36, E_PRIORITY = 37, E_SPEED = 38, E_SCALE = 39, E_YOFF = 40,
            E_SHADOW = 41, E_SEETHRU = 42, E_ALIGN = 43, E_BG = 44, E_OPACITY = 45, E_BGANIM = 46,
            E_ICON = 47, E_ICONSCALE = 48, E_DELETE = 53;

    private final AnimatedNametagsPlugin plugin;
    private final NametagManager manager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    private abstract static class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class ListHolder extends Holder {
    }

    private static final class SettingsHolder extends Holder {
    }

    private static final class EditHolder extends Holder {
        final String id;

        EditHolder(String id) {
            this.id = id;
        }
    }

    public GuiManager(AnimatedNametagsPlugin plugin, NametagManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ------------------------------------------------------------------ utilidades

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(mm.deserialize("<!italic>" + line));
            }
            meta.lore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String esc(String text) {
        return mm.escapeTags(text == null ? "" : text);
    }

    private void msg(Player player, String text) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        player.sendMessage(mm.deserialize(prefix + text));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static int step(ClickType click, int small, int big) {
        return switch (click) {
            case LEFT -> small;
            case RIGHT -> -small;
            case SHIFT_LEFT -> big;
            case SHIFT_RIGHT -> -big;
            default -> 0;
        };
    }

    private static double step(ClickType click, double small, double big) {
        return switch (click) {
            case LEFT -> small;
            case RIGHT -> -small;
            case SHIFT_LEFT -> big;
            case SHIFT_RIGHT -> -big;
            default -> 0;
        };
    }

    private void prompt(Player player, String message, Consumer<String> handler, Runnable onCancel) {
        player.closeInventory();
        msg(player, message + " <dark_gray>(escribe <white>cancelar</white> para salir)</dark_gray>");
        pending.put(player.getUniqueId(), input -> {
            if (input.equalsIgnoreCase("cancelar")) {
                onCancel.run();
            } else {
                handler.accept(input);
            }
        });
    }

    // ------------------------------------------------------------------ lista de formatos

    public void openList(Player player) {
        ListHolder holder = new ListHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>AnimatedNametags <gray>» <white>Formatos"));
        holder.inv = inv;
        fillList(inv);
        player.openInventory(inv);
    }

    private void fillList(Inventory inv) {
        inv.clear();
        List<NametagFormat> list = manager.store().sorted();
        for (int i = 0; i < Math.min(45, list.size()); i++) {
            NametagFormat f = list.get(i);
            inv.setItem(i, item(Material.NAME_TAG, "<gold><bold>" + f.id,
                    "<gray>Permiso: <white>" + (f.permission.isEmpty() ? "(todos)" : esc(f.permission)),
                    "<gray>Prioridad: <white>" + f.priority,
                    "<gray>Lineas: <white>" + f.lines.size(),
                    "",
                    "<yellow>Click izq: <gray>editar",
                    "<yellow>Click der: <gray>previsualizar en ti",
                    "<red>Shift + click der: <gray>eliminar"));
        }
        inv.setItem(45, item(Material.COMPARATOR, "<aqua>Recargar todo", "<gray>Recarga config.yml y formats.yml"));
        inv.setItem(47, item(Material.REDSTONE_TORCH, "<yellow>Ajustes globales", "<gray>Opciones de config.yml"));
        inv.setItem(49, item(Material.LIME_DYE, "<green><bold>+ Crear formato", "<gray>Click para crear uno nuevo"));
        inv.setItem(51, item(Material.ENDER_EYE, "<light_purple>Quitar previsualizacion", "<gray>Vuelves a tu formato normal"));
        inv.setItem(53, item(Material.BARRIER, "<red>Cerrar"));
    }

    private void handleList(Player player, int slot, ClickType click, Inventory inv) {
        List<NametagFormat> list = manager.store().sorted();
        if (slot < 45) {
            if (slot >= list.size()) {
                return;
            }
            NametagFormat f = list.get(slot);
            switch (click) {
                case LEFT -> openEdit(player, f.id);
                case RIGHT -> {
                    manager.setPreview(player, f.id);
                    msg(player, "<green>Previsualizando <white>" + f.id + "</white>. Usa el boton de quitar para volver.");
                }
                case SHIFT_RIGHT -> {
                    if (list.size() <= 1) {
                        msg(player, "<red>No puedes eliminar el ultimo formato.</red>");
                        return;
                    }
                    manager.store().delete(f.id);
                    fillList(inv);
                }
                default -> {
                }
            }
            return;
        }
        switch (slot) {
            case 45 -> {
                manager.reload();
                fillList(inv);
                msg(player, "<green>Recargado.</green>");
            }
            case 47 -> openSettings(player);
            case 49 -> prompt(player, "Escribe el <white>id</white> del nuevo formato (a-z, 0-9, _ o -):", input -> {
                if (!input.matches("[a-zA-Z0-9_-]{1,24}")) {
                    msg(player, "<red>Id invalido.</red>");
                    openList(player);
                    return;
                }
                if (manager.store().get(input) != null) {
                    msg(player, "<red>Ese formato ya existe.</red>");
                    openList(player);
                    return;
                }
                manager.store().create(input);
                openEdit(player, input);
            }, () -> openList(player));
            case 51 -> {
                manager.clearPreview(player);
                msg(player, "<green>Previsualizacion quitada.</green>");
            }
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ editor de formato

    public void openEdit(Player player, String id) {
        NametagFormat f = manager.store().get(id);
        if (f == null) {
            openList(player);
            return;
        }
        EditHolder holder = new EditHolder(id);
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>Formato <gray>» <white>" + esc(id)));
        holder.inv = inv;
        fillEdit(inv, f);
        player.openInventory(inv);
    }

    private String val(Object own, Object global) {
        return own != null ? String.valueOf(own) : "(global: " + global + ")";
    }

    private void fillEdit(Inventory inv, NametagFormat f) {
        inv.clear();
        inv.setItem(E_BACK, item(Material.ARROW, "<yellow>« Volver"));
        inv.setItem(4, item(Material.NAME_TAG, "<gold><bold>Formato: " + f.id,
                "<gray>Los cambios se guardan y aplican al instante."));
        inv.setItem(E_PREVIEW, item(Material.ENDER_EYE, "<aqua>Previsualizar en ti",
                "<gray>Click: ver este formato en tu nametag", "<gray>(/ant preview off para quitarlo)"));

        for (int i = 0; i < Math.min(18, f.lines.size()); i++) {
            LineSpec spec = f.lines.get(i);
            String first = spec.frames().isEmpty() ? "" : spec.frames().get(0);
            inv.setItem(9 + i, item(Material.PAPER, "<yellow>Linea " + (i + 1)
                            + (spec.frames().size() > 1 ? " <gray>(" + spec.frames().size() + " frames, cada " + spec.interval() + "t)" : ""),
                    "<white>" + esc(first),
                    "",
                    "<yellow>Izq: <gray>editar texto",
                    "<yellow>Der: <gray>eliminar",
                    "<yellow>Shift+izq: <gray>subir",
                    "<yellow>Shift+der: <gray>añadir frame (animacion)",
                    "<yellow>Q (soltar): <gray>intervalo de frames"));
        }
        inv.setItem(E_ADD_LINE, item(Material.LIME_DYE, "<green>+ Añadir linea", "<gray>Maximo 18 lineas"));

        inv.setItem(E_PERM, item(Material.TRIPWIRE_HOOK, "<gold>Permiso",
                "<white>" + (f.permission.isEmpty() ? "(todos)" : esc(f.permission)), "", "<yellow>Click: <gray>cambiar (chat)"));
        inv.setItem(E_PRIORITY, item(Material.COMPARATOR, "<gold>Prioridad", "<white>" + f.priority, "",
                "<yellow>Izq/Der: <gray>+1 / -1", "<yellow>Shift: <gray>+10 / -10"));
        inv.setItem(E_SPEED, item(Material.CLOCK, "<gold>Velocidad de animacion", "<white>" + round(f.speed), "",
                "<yellow>Izq/Der: <gray>+0.005 / -0.005", "<yellow>Shift: <gray>+0.02 / -0.02"));
        inv.setItem(E_SCALE, item(Material.SPYGLASS, "<gold>Tamaño del texto",
                "<white>" + val(f.scale, manager.globalScale()), "",
                "<yellow>Izq/Der: <gray>+0.1 / -0.1", "<yellow>Shift der: <gray>usar global"));
        inv.setItem(E_YOFF, item(Material.LADDER, "<gold>Altura (y-offset)",
                "<white>" + val(f.yOffset, manager.globalYOffset()), "",
                "<yellow>Izq/Der: <gray>+0.05 / -0.05", "<yellow>Shift: <gray>+0.25 / -0.25"));
        inv.setItem(E_SHADOW, item(Material.BLACK_DYE, "<gold>Sombra del texto",
                "<white>" + val(f.shadow, manager.globalShadow()), "",
                "<yellow>Izq: <gray>alternar", "<yellow>Der: <gray>usar global"));
        inv.setItem(E_SEETHRU, item(Material.GLASS, "<gold>Ver a traves de bloques",
                "<white>" + val(f.seeThrough, manager.globalSeeThrough()), "",
                "<yellow>Izq: <gray>alternar", "<yellow>Der: <gray>usar global"));
        inv.setItem(E_ALIGN, item(Material.OAK_SIGN, "<gold>Alineacion",
                "<white>" + val(f.alignment, "center"), "",
                "<yellow>Izq: <gray>cambiar", "<yellow>Der: <gray>usar global"));
        inv.setItem(E_BG, item(Material.BLACK_STAINED_GLASS_PANE, "<gold>Fondo",
                "<white>" + val(f.background, manager.globalBackground()), "",
                "<yellow>Izq: <gray>siguiente preset", "<yellow>Der: <gray>hex personalizado (chat)",
                "<yellow>Shift der: <gray>usar global"));
        inv.setItem(E_OPACITY, item(Material.WHITE_STAINED_GLASS_PANE, "<gold>Opacidad del texto",
                "<white>" + (f.textOpacity == null ? "100%" : f.textOpacity + "%"), "",
                "<yellow>Izq/Der: <gray>+10% / -10%", "<yellow>Shift der: <gray>reiniciar"));
        inv.setItem(E_BGANIM, item(Material.REDSTONE_LAMP, "<gold>Fondo animado",
                "<white>" + (f.bgColors.isEmpty() ? "(desactivado)" : esc(String.join(", ", f.bgColors))),
                "<gray>Velocidad: <white>" + round(f.bgSpeed), "",
                "<yellow>Izq: <gray>colores (chat, separados por coma)", "<yellow>Der: <gray>desactivar",
                "<yellow>Shift: <gray>velocidad +/- 0.005"));
        inv.setItem(E_ICON, item(Material.ITEM_FRAME, "<gold>Icono (item) sobre el nametag",
                "<white>" + (f.iconMaterial == null ? "(ninguno)" : esc(f.iconMaterial)), "",
                "<yellow>Izq: <gray>elegir material (chat)", "<yellow>Der: <gray>quitar"));
        inv.setItem(E_ICONSCALE, item(Material.SPYGLASS, "<gold>Tamaño del icono", "<white>" + round(f.iconScale), "",
                "<yellow>Izq/Der: <gray>+0.1 / -0.1"));
        inv.setItem(E_DELETE, item(Material.BARRIER, "<red><bold>Eliminar formato", "<gray>Shift + click izquierdo"));
    }

    private void changed(Inventory inv, NametagFormat f) {
        manager.store().touch();
        fillEdit(inv, f);
    }

    private void done(Player player, NametagFormat f) {
        manager.store().touch();
        openEdit(player, f.id);
    }

    private void handleEdit(Player player, EditHolder holder, int slot, ClickType click, Inventory inv) {
        NametagFormat f = manager.store().get(holder.id);
        if (f == null) {
            openList(player);
            return;
        }
        Runnable back = () -> openEdit(player, f.id);

        if (slot == E_BACK) {
            openList(player);
            return;
        }
        if (slot == E_PREVIEW) {
            manager.setPreview(player, f.id);
            msg(player, "<green>Previsualizando <white>" + f.id + "</white>.</green>");
            return;
        }
        if (slot >= 9 && slot <= 26) {
            handleLine(player, f, slot - 9, click, inv, back);
            return;
        }

        switch (slot) {
            case E_ADD_LINE -> {
                if (f.lines.size() >= 18) {
                    msg(player, "<red>Maximo 18 lineas.</red>");
                    return;
                }
                prompt(player, "Escribe el texto de la nueva linea (MiniMessage):", input -> {
                    f.lines.add(LineSpec.single(input));
                    done(player, f);
                }, back);
            }
            case E_PERM -> prompt(player, "Escribe el permiso (o <white>ninguno</white>):", input -> {
                f.permission = input.equalsIgnoreCase("ninguno") ? "" : input;
                done(player, f);
            }, back);
            case E_PRIORITY -> {
                int d = step(click, 1, 10);
                if (d != 0) {
                    f.priority += d;
                    changed(inv, f);
                }
            }
            case E_SPEED -> {
                double d = step(click, 0.005, 0.02);
                if (d != 0) {
                    f.speed = Math.max(0.0, round(f.speed + d));
                    changed(inv, f);
                }
            }
            case E_SCALE -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    f.scale = null;
                    changed(inv, f);
                    return;
                }
                double d = step(click, 0.1, 0.0);
                if (d != 0) {
                    double current = f.scale != null ? f.scale : manager.globalScale();
                    f.scale = Math.max(0.2, Math.min(5.0, round(current + d)));
                    changed(inv, f);
                }
            }
            case E_YOFF -> {
                double d = step(click, 0.05, 0.25);
                if (d != 0) {
                    double current = f.yOffset != null ? f.yOffset : manager.globalYOffset();
                    f.yOffset = round(current + d);
                    changed(inv, f);
                }
            }
            case E_SHADOW -> {
                if (click == ClickType.LEFT) {
                    f.shadow = !(f.shadow != null ? f.shadow : manager.globalShadow());
                    changed(inv, f);
                } else if (click == ClickType.RIGHT) {
                    f.shadow = null;
                    changed(inv, f);
                }
            }
            case E_SEETHRU -> {
                if (click == ClickType.LEFT) {
                    f.seeThrough = !(f.seeThrough != null ? f.seeThrough : manager.globalSeeThrough());
                    changed(inv, f);
                } else if (click == ClickType.RIGHT) {
                    f.seeThrough = null;
                    changed(inv, f);
                }
            }
            case E_ALIGN -> {
                if (click == ClickType.LEFT) {
                    int idx = f.alignment == null ? 0 : ALIGNS.indexOf(f.alignment.toLowerCase());
                    f.alignment = ALIGNS.get((idx + 1) % ALIGNS.size());
                    changed(inv, f);
                } else if (click == ClickType.RIGHT) {
                    f.alignment = null;
                    changed(inv, f);
                }
            }
            case E_BG -> {
                if (click == ClickType.LEFT) {
                    int idx = f.background == null ? -1 : BG_PRESETS.indexOf(f.background);
                    f.background = BG_PRESETS.get((idx + 1) % BG_PRESETS.size());
                    changed(inv, f);
                } else if (click == ClickType.RIGHT) {
                    prompt(player, "Escribe el color de fondo en hex <white>AARRGGBB</white> o <white>RRGGBB</white>:", input -> {
                        if (ColorUtil.parseArgb(input) == null) {
                            msg(player, "<red>Hex invalido.</red>");
                        } else {
                            f.background = input.replace("#", "");
                        }
                        done(player, f);
                    }, back);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    f.background = null;
                    changed(inv, f);
                }
            }
            case E_OPACITY -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    f.textOpacity = null;
                    changed(inv, f);
                    return;
                }
                int d = step(click, 10, 0);
                if (d != 0) {
                    int current = f.textOpacity != null ? f.textOpacity : 100;
                    f.textOpacity = Math.max(10, Math.min(100, current + d));
                    changed(inv, f);
                }
            }
            case E_BGANIM -> {
                if (click == ClickType.LEFT) {
                    prompt(player, "Escribe los colores separados por coma, ej: <white>80FF0000,8000AAFF</white>:", input -> {
                        List<String> colors = new ArrayList<>();
                        for (String part : input.split(",")) {
                            String c = part.trim();
                            if (ColorUtil.parseArgb(c) != null) {
                                colors.add(c.replace("#", ""));
                            }
                        }
                        if (colors.isEmpty()) {
                            msg(player, "<red>Ningun color valido.</red>");
                        } else {
                            f.bgColors.clear();
                            f.bgColors.addAll(colors);
                        }
                        done(player, f);
                    }, back);
                } else if (click == ClickType.RIGHT) {
                    f.bgColors.clear();
                    changed(inv, f);
                } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    double d = click == ClickType.SHIFT_LEFT ? 0.005 : -0.005;
                    f.bgSpeed = Math.max(0.001, round(f.bgSpeed + d));
                    changed(inv, f);
                }
            }
            case E_ICON -> {
                if (click == ClickType.LEFT) {
                    prompt(player, "Escribe el material del icono (ej: <white>NETHER_STAR</white>):", input -> {
                        Material m = Material.matchMaterial(input);
                        if (m == null || !m.isItem() || m.isAir()) {
                            msg(player, "<red>Material invalido.</red>");
                        } else {
                            f.iconMaterial = m.name();
                        }
                        done(player, f);
                    }, back);
                } else if (click == ClickType.RIGHT) {
                    f.iconMaterial = null;
                    changed(inv, f);
                }
            }
            case E_ICONSCALE -> {
                double d = step(click, 0.1, 0.0);
                if (d != 0) {
                    f.iconScale = Math.max(0.2, Math.min(3.0, round(f.iconScale + d)));
                    changed(inv, f);
                }
            }
            case E_DELETE -> {
                if (click == ClickType.SHIFT_LEFT) {
                    if (manager.store().sorted().size() <= 1) {
                        msg(player, "<red>No puedes eliminar el ultimo formato.</red>");
                        return;
                    }
                    manager.store().delete(f.id);
                    openList(player);
                }
            }
            default -> {
            }
        }
    }

    private void handleLine(Player player, NametagFormat f, int idx, ClickType click, Inventory inv, Runnable back) {
        if (idx >= f.lines.size()) {
            return;
        }
        LineSpec spec = f.lines.get(idx);
        switch (click) {
            case LEFT -> prompt(player, "Escribe el nuevo texto de la linea " + (idx + 1) + " (MiniMessage):", input -> {
                f.lines.set(idx, LineSpec.single(input));
                done(player, f);
            }, back);
            case RIGHT -> {
                f.lines.remove(idx);
                changed(inv, f);
            }
            case SHIFT_LEFT -> {
                if (idx > 0) {
                    Collections.swap(f.lines, idx, idx - 1);
                    changed(inv, f);
                }
            }
            case SHIFT_RIGHT -> prompt(player, "Escribe el texto del nuevo <white>frame</white> para la linea " + (idx + 1) + ":", input -> {
                List<String> frames = new ArrayList<>(spec.frames());
                frames.add(input);
                f.lines.set(idx, new LineSpec(frames, spec.interval()));
                done(player, f);
            }, back);
            case DROP -> prompt(player, "Cada cuantos ticks cambia de frame? (numero, ej: <white>10</white>):", input -> {
                try {
                    int n = Math.max(1, Integer.parseInt(input.trim()));
                    f.lines.set(idx, new LineSpec(spec.frames(), n));
                } catch (NumberFormatException e) {
                    msg(player, "<red>Numero invalido.</red>");
                }
                done(player, f);
            }, back);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ ajustes globales

    public void openSettings(Player player) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>AnimatedNametags <gray>» <white>Ajustes"));
        holder.inv = inv;
        fillSettings(inv);
        player.openInventory(inv);
    }

    private void fillSettings(Inventory inv) {
        inv.clear();
        FileConfiguration c = plugin.getConfig();
        for (int i = 0; i < TOGGLES.length; i++) {
            boolean on = c.getBoolean(TOGGLES[i][0]);
            inv.setItem(9 + i, item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    (on ? "<green>" : "<red>") + TOGGLES[i][1],
                    "<gray>Estado: " + (on ? "<green>activado" : "<red>desactivado"), "",
                    "<yellow>Click: <gray>alternar"));
        }
        inv.setItem(27, item(Material.LEATHER_BOOTS, "<gold>Al agacharse",
                "<white>" + c.getString("settings.sneak-mode", "dim"), "", "<yellow>Click: <gray>cambiar (dim / hide / none)"));
        inv.setItem(28, item(Material.CLOCK, "<gold>Intervalo de actualizacion (ticks)",
                "<white>" + c.getInt("settings.update-interval-ticks", 2), "", "<yellow>Izq/Der: <gray>+1 / -1"));
        inv.setItem(29, item(Material.LADDER, "<gold>Altura global (y-offset)",
                "<white>" + round(c.getDouble("settings.y-offset", 0.25)), "", "<yellow>Izq/Der: <gray>+0.05 / -0.05"));
        inv.setItem(30, item(Material.SPYGLASS, "<gold>Tamaño global",
                "<white>" + round(c.getDouble("settings.scale", 1.0)), "", "<yellow>Izq/Der: <gray>+0.1 / -0.1"));
        inv.setItem(31, item(Material.ENDER_PEARL, "<gold>Distancia de vision",
                "<white>" + round(c.getDouble("settings.view-range", 0.6)), "", "<yellow>Izq/Der: <gray>+0.1 / -0.1"));
        inv.setItem(32, item(Material.BLACK_STAINED_GLASS_PANE, "<gold>Fondo global",
                "<white>" + c.getString("settings.background", "40000000"), "", "<yellow>Click: <gray>siguiente preset"));
        inv.setItem(49, item(Material.ARROW, "<yellow>« Volver"));
    }

    private void applyConfig(Inventory inv) {
        plugin.saveConfig();
        manager.reload();
        fillSettings(inv);
    }

    private void handleSettings(Player player, int slot, ClickType click, Inventory inv) {
        FileConfiguration c = plugin.getConfig();
        if (slot >= 9 && slot < 9 + TOGGLES.length) {
            String path = TOGGLES[slot - 9][0];
            c.set(path, !c.getBoolean(path));
            applyConfig(inv);
            return;
        }
        switch (slot) {
            case 27 -> {
                int idx = SNEAK_MODES.indexOf(c.getString("settings.sneak-mode", "dim"));
                c.set("settings.sneak-mode", SNEAK_MODES.get((idx + 1) % SNEAK_MODES.size()));
                applyConfig(inv);
            }
            case 28 -> {
                int d = step(click, 1, 0);
                if (d != 0) {
                    c.set("settings.update-interval-ticks",
                            Math.max(1, Math.min(20, c.getInt("settings.update-interval-ticks", 2) + d)));
                    applyConfig(inv);
                }
            }
            case 29 -> {
                double d = step(click, 0.05, 0.0);
                if (d != 0) {
                    c.set("settings.y-offset", round(c.getDouble("settings.y-offset", 0.25) + d));
                    applyConfig(inv);
                }
            }
            case 30 -> {
                double d = step(click, 0.1, 0.0);
                if (d != 0) {
                    c.set("settings.scale", Math.max(0.2, Math.min(5.0, round(c.getDouble("settings.scale", 1.0) + d))));
                    applyConfig(inv);
                }
            }
            case 31 -> {
                double d = step(click, 0.1, 0.0);
                if (d != 0) {
                    c.set("settings.view-range", Math.max(0.1, Math.min(3.0, round(c.getDouble("settings.view-range", 0.6) + d))));
                    applyConfig(inv);
                }
            }
            case 32 -> {
                int idx = BG_PRESETS.indexOf(c.getString("settings.background", "40000000"));
                c.set("settings.background", BG_PRESETS.get((idx + 1) % BG_PRESETS.size()));
                applyConfig(inv);
            }
            case 49 -> openList(player);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("animatednametags.admin")) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        int slot = event.getSlot();
        ClickType click = event.getClick();
        if (holder instanceof ListHolder) {
            handleList(player, slot, click, top);
        } else if (holder instanceof EditHolder editHolder) {
            handleEdit(player, editHolder, slot, click, top);
        } else if (holder instanceof SettingsHolder) {
            handleSettings(player, slot, click, top);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
