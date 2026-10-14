package com.epicsmp.halloween;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores everything in plugins/HalloweenEvent/data.yml.
 * The whole file is kept in memory and written back on every important
 * change (hand-in, purchase, key) and every few minutes.
 */
public final class DataManager {

    /** Everything we remember about one player. */
    public static final class PlayerData {
        public final UUID uuid;
        public String name;
        /** Halloween points the player can spend. */
        public long points;
        /** Lifetime counter of handed in items. Only goes up. */
        public long delivered;
        /** How many keys were already given (so nobody gets a key twice). */
        public int keysGiven;
        /** When /halloween daily was last used (milliseconds). */
        public long lastDaily;
        /** Milestones that were already rewarded. */
        public final Set<Integer> milestones = new HashSet<>();
        /** Shop item id -> how often it was bought. */
        public final Map<String, Integer> purchases = new HashMap<>();

        public PlayerData(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private final HalloweenPlugin plugin;
    private final File file;
    // ConcurrentHashMap: TAB / PlaceholderAPI may read this from another thread.
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private boolean dirty;

    /** Pumpkins handed in by everybody together. */
    public long communityDelivered;
    /** Ids of community goals that were already reached. */
    public final Set<String> goalsReached = new HashSet<>();
    /** /halloween admin event on|off */
    public boolean eventEnabled = true;

    public DataManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        players.clear();
        goalsReached.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        communityDelivered = yaml.getLong("community.delivered", 0L);
        goalsReached.addAll(yaml.getStringList("community.goals-reached"));
        eventEnabled = yaml.getBoolean("event-enabled", true);

        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection p = section.getConfigurationSection(key);
            if (p == null) {
                continue;
            }
            PlayerData data = new PlayerData(id, p.getString("name", "?"));
            data.points = p.getLong("points", 0L);
            data.delivered = p.getLong("delivered", 0L);
            data.keysGiven = p.getInt("keys-given", 0);
            data.lastDaily = p.getLong("last-daily", 0L);
            data.milestones.addAll(p.getIntegerList("milestones"));
            ConfigurationSection bought = p.getConfigurationSection("purchases");
            if (bought != null) {
                for (String item : bought.getKeys(false)) {
                    data.purchases.put(item, bought.getInt(item, 0));
                }
            }
            players.put(id, data);
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("event-enabled", eventEnabled);
        yaml.set("community.delivered", communityDelivered);
        yaml.set("community.goals-reached", new ArrayList<>(goalsReached));
        for (PlayerData d : players.values()) {
            String base = "players." + d.uuid + ".";
            yaml.set(base + "name", d.name);
            yaml.set(base + "points", d.points);
            yaml.set(base + "delivered", d.delivered);
            yaml.set(base + "keys-given", d.keysGiven);
            yaml.set(base + "last-daily", d.lastDaily);
            yaml.set(base + "milestones", new ArrayList<>(d.milestones));
            for (Map.Entry<String, Integer> e : d.purchases.entrySet()) {
                yaml.set(base + "purchases." + e.getKey(), e.getValue());
            }
        }
        try {
            // Write to a temp file first so a crash can never leave a half-written data.yml.
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            File temp = new File(file.getPath() + ".tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /** The player's data or null if the player never took part. */
    public PlayerData get(UUID uuid) {
        return players.get(uuid);
    }

    /** The player's data, created on first use. Also refreshes the stored name. */
    public PlayerData getOrCreate(Player player) {
        PlayerData d = players.get(player.getUniqueId());
        if (d == null) {
            d = new PlayerData(player.getUniqueId(), player.getName());
            players.put(player.getUniqueId(), d);
        } else {
            d.name = player.getName();
        }
        dirty = true;
        return d;
    }

    public PlayerData findByName(String name) {
        for (PlayerData d : players.values()) {
            if (d.name.equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    public Collection<PlayerData> all() {
        return players.values();
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (PlayerData d : players.values()) {
            names.add(d.name);
        }
        return names;
    }
}
