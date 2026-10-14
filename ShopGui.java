package com.epicsmp.halloween;

import com.epicsmp.halloween.DataManager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The point shop (/halloween shop). Items are defined in config.yml
 * under "shop.items". Players cannot take anything out of the menu -
 * a click only buys the item.
 */
public final class ShopGui implements Listener {

    /** Marks an inventory as "our shop menu". */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        /** menu slot -> shop item id */
        private final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final HalloweenPlugin plugin;

    public ShopGui(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("shop.rows", 3)));
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                Messages.color(plugin.getConfig().getString("shop.title", "&6Halloween Shop")));
        holder.inventory = inventory;
        render(holder, player);
        player.openInventory(inventory);
        plugin.service().sound(player, "gui-open");
    }

    /** Fills (or refreshes) all icons. */
    private void render(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        Messages m = plugin.messages();
        PlayerData d = plugin.data().getOrCreate(player);

        inventory.clear();
        holder.slots.clear();

        String fillerName = plugin.getConfig().getString("shop.filler", "");
        if (fillerName != null && !fillerName.isBlank()) {
            Material filler = Material.matchMaterial(fillerName);
            if (filler != null) {
                ItemStack pane = GuiUtil.item(filler, m.get("gui-filler-name"), null, 1, false);
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    inventory.setItem(slot, pane);
                }
            }
        }

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                int slot = s.getInt("slot", -1);
                Material material = Material.matchMaterial(s.getString("material", "STONE"));
                if (slot < 0 || slot >= inventory.getSize() || material == null) {
                    plugin.getLogger().warning("shop.items." + id + ": invalid slot or material - skipped.");
                    continue;
                }
                int cost = s.getInt("cost", 0);
                int limit = s.getInt("limit", -1);
                int bought = d.purchases.getOrDefault(id, 0);

                List<String> lore = new ArrayList<>();
                for (String line : s.getStringList("lore")) {
                    lore.add(m.format(line));
                }
                lore.addAll(m.list("shop-item-footer",
                        "cost", cost,
                        "limit", limit < 0 ? m.get("unlimited") : String.valueOf(limit),
                        "bought", bought,
                        "points", d.points));

                inventory.setItem(slot, GuiUtil.item(material,
                        m.format(s.getString("name", id)), lore, s.getInt("amount", 1), s.getBoolean("glow", false)));
                holder.slots.put(slot, id);
            }
        }

        // Balance icon in the middle of the bottom row.
        int balanceSlot = inventory.getSize() - 5;
        inventory.setItem(balanceSlot, GuiUtil.item(Material.PUMPKIN,
                m.get("shop-balance-name", "points", d.points),
                m.list("shop-balance-lore", "points", d.points), 1, false));
        holder.slots.remove(balanceSlot);
    }

    private void buy(Player player, Holder holder, String id) {
        Messages m = plugin.messages();
        HalloweenService service = plugin.service();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("shop.items." + id);
        if (s == null) {
            return;
        }
        PlayerData d = plugin.data().getOrCreate(player);
        int cost = s.getInt("cost", 0);
        int limit = s.getInt("limit", -1);
        int bought = d.purchases.getOrDefault(id, 0);
        String name = m.format(s.getString("name", id));

        if (limit >= 0 && bought >= limit) {
            m.send(player, "shop-limit", "item", name, "limit", limit);
            service.sound(player, "error");
            return;
        }
        if (d.points < cost) {
            m.send(player, "shop-not-enough", "item", name, "cost", cost, "points", d.points);
            service.sound(player, "error");
            return;
        }

        d.points -= cost;
        d.purchases.merge(id, 1, Integer::sum);
        service.runCommands(player, s.getStringList("commands"));
        service.sound(player, "shop");
        m.send(player, "shop-bought", "item", name, "cost", cost, "points", d.points);

        String broadcast = s.getString("broadcast", "");
        if (broadcast != null && !broadcast.isEmpty()) {
            Bukkit.broadcastMessage(m.format(broadcast, "player", player.getName(), "item", name));
        }
        plugin.data().save();
        render(holder, player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        // Nothing can be taken out of or put into the shop.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) {
            return;
        }
        String id = holder.slots.get(raw);
        if (id != null) {
            buy(player, holder, id);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
