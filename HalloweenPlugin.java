package com.epicsmp.halloween;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Main class. It only wires everything together:
 *
 *   Messages          reads messages.yml (all texts)
 *   DataManager       saves points and progress in data.yml
 *   HalloweenService  the rules: points, keys, milestones, goals, daily bonus
 *   SellGui           the "hand in pumpkins" menu
 *   ShopGui           the point shop
 *   HalloweenCommand  /halloween ...
 *   HalloweenExpansion  optional PlaceholderAPI placeholders
 */
public final class HalloweenPlugin extends JavaPlugin {

    private Messages messages;
    private DataManager data;
    private HalloweenService service;
    private SellGui sellGui;
    private ShopGui shopGui;

    @Override
    public void onEnable() {
        // Creates plugins/HalloweenEvent/config.yml and messages.yml on first start.
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        messages = new Messages(this);
        data = new DataManager(this);
        data.load();
        service = new HalloweenService(this);
        sellGui = new SellGui(this);
        shopGui = new ShopGui(this);

        getServer().getPluginManager().registerEvents(service, this);
        getServer().getPluginManager().registerEvents(sellGui, this);
        getServer().getPluginManager().registerEvents(shopGui, this);

        HalloweenCommand command = new HalloweenCommand(this);
        PluginCommand pluginCommand = getCommand("halloween");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // Optional: placeholders for scoreboards / TAB, only if PlaceholderAPI exists.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new HalloweenExpansion(this).register();
            getLogger().info("PlaceholderAPI found - the halloween placeholders are available.");
        }

        // Automatic save every few minutes (only writes if something changed).
        long ticks = Math.max(1L, getConfig().getLong("storage.autosave-minutes", 5L)) * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> data.saveIfDirty(), ticks, ticks);

        getLogger().info("HalloweenEvent enabled. Event active right now: " + service.isEventActive());
    }

    @Override
    public void onDisable() {
        // Close open menus so nobody loses the items they put in.
        for (Player player : getServer().getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof SellGui.Holder || holder instanceof ShopGui.Holder) {
                player.closeInventory();
            }
        }
        if (data != null) {
            data.save();
        }
    }

    /** Used by /halloween admin reload. */
    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        service.reload();
    }

    public Messages messages() { return messages; }
    public DataManager data() { return data; }
    public HalloweenService service() { return service; }
    public SellGui sellGui() { return sellGui; }
    public ShopGui shopGui() { return shopGui; }
}
