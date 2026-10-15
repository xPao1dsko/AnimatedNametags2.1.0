package me.animatednametags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestiona un TextDisplay (multilinea, MiniMessage) + ItemDisplay opcional montados en cada jugador.
 */
public final class NametagManager {

    static final String ENTITY_TAG = "animatednametags";
    public static final String LUNAR_CHANNEL = "lunarclient:apollo";
    /** El nametag se mueve cada tick con interpolacion de 3 ticks (igual que el cuerpo de otros jugadores). */
    private static final int FOLLOW_INTERVAL_TICKS = 1;
    private static final int TELEPORT_DURATION = 3;
    /** Si el jugador se aleja mas de 6 bloques de golpe (teleport) se recrea el nametag. */
    private static final double SNAP_DISTANCE_SQ = 36.0;
    /** Con montaje por paquetes la posicion del servidor solo importa para el tracking: basta cada 4 ticks. */
    private static final int PACKET_FOLLOW_EVERY = 4;
    /** Reenvio periodico del montaje por si otro plugin lo pisa (2 segundos). */
    private static final int RESEND_EVERY = 40;
    private static final Pattern PRESET = Pattern.compile("\\{preset:([A-Za-z0-9_-]+)\\}");

    private final AnimatedNametagsPlugin plugin;
    private final FormatStore store;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Tag> tags = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final Map<UUID, String> previews = new HashMap<>();
    private final Map<String, String> presets = new HashMap<>();
    private final Set<String> disabledWorlds = new HashSet<>();
    private final VanillaHider vanilla = new VanillaHider();
    private final TabHook tab = new TabHook();

    private BukkitTask task;
    private BukkitTask followTask;
    private boolean packetMount;
    private long followTicks;
    private final Map<Integer, UUID> displayOwners = new HashMap<>();
    private long tick;
    private boolean papi;

    // nametag propio por jugador + deteccion de Lunar Client
    private final Map<UUID, Boolean> selfOverride = new HashMap<>();
    private final Set<UUID> lunarUsers = new HashSet<>();
    private boolean lunarHideOwn;

    // settings globales
    private int interval;
    private boolean hideVanilla;
    private boolean hideTab;
    private boolean showOwn;
    private boolean hideWhenInvisible;
    private boolean seeThrough;
    private String sneakMode;
    private float yOffset;
    private float scale;
    private int lineWidth;
    private boolean shadow;
    private String background;
    private float viewRange;
    private boolean legacyColors;

    private static final class Tag {
        final TextDisplay display;
        final ItemDisplay icon;
        final String formatId;
        final long version;
        final boolean baseSeeThrough;
        final byte baseOpacity;
        String lastKey;
        boolean sneakApplied;
        boolean iconHidden;
        int lastBg = Integer.MIN_VALUE;
        Location lastPos;
        final int displayId;
        final int iconId;

        Tag(TextDisplay display, ItemDisplay icon, String formatId, long version,
            boolean baseSeeThrough, byte baseOpacity) {
            this.display = display;
            this.icon = icon;
            this.formatId = formatId;
            this.version = version;
            this.baseSeeThrough = baseSeeThrough;
            this.baseOpacity = baseOpacity;
            this.displayId = display.getEntityId();
            this.iconId = icon != null ? icon.getEntityId() : -1;
        }
    }

    public NametagManager(AnimatedNametagsPlugin plugin) {
        this.plugin = plugin;
        this.store = new FormatStore(plugin);
    }

