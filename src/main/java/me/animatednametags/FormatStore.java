package me.animatednametags;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga y guarda los formatos en formats.yml (los edita la GUI).
 */
public final class FormatStore {

    private final AnimatedNametagsPlugin plugin;
    private final Map<String, NametagFormat> formats = new LinkedHashMap<>();
    private List<NametagFormat> sorted = List.of();
    private long version = 0;

    public FormatStore(AnimatedNametagsPlugin plugin) {
        this.plugin = plugin;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "formats.yml");
    }

    public void load() {
        File file = file();
        if (!file.exists()) {
            createInitialFile(file);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        formats.clear();
        ConfigurationSection section = yaml.getConfigurationSection("formats");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s != null) {
                    formats.put(id, NametagFormat.read(id, s));
                }
            }
        }
        resort();
        version++;
    }

    /** Migra los formatos del config.yml viejo (v1) si existen; si no, copia los de ejemplo. */
    private void createInitialFile(File file) {
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("formats");
        if (legacy != null) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Object> entry : legacy.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    yaml.set("formats." + entry.getKey(), entry.getValue());
                }
            }
            try {
                yaml.save(file);
                plugin.getLogger().info("Formatos migrados de config.yml a formats.yml");
                return;
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo migrar los formatos: " + e.getMessage());
            }
        }
        plugin.saveResource("formats.yml", false);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("formats");
        for (NametagFormat f : formats.values()) {
            f.write(root.createSection(f.id));
        }
        try {
            yaml.save(file());
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar formats.yml: " + e.getMessage());
        }
    }

    private void resort() {
        List<NametagFormat> list = new ArrayList<>(formats.values());
        list.sort(Comparator.comparingInt((NametagFormat f) -> f.priority).reversed()
                .thenComparing(f -> f.id));
        sorted = list;
    }

    /** Llamar despues de modificar un formato: guarda, reordena y fuerza a recrear los nametags. */
    public void touch() {
        for (NametagFormat f : formats.values()) {
            f.refreshBg();
        }
        resort();
        save();
        version++;
    }

    public NametagFormat get(String id) {
        return formats.get(id);
    }

    public List<NametagFormat> sorted() {
        return sorted;
    }

    public long version() {
        return version;
    }

    public NametagFormat create(String id) {
        NametagFormat f = new NametagFormat(id);
        f.lines.add(LineSpec.single("<white><player>"));
        formats.put(id, f);
        touch();
        return f;
    }

    public void delete(String id) {
        formats.remove(id);
        touch();
    }
}
