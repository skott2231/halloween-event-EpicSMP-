package com.epicsmp.halloween;

import com.epicsmp.halloween.DataManager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of the event. Everything that gives points, keys, milestone
 * rewards, community goals or the daily bonus lives here.
 */
public final class HalloweenService implements Listener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FIREWORK_TAG = "halloween_firework";

    private final HalloweenPlugin plugin;
    private final Map<Material, Integer> accepted = new EnumMap<>(Material.class);
    private boolean ignoreCustomItems = true;
    private ZoneId zone = ZoneId.of("UTC");
    private ZonedDateTime start;
    private ZonedDateTime end;

    private volatile List<PlayerData> sortedCache = new ArrayList<>();
    private volatile long sortedAt;

    public HalloweenService(HalloweenPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private DataManager data() {
        return plugin.data();
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ───────────────────────── loading ─────────────────────────

    public void reload() {
        FileConfiguration c = cfg();

        try {
            zone = ZoneId.of(c.getString("event.timezone", "UTC"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Unknown event.timezone in config.yml - using UTC.");
            zone = ZoneId.of("UTC");
        }
        start = parseDate(c.getString("event.start", ""));
        end = parseDate(c.getString("event.end", ""));

        accepted.clear();
        ConfigurationSection section = c.getConfigurationSection("pumpkins.accepted");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("pumpkins.accepted: unknown item '" + key + "' - skipped.");
                    continue;
                }
                accepted.put(material, Math.max(0, section.getInt(key, 1)));
            }
        }
        ignoreCustomItems = c.getBoolean("pumpkins.ignore-custom-items", true);
        sortedAt = 0L;
    }

    private ZonedDateTime parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_FORMAT).atZone(zone);
        } catch (DateTimeParseException ex) {
            plugin.getLogger().warning("Invalid date '" + text + "' in config.yml. Use yyyy-MM-dd HH:mm");
            return null;
        }
    }

    // ───────────────────────── event status ─────────────────────────

    /** True while pumpkins can be handed in. */
    public boolean isEventActive() {
        if (!data().eventEnabled) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        if (start != null && now.isBefore(start)) {
            return false;
        }
        return end == null || !now.isAfter(end);
    }

    public boolean hasEnded() {
        return end != null && ZonedDateTime.now(zone).isAfter(end);
    }

    public String timeLeft() {
        if (end == null) {
            return msg().get("no-end");
        }
        long millis = Duration.between(ZonedDateTime.now(zone), end).toMillis();
        if (millis <= 0) {
            return msg().get("ended");
        }
        return formatDuration(millis);
    }

    public String formatDuration(long millis) {
        long minutes = Math.max(0L, millis / 60000L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        return msg().get("time-format", "d", days, "h", hours, "m", mins);
    }

    // ───────────────────────── items ─────────────────────────

    /** Points for ONE of these items, or 0 if the item is not accepted. */
    public int valueOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        Integer value = accepted.get(item.getType());
        if (value == null) {
            return 0;
        }
        if (ignoreCustomItems && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && (meta.hasDisplayName() || meta.hasLore())) {
                return 0;
            }
        }
        return value;
    }

    /** /halloween sellall: takes every accepted item out of the player's inventory. */
    public void sellAll(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int items = 0;
        int points = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            int value = valueOf(stack);
            if (value > 0) {
                items += stack.getAmount();
                points += value * stack.getAmount();
                contents[i] = null;
            }
        }
        if (items == 0) {
            msg().send(player, "sellall-nothing");
            sound(player, "error");
            return;
        }
        player.getInventory().setStorageContents(contents);
        deliver(player, items, points);
    }

    // ───────────────────────── hand in ─────────────────────────

    /**
     * Called after the items were taken. Adds points, checks keys,
     * milestones and community goals, and saves.
     */
    public void deliver(Player player, int items, int points) {
        PlayerData d = data().getOrCreate(player);
        d.points += points;
        d.delivered += items;
        data().communityDelivered += items;
        sortedAt = 0L;

        int newKeys = giveKeys(player, d);

        sound(player, "sell");
        msg().sendList(player, "delivered", concat(stats(d), "amount", items, "gained", points));
        if (newKeys > 0) {
            announceKeys(player, d, newKeys);
        }
        checkMilestones(player, d);
        checkGoals();

        data().save();
    }

    // ───────────────────────── keys ─────────────────────────

    public long cycleSize() {
        return Math.max(1, cfg().getInt("key-reward.every", 250));
    }

    /** How many keys a player has earned after handing in this many items. */
    public int keysEarned(long delivered) {
        if (!cfg().getBoolean("key-reward.enabled", true)) {
            return 0;
        }
        long every = cycleSize();
        long earned = cfg().getBoolean("key-reward.repeat", true)
                ? delivered / every
                : (delivered >= every ? 1L : 0L);
        int max = cfg().getInt("key-reward.max-keys", -1);
        if (max >= 0) {
            earned = Math.min(earned, max);
        }
        return (int) earned;
    }

    /** Progress inside the current 250-pumpkin cycle. */
    public long progressInCycle(long delivered) {
        long every = cycleSize();
        if (cfg().getBoolean("key-reward.repeat", true)) {
            return delivered % every;
        }
        return Math.min(delivered, every);
    }

    /** Pumpkins missing for the next key, or -1 if there is no next key. */
    public long nextKeyIn(long delivered) {
        if (!cfg().getBoolean("key-reward.enabled", true)) {
            return -1L;
        }
        int max = cfg().getInt("key-reward.max-keys", -1);
        if (max >= 0 && keysEarned(delivered) >= max) {
            return -1L;
        }
        long every = cycleSize();
        if (cfg().getBoolean("key-reward.repeat", true)) {
            return every - (delivered % every);
        }
        return delivered >= every ? -1L : every - delivered;
    }

    /**
     * Gives every key the player has earned but not received yet.
     * Returns how many keys were given.
     */
    public int giveKeys(Player player, PlayerData d) {
        int due = keysEarned(d.delivered) - d.keysGiven;
        if (due <= 0) {
            return 0;
        }
        List<String> commands = cfg().getStringList("key-reward.commands");
        if (commands.isEmpty()) {
            plugin.getLogger().warning("key-reward.commands is empty - no key can be given.");
            return 0;
        }
        int given = 0;
        for (int i = 0; i < due; i++) {
            if (!runCommands(player, commands)) {
                plugin.getLogger().severe("The key command failed for " + player.getName()
                        + ". It will be tried again at the next hand-in or with /halloween admin synckeys.");
                break;
            }
            d.keysGiven++;
            given++;
        }
        return given;
    }

    private void announceKeys(Player player, PlayerData d, int amount) {
        sound(player, "key");
        msg().send(player, "key-received", concat(stats(d), "amount", amount));
        if (cfg().getBoolean("key-reward.broadcast", true)) {
            msg().broadcast("key-broadcast", concat(stats(d), "amount", amount));
        }
        if (cfg().getBoolean("effects.title-on-key", true)) {
            player.sendTitle(msg().get("key-title", "amount", amount),
                    msg().get("key-subtitle", "amount", amount), 10, 70, 20);
        }
        if (cfg().getBoolean("effects.firework-on-key", true)) {
            firework(player);
        }
    }

    private void firework(Player player) {
        Location location = player.getLocation().add(0, 1, 0);
        if (location.getWorld() == null) {
            return;
        }
        Firework firework = location.getWorld().spawn(location, Firework.class);
        firework.setMetadata(FIREWORK_TAG, new FixedMetadataValue(plugin, true));
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.PURPLE)
                .withFade(Color.BLACK)
                .flicker(true)
                .build());
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 2L);
    }

    /** Our celebration fireworks never hurt anybody. */
    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && firework.hasMetadata(FIREWORK_TAG)) {
            event.setCancelled(true);
        }
    }

    /** /halloween admin synckeys: gives keys that are due but were never delivered. */
    public int syncKeys(Player player) {
        PlayerData d = data().getOrCreate(player);
        int given = giveKeys(player, d);
        if (given > 0) {
            announceKeys(player, d, given);
        }
        data().save();
        return given;
    }

    // ───────────────────────── milestones ─────────────────────────

    private void checkMilestones(Player player, PlayerData d) {
        if (!cfg().getBoolean("milestones.enabled", true)) {
            return;
        }
        ConfigurationSection list = cfg().getConfigurationSection("milestones.list");
        if (list == null) {
            return;
        }
        for (String key : list.getKeys(false)) {
            int needed;
            try {
                needed = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (d.delivered < needed || !d.milestones.add(needed)) {
                continue;
            }
            ConfigurationSection milestone = list.getConfigurationSection(key);
            if (milestone == null) {
                continue;
            }
            runCommands(player, milestone.getStringList("commands"));
            sound(player, "milestone");
            String message = milestone.getString("message", "");
            if (!message.isEmpty()) {
                player.sendMessage(msg().format(message, concat(stats(d), "amount", needed)));
            }
            String broadcast = milestone.getString("broadcast", "");
            if (!broadcast.isEmpty()) {
                Bukkit.broadcastMessage(msg().format(broadcast, concat(stats(d), "amount", needed)));
            }
        }
    }

    // ───────────────────────── community goals ─────────────────────────

    private void checkGoals() {
        if (!cfg().getBoolean("community-goal.enabled", true)) {
            return;
        }
        ConfigurationSection goals = cfg().getConfigurationSection("community-goal.goals");
        if (goals == null) {
            return;
        }
        for (String id : goals.getKeys(false)) {
            ConfigurationSection goal = goals.getConfigurationSection(id);
            if (goal == null) {
                continue;
            }
            long target = goal.getLong("target", 0L);
            if (target <= 0 || data().communityDelivered < target || data().goalsReached.contains(id)) {
                continue;
            }
            data().goalsReached.add(id);
            String broadcast = goal.getString("broadcast", "");
            if (!broadcast.isEmpty()) {
                Bukkit.broadcastMessage(msg().format(broadcast, "target", target));
            }
            List<String> commands = goal.getStringList("commands");
            for (Player online : Bukkit.getOnlinePlayers()) {
                runCommands(online, commands);
            }
        }
    }

    /** Smallest community goal that is not reached yet, or -1. */
    public long nextGoalTarget() {
        if (!cfg().getBoolean("community-goal.enabled", true)) {
            return -1L;
        }
        ConfigurationSection goals = cfg().getConfigurationSection("community-goal.goals");
        if (goals == null) {
            return -1L;
        }
        long best = -1L;
        for (String id : goals.getKeys(false)) {
            long target = goals.getLong(id + ".target", 0L);
            if (target > data().communityDelivered && !data().goalsReached.contains(id)
                    && (best < 0 || target < best)) {
                best = target;
            }
        }
        return best;
    }

    // ───────────────────────── daily bonus ─────────────────────────

    public void daily(Player player) {
        if (!cfg().getBoolean("daily.enabled", true)) {
            msg().send(player, "daily-disabled");
            return;
        }
        PlayerData d = data().getOrCreate(player);
        long cooldown = Math.max(0L, cfg().getLong("daily.cooldown-hours", 20L)) * 3_600_000L;
        long now = System.currentTimeMillis();
        long left = d.lastDaily + cooldown - now;
        if (left > 0) {
            sound(player, "error");
            msg().send(player, "daily-wait", "time", formatDuration(left));
            return;
        }
        long points = cfg().getLong("daily.points", 5L);
        d.lastDaily = now;
        d.points += points;
        runCommands(player, cfg().getStringList("daily.commands"));
        sound(player, "shop");
        msg().send(player, "daily-claimed", concat(stats(d), "gained", points));
        data().save();
    }

    // ───────────────────────── leaderboard ─────────────────────────

    /** Everybody who handed in at least one item, best first. Cached for 5 seconds. */
    public synchronized List<PlayerData> sorted() {
        long now = System.currentTimeMillis();
        if (now - sortedAt > 5000L) {
            List<PlayerData> list = new ArrayList<>(data().all());
            list.removeIf(p -> p.delivered <= 0);
            list.sort(Comparator.comparingLong((PlayerData p) -> p.delivered).reversed());
            sortedCache = list;
            sortedAt = now;
        }
        return sortedCache;
    }

    public int rankOf(PlayerData d) {
        List<PlayerData> list = sorted();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(d.uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public PlayerData find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return data().getOrCreate(online);
        }
        return data().findByName(name);
    }

    // ───────────────────────── text helpers ─────────────────────────

    /** All placeholders for a player: {points}, {delivered}, {bar}, {rank}, ... */
    public Object[] stats(PlayerData d) {
        long every = cycleSize();
        long progress = progressInCycle(d.delivered);
        long next = nextKeyIn(d.delivered);
        long goal = nextGoalTarget();
        int rank = rankOf(d);
        String none = msg().get("none");
        return new Object[]{
                "player", d.name,
                "points", d.points,
                "delivered", d.delivered,
                "keys", d.keysGiven,
                "every", every,
                "progress", progress,
                "next_key", next < 0 ? none : String.valueOf(next),
                "bar", bar(progress, every),
                "rank", rank < 0 ? none : String.valueOf(rank),
                "status", msg().get(isEventActive() ? "status-active" : "status-inactive"),
                "time_left", timeLeft(),
                "global", data().communityDelivered,
                "goal_next", goal < 0 ? none : String.valueOf(goal),
                "goal_bar", bar(Math.min(data().communityDelivered, goal < 0 ? 1 : goal), goal < 0 ? 1 : goal)
        };
    }

    public String bar(long value, long max) {
        int length = Math.max(5, cfg().getInt("progress-bar.length", 20));
        int filled = max <= 0 ? 0 : (int) Math.min(length, (value * length) / max);
        String on = msg().get("bar-filled");
        String off = msg().get("bar-empty");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append(i < filled ? on : off);
        }
        return out.toString();
    }

    public static Object[] concat(Object[] first, Object... more) {
        Object[] out = Arrays.copyOf(first, first.length + more.length);
        System.arraycopy(more, 0, out, first.length, more.length);
        return out;
    }

    // ───────────────────────── commands & sounds ─────────────────────────

    /** Runs console commands. %player% is replaced. Returns false if one failed. */
    public boolean runCommands(Player player, List<String> commands) {
        boolean ok = true;
        for (String raw : commands) {
            String command = raw.trim();
            if (command.isEmpty()) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            command = command.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    ok = false;
                    plugin.getLogger().warning("Command was not accepted: /" + command);
                }
            } catch (Exception ex) {
                ok = false;
                plugin.getLogger().severe("Error in command '/" + command + "': " + ex.getMessage());
            }
        }
        return ok;
    }

    public void sound(Player player, String name) {
        String sound = cfg().getString("effects.sounds." + name, "");
        if (sound != null && !sound.isBlank()) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    // ───────────────────────── join reminder ─────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData known = data().get(player.getUniqueId());
        if (known != null) {
            known.name = player.getName();
        }
        if (!cfg().getBoolean("join-reminder.enabled", true) || !isEventActive()) {
            return;
        }
        long delay = Math.max(0L, cfg().getLong("join-reminder.delay-seconds", 5L)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerData d = data().get(player.getUniqueId());
            if (d == null) {
                d = new PlayerData(player.getUniqueId(), player.getName());
            }
            msg().sendList(player, "join-reminder", stats(d));
        }, delay);
    }
}
