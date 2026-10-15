package me.animatednametags;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Un formato de nametag. Los campos "nullable" usan el valor global del config.yml cuando son null.
 */
public final class NametagFormat {

    public final String id;
    public String permission = "";
    public int priority = 0;
    public double speed = 0.02;
    public final List<LineSpec> lines = new ArrayList<>();

    public Boolean shadow;
    public Boolean seeThrough;
    public Double scale;
    public Double yOffset;
    public String alignment;
    public String background;
    public Integer textOpacity;

    public final List<String> bgColors = new ArrayList<>();
    public double bgSpeed = 0.01;
    public int[] bgArgb = new int[0];

    public String iconMaterial;
    public double iconScale = 0.6;
    public double iconYOffset = 0.0;

    public NametagFormat(String id) {
        this.id = id;
    }

    public void refreshBg() {
        List<Integer> parsed = new ArrayList<>();
        for (String s : bgColors) {
            Integer argb = ColorUtil.parseArgb(s);
            if (argb != null) {
                parsed.add(argb);
            }
        }
        bgArgb = parsed.stream().mapToInt(Integer::intValue).toArray();
    }

    static NametagFormat read(String id, ConfigurationSection s) {
        NametagFormat f = new NametagFormat(id);
        f.permission = s.getString("permission", "");
        f.priority = s.getInt("priority", 0);
        f.speed = s.getDouble("animation-speed", 0.02);

        List<?> raw = s.getList("lines");
        if (raw != null) {
            for (Object o : raw) {
                LineSpec spec = LineSpec.parse(o);
                if (spec != null) {
                    f.lines.add(spec);
                }
            }
        }

        if (s.contains("shadow")) f.shadow = s.getBoolean("shadow");
        if (s.contains("see-through")) f.seeThrough = s.getBoolean("see-through");
        if (s.contains("scale")) f.scale = s.getDouble("scale");
        if (s.contains("y-offset")) f.yOffset = s.getDouble("y-offset");
        if (s.contains("alignment")) f.alignment = s.getString("alignment");
        if (s.contains("background")) f.background = s.getString("background");
        if (s.contains("text-opacity")) f.textOpacity = s.getInt("text-opacity");

        f.bgColors.addAll(s.getStringList("background-animation.colors"));
        f.bgSpeed = s.getDouble("background-animation.speed", 0.01);
        f.refreshBg();

        f.iconMaterial = s.getString("icon.material");
        f.iconScale = s.getDouble("icon.scale", 0.6);
        f.iconYOffset = s.getDouble("icon.y-offset", 0.0);
        return f;
    }

    void write(ConfigurationSection s) {
        s.set("permission", permission);
        s.set("priority", priority);
        s.set("animation-speed", speed);

        List<Object> out = new ArrayList<>();
        for (LineSpec spec : lines) {
            out.add(spec.serialize());
        }
        s.set("lines", out);

        s.set("shadow", shadow);
        s.set("see-through", seeThrough);
        s.set("scale", scale);
        s.set("y-offset", yOffset);
        s.set("alignment", alignment);
        s.set("background", background);
        s.set("text-opacity", textOpacity);

        if (!bgColors.isEmpty()) {
            s.set("background-animation.colors", new ArrayList<>(bgColors));
            s.set("background-animation.speed", bgSpeed);
        }
        if (iconMaterial != null && !iconMaterial.isEmpty()) {
            s.set("icon.material", iconMaterial);
            s.set("icon.scale", iconScale);
            s.set("icon.y-offset", iconYOffset);
        }
    }
}