    public FormatStore store() {
        return store;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void start() {
        loadSettings();
        store.load();
        loadData();
        cleanupOrphans();
        packetMount = PacketMount.init(plugin.getLogger());

        if (hideVanilla) {
            vanilla.enable();
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 1L, interval);
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, FOLLOW_INTERVAL_TICKS);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (hideTab) {
                tab.init(plugin.getLogger());
            }
            applyHidingToAll();
        });
        Bukkit.getScheduler().runTaskLater(plugin, this::applyHidingToAll, 60L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        for (Tag tag : new ArrayList<>(tags.values())) {
            discard(tag);
        }
        tags.clear();
        displayOwners.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            vanilla.show(player);
            if (tab.isActive()) {
                tab.show(player);
            }
        }
        vanilla.disable();
    }

    public void reload() {
        plugin.reloadConfig();
        shutdown();
        start();
    }

    private void loadSettings() {
        FileConfiguration c = plugin.getConfig();
        interval = Math.max(1, c.getInt("settings.update-interval-ticks", 2));
        hideVanilla = c.getBoolean("settings.hide-vanilla-nametags", true);
        hideTab = c.getBoolean("settings.hide-tab-nametags", true);
        showOwn = c.getBoolean("settings.show-own-nametag", false);
        hideWhenInvisible = c.getBoolean("settings.hide-when-invisible", true);
        seeThrough = c.getBoolean("settings.see-through", true);
        sneakMode = c.getString("settings.sneak-mode", "dim").toLowerCase(Locale.ROOT);
        yOffset = (float) c.getDouble("settings.y-offset", 0.25);
        scale = (float) c.getDouble("settings.scale", 1.0);
        lineWidth = c.getInt("settings.line-width", 400);
        shadow = c.getBoolean("settings.shadow", false);
        background = c.getString("settings.background", "40000000");
        viewRange = (float) c.getDouble("settings.view-range", 0.6);
        legacyColors = c.getBoolean("settings.legacy-colors", true);
        lunarHideOwn = c.getBoolean("lunar.hide-own-nametag-for-lunar-users", false);
        papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList("settings.disabled-worlds"));

        presets.clear();
        ConfigurationSection ps = c.getConfigurationSection("presets");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                String value = ps.getString(key);
                if (value != null) {
                    presets.put(key, value);
                }
            }
        }
    }

    // getters globales (usados por la GUI)
    public double globalScale() { return scale; }
    public double globalYOffset() { return yOffset; }
    public boolean globalShadow() { return shadow; }
    public boolean globalSeeThrough() { return seeThrough; }
    public String globalBackground() { return background; }

    private void cleanupOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(TextDisplay.class, ItemDisplay.class)) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    public void onJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getListeningPluginChannels().contains(LUNAR_CHANNEL)) {
                markLunar(player);
            }
        }, 40L);
        for (long delay : new long[]{1L, 40L, 100L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    applyHiding(player);
                }
            }, delay);
        }
    }

    public void onQuit(Player player) {
        lunarUsers.remove(player.getUniqueId());
        previews.remove(player.getUniqueId());
        remove(player);
        vanilla.show(player);
    }

    public void scheduleRefresh(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                remove(player);
                tickPlayer(player);
            }
        }, 2L);
    }

    // ------------------------------------------------------------------ ocultar vanilla / TAB

    private void applyHidingToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyHiding(player);
        }
    }

    private void applyHiding(Player player) {
        if (disabled.contains(player.getUniqueId())) {
            vanilla.show(player);
            if (tab.isActive()) {
                tab.show(player);
            }
            return;
        }
        if (hideVanilla) {
            vanilla.hide(player);
        }
        if (hideTab && tab.isActive()) {
            tab.hide(player);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
        applyHidingToAll();
    }

    /** @return true si el nametag queda activado. */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        boolean nowEnabled;
        if (disabled.remove(id)) {
            nowEnabled = true;
        } else {
            disabled.add(id);
            nowEnabled = false;
            remove(player);
        }
        applyHiding(player);
        return nowEnabled;
    }

    // ------------------------------------------------------------------ teleports

    /**
     * El nametag NO va montado en el jugador (un jugador con pasajeros puede ser rechazado por
     * los teleports de otros plugins: Multiverse, RTP, Essentials...). Tras un teleport se
     * recrea en la nueva posicion para que no "vuele" desde el punto anterior.
     */
    public void onTeleported(Player player) {
        remove(player);
        scheduleRefresh(player);
    }

    // ------------------------------------------------------------------ preview

    public boolean setPreview(Player player, String formatId) {
        if (store.get(formatId) == null) {
            return false;
        }
        previews.put(player.getUniqueId(), formatId);
        return true;
    }

    public void clearPreview(Player player) {
        previews.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ bucle de animacion

    private void run() {
        tick += interval;
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(Player player) {
        UUID id = player.getUniqueId();

        if (disabled.contains(id)
                || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR
                || disabledWorlds.contains(player.getWorld().getName())) {
            remove(player);
            return;
        }

        NametagFormat format = pickFormat(player);
        if (format == null || format.lines.isEmpty()) {
            remove(player);
            return;
        }

        Tag tag = tags.get(id);
        if (tag == null
                || !isTagValid(player, tag)
                || !tag.formatId.equals(format.id)
                || tag.version != store.version()) {
            remove(player);
            tag = spawn(player, format);
            if (tag == null) {
                return;
            }
            tags.put(id, tag);
        }

        boolean sneaking = player.isSneaking();
        boolean hideNow = (hideWhenInvisible && player.hasPotionEffect(PotionEffectType.INVISIBILITY))
                || isVanished(player)
                || (sneaking && sneakMode.equals("hide"));

        List<String> raws = null;
        String key;
        if (hideNow) {
            key = "\u0000hidden";
        } else {
            raws = buildRaw(player, format);
            key = buildKey(player, raws);
        }

        if (!key.equals(tag.lastKey)) {
            Component text = hideNow ? Component.empty() : parse(player, raws);
            tag.display.text(text);
            tag.lastKey = key;
            if (tag.icon != null && tag.iconHidden != hideNow) {
                tag.icon.setItemStack(hideNow ? new ItemStack(Material.AIR) : iconStack(format));
                tag.iconHidden = hideNow;
            }
        }

        // fondo animado
        if (!hideNow && format.bgArgb.length > 0) {
            int argb = sampleBackground(format.bgArgb, format.bgSpeed);
            if (argb != tag.lastBg) {
                tag.display.setBackgroundColor(ColorUtil.toColor(argb));
                tag.lastBg = argb;
            }
        }

        if (sneaking != tag.sneakApplied) {
            if (sneakMode.equals("dim")) {
                tag.display.setSeeThrough(tag.baseSeeThrough && !sneaking);
                tag.display.setTextOpacity(sneaking ? (byte) 110 : tag.baseOpacity);
            }
            tag.sneakApplied = sneaking;
        }
    }

    private boolean isTagValid(Player player, Tag tag) {
        if (!tag.display.isValid()) {
            return false;
        }
        if (tag.icon != null && !tag.icon.isValid()) {
            return false;
        }
        return tag.display.getWorld().equals(player.getWorld());
    }

    /**
     * Con montaje por paquetes: mantiene la entidad del servidor cerca del jugador (para el tracking)
     * y reenvia el montaje de vez en cuando. Sin paquetes (respaldo): mueve el nametag cada tick.
     */
    private void follow() {
        followTicks++;
        boolean move = !packetMount || followTicks % PACKET_FOLLOW_EVERY == 0;
        boolean resend = packetMount && followTicks % RESEND_EVERY == 0;

        Iterator<Map.Entry<UUID, Tag>> it = tags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Tag> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Tag tag = entry.getValue();
            if (player == null || !tag.display.isValid()
                    || !tag.display.getWorld().equals(player.getWorld())) {
                continue; // el bucle principal lo recrea
            }
            if (move) {
                Location target = player.getLocation();
                target.add(0, player.getHeight(), 0);
                Location last = tag.lastPos;
                boolean skip = false;
                if (last != null && last.getWorld().equals(target.getWorld())) {
                    double distance = last.distanceSquared(target);
                    if (distance < 1.0E-6) {
                        skip = true;
                    } else if (!packetMount && distance > SNAP_DISTANCE_SQ) {
                        discard(tag); // salto grande (teleport): se recrea en el sitio nuevo
                        it.remove();
                        continue;
                    }
                }
                if (!skip) {
                    tag.lastPos = target.clone();
                    tag.display.teleport(target);
                    if (tag.icon != null && tag.icon.isValid()) {
                        tag.icon.teleport(target);
                    }
                }
            }
            if (resend) {
                for (Player viewer : player.getWorld().getNearbyPlayers(player.getLocation(), 48)) {
                    if (viewer.equals(player) && !isSelfVisible(player)) {
                        continue;
                    }
                    sendMount(viewer, player, tag);
                }
            }
        }
    }

    // ------------------------------------------------------------------ montaje por paquetes

    /** Un espectador empieza a ver al jugador o a su nametag: se le envia el montaje. */
    public void onTracked(Player viewer, Entity entity) {
        if (!packetMount) {
            return;
        }
        UUID ownerId = null;
        if (entity instanceof Player tracked) {
            if (tags.containsKey(tracked.getUniqueId())) {
                ownerId = tracked.getUniqueId();
            }
        } else {
            ownerId = displayOwners.get(entity.getEntityId());
        }
        if (ownerId == null) {
            return;
        }
        final UUID id = ownerId;
        for (long delay : new long[]{1L, 10L}) { // el segundo envio cubre cualquier carrera
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player owner = Bukkit.getPlayer(id);
                Tag tag = tags.get(id);
                if (owner == null || tag == null || !viewer.isOnline() || !tag.display.isValid()
                        || !viewer.getWorld().equals(owner.getWorld())) {
                    return;
                }
                if (viewer.equals(owner) && !isSelfVisible(owner)) {
                    return;
                }
                sendMount(viewer, owner, tag);
            }, delay);
        }
    }

    private void sendMount(Player viewer, Player owner, Tag tag) {
        if (!packetMount) {
            return;
        }
        List<Entity> real = owner.getPassengers(); // pasajeros reales de otros plugins se conservan
        int[] ids = new int[real.size() + (tag.icon != null ? 2 : 1)];
        int i = 0;
        for (Entity passenger : real) {
            ids[i++] = passenger.getEntityId();
        }
        ids[i++] = tag.displayId;
        if (tag.icon != null) {
            ids[i] = tag.iconId;
        }
        try {
            PacketMount.send(viewer, owner.getEntityId(), ids);
        } catch (Throwable t) {
            disablePacketMode(t);
        }
    }

    private void disablePacketMode(Throwable cause) {
        if (!packetMount) {
            return;
        }
        packetMount = false;
        plugin.getLogger().warning("Fallo el montaje por paquetes (" + cause + "). Se usa el modo de seguimiento.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Tag tag : new ArrayList<>(tags.values())) {
                discard(tag);
            }
            tags.clear();
            displayOwners.clear();
        });
    }

    private Location spawnLocation(Player player) {
        Location loc = player.getLocation();
        loc.add(0, player.getHeight(), 0);
        return loc;
    }

    private void applyFollow(Display display) {
        display.setTeleportDuration(packetMount ? 0 : TELEPORT_DURATION);
    }

    private boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private NametagFormat pickFormat(Player player) {
        String preview = previews.get(player.getUniqueId());
        if (preview != null) {
            NametagFormat f = store.get(preview);
            if (f != null) {
                return f;
            }
        }
        for (NametagFormat f : store.sorted()) {
            if (f.permission == null || f.permission.isEmpty() || player.hasPermission(f.permission)) {
                return f;
            }
        }
        return null;
    }

    private int sampleBackground(int[] colors, double speed) {
        if (colors.length == 1) {
            return colors[0];
        }
        double pos = (tick * speed) % 1.0;
        double scaled = pos * colors.length;
        int index = (int) scaled;
        double t = scaled - index;
        return ColorUtil.lerp(colors[index % colors.length], colors[(index + 1) % colors.length], t);
    }

    // ------------------------------------------------------------------ render

    private List<String> buildRaw(Player player, NametagFormat format) {
        double phaseValue = ((tick * format.speed) % 2.0) - 1.0;
        String phase = fmt(phaseValue);
        String phaseRev = fmt(-phaseValue);
        String tickStr = Long.toString(tick);

        List<String> out = new ArrayList<>(format.lines.size());
        for (LineSpec spec : format.lines) {
            int size = spec.frames().size();
            int idx = size <= 1 ? 0 : (int) ((tick / Math.max(1, spec.interval())) % size);
            String line = spec.frames().get(idx);

            if (line.contains("{preset:")) {
                line = PRESET.matcher(line).replaceAll(
                        m -> Matcher.quoteReplacement(presets.getOrDefault(m.group(1), "white")));
            }
            line = line.replace("{phase}", phase)
                    .replace("{phase_rev}", phaseRev)
                    .replace("{tick}", tickStr);
            if (papi) {
                line = PapiHook.apply(player, line);
            }
            if (legacyColors) {
                line = LegacyConverter.convert(line);
            }
            out.add(line);
        }
        return out;
    }

    private String buildKey(Player player, List<String> raws) {
        return String.join("\u0001", raws)
                + "\u0002" + (int) Math.ceil(player.getHealth())
                + "\u0002" + player.getPing()
                + "\u0002" + player.getLevel()
                + "\u0002" + player.getGameMode().ordinal()
                + "\u0002" + player.getWorld().getName()
                + "\u0002" + player.displayName().hashCode();
    }

    private Component parse(Player player, List<String> raws) {
        TagResolver resolver = resolver(player);
        List<Component> lines = new ArrayList<>(raws.size());
        for (String raw : raws) {
            lines.add(mm.deserialize(raw, resolver));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private TagResolver resolver(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attribute != null ? attribute.getValue() : 20.0;
        int total = (int) Math.max(1, Math.min(10, Math.ceil(maxHealth / 2.0)));
        int filled = (int) Math.max(0, Math.min(total, Math.ceil(player.getHealth() / 2.0)));
        String bar = "<red>" + "❤".repeat(filled) + "</red><dark_gray>" + "❤".repeat(total - filled) + "</dark_gray>";

        return TagResolver.resolver(
                Placeholder.unparsed("player", player.getName()),
                Placeholder.component("displayname", player.displayName()),
                Placeholder.unparsed("health", Integer.toString((int) Math.ceil(player.getHealth()))),
                Placeholder.unparsed("max_health", Integer.toString((int) Math.ceil(maxHealth))),
                Placeholder.parsed("health_bar", bar),
                Placeholder.unparsed("level", Integer.toString(player.getLevel())),
                Placeholder.unparsed("gamemode", player.getGameMode().name().toLowerCase(Locale.ROOT)),
                Placeholder.unparsed("world", player.getWorld().getName()),
                Placeholder.unparsed("ping", Integer.toString(player.getPing()))
        );
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public String debug(Player player) {
        NametagFormat format = pickFormat(player);
        if (format == null) {
            return "sin formato aplicable";
        }
        return "formato=" + format.id
                + " | lunar=" + isLunar(player)
                + " | verPropio=" + isSelfVisible(player)
                + " | " + String.join(" / ", buildRaw(player, format));
    }

    // ------------------------------------------------------------------ entidades

    private Material iconMaterial(NametagFormat f) {
        if (f.iconMaterial == null || f.iconMaterial.isEmpty()) {
            return null;
        }
        Material m = Material.matchMaterial(f.iconMaterial);
        return (m != null && m.isItem() && !m.isAir()) ? m : null;
    }

    private ItemStack iconStack(NametagFormat f) {
        Material m = iconMaterial(f);
        return m == null ? new ItemStack(Material.AIR) : new ItemStack(m);
    }

    private Tag spawn(Player player, NametagFormat f) {
        final float scaleV = (float) (f.scale != null ? f.scale : scale);
        final float yOff = (float) (f.yOffset != null ? f.yOffset : yOffset);
        final boolean shadowV = f.shadow != null ? f.shadow : shadow;
        final boolean seeThroughV = f.seeThrough != null ? f.seeThrough : seeThrough;
        final String bgV = f.background != null ? f.background : background;
        final byte opacityV = f.textOpacity != null
                ? (byte) Math.round(Math.max(10, Math.min(100, f.textOpacity)) * 2.55)
                : (byte) -1;

        TextDisplay.TextAlignment alignTmp = TextDisplay.TextAlignment.CENTER;
        if (f.alignment != null) {
            try {
                alignTmp = TextDisplay.TextAlignment.valueOf(f.alignment.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // se queda en CENTER
            }
        }
        final TextDisplay.TextAlignment alignV = alignTmp;
        final boolean animatedBg = f.bgArgb.length > 0;

        TextDisplay display = player.getWorld().spawn(spawnLocation(player), TextDisplay.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(ENTITY_TAG);
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(alignV);
            e.setLineWidth(lineWidth);
            e.setShadowed(shadowV);
            e.setSeeThrough(seeThroughV);
            e.setViewRange(viewRange);
            applyFollow(e);
            e.setTextOpacity(opacityV);
            e.setTransformation(new Transformation(
                    new Vector3f(0f, yOff, 0f),
                    new AxisAngle4f(),
                    new Vector3f(scaleV, scaleV, scaleV),
                    new AxisAngle4f()));
            applyBackground(e, bgV, animatedBg);
            e.text(Component.empty());
        });

        ItemDisplay icon = null;
        final Material material = iconMaterial(f);
        if (material != null) {
            final float iconScale = (float) f.iconScale;
            final float top = yOff + (float) (f.lines.size() * 0.25 * scaleV) + 0.1f + iconScale / 2f
                    + (float) f.iconYOffset;
            icon = player.getWorld().spawn(spawnLocation(player), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.addScoreboardTag(ENTITY_TAG);
                e.setBillboard(Display.Billboard.CENTER);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                e.setItemStack(new ItemStack(material));
                e.setViewRange(viewRange);
            applyFollow(e);
                e.setTransformation(new Transformation(
                        new Vector3f(0f, top, 0f),
                        new AxisAngle4f(),
                        new Vector3f(iconScale, iconScale, iconScale),
                        new AxisAngle4f()));
            });
        }

        if (!isSelfVisible(player)) {
            player.hideEntity(plugin, display);
            if (icon != null) {
                player.hideEntity(plugin, icon);
            }
        }
        Tag created = new Tag(display, icon, f.id, store.version(), seeThroughV, opacityV);
        displayOwners.put(created.displayId, player.getUniqueId());
        if (created.iconId != -1) {
            displayOwners.put(created.iconId, player.getUniqueId());
        }
        return created;
    }

    private void applyBackground(TextDisplay display, String bg, boolean animated) {
        if (animated) {
            display.setDefaultBackground(false);
            return;
        }
        if (bg.equalsIgnoreCase("default")) {
            display.setDefaultBackground(true);
            return;
        }
        display.setDefaultBackground(false);
        if (bg.equalsIgnoreCase("none")) {
            display.setBackgroundColor(ColorUtil.toColor(0));
            return;
        }
        Integer argb = ColorUtil.parseArgb(bg);
        if (argb == null) {
            display.setDefaultBackground(true);
        } else {
            display.setBackgroundColor(ColorUtil.toColor(argb));
        }
    }

    private void discard(Tag tag) {
        displayOwners.remove(tag.displayId);
        if (tag.iconId != -1) {
            displayOwners.remove(tag.iconId);
        }
        if (tag.display.isValid()) {
            tag.display.remove();
        }
        if (tag.icon != null && tag.icon.isValid()) {
            tag.icon.remove();
        }
    }

    public void remove(Player player) {
        Tag tag = tags.remove(player.getUniqueId());
        if (tag != null) {
            discard(tag);
        }
    }

    // ------------------------------------------------------------------ nametag propio / Lunar Client

    public boolean isSelfVisible(Player player) {
        Boolean override = selfOverride.get(player.getUniqueId());
        if (override != null) {
            return override;
        }
        if (lunarHideOwn && lunarUsers.contains(player.getUniqueId())) {
            return false;
        }
        return showOwn;
    }

    /** @return true si ahora el jugador ve su propio nametag. */
    public boolean toggleSelf(Player player) {
        boolean now = !isSelfVisible(player);
        selfOverride.put(player.getUniqueId(), now);
        saveData();
        remove(player);
        return now;
    }

    public void markLunar(Player player) {
        if (lunarUsers.add(player.getUniqueId()) && lunarHideOwn) {
            scheduleRefresh(player);
        }
    }

    public boolean isLunar(Player player) {
        return lunarUsers.contains(player.getUniqueId());
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    private void loadData() {
        selfOverride.clear();
        File file = dataFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("self");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                selfOverride.put(UUID.fromString(key), section.getBoolean(key));
            } catch (IllegalArgumentException ignored) {
                // clave invalida
            }
        }
    }

    private void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Boolean> entry : selfOverride.entrySet()) {
            yaml.set("self." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(dataFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }
}
