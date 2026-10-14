package com.epicsmp.halloween;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "hand in pumpkins" menu (/halloween sell).
 *
 *   slots  0-44   free space: the player puts the pumpkins here
 *   slot   48     CONFIRM button
 *   slot   49     live info (how many pumpkins, how many points)
 *   slot   50     CANCEL button
 *
 * Nothing is taken until CONFIRM is clicked. Closing the menu any other
 * way gives every item back.
 */
public final class SellGui implements Listener {

    private static final int SIZE = 54;
    private static final int DROP_SLOTS = 45;
    private static final int CONFIRM_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int CANCEL_SLOT = 50;

    /** Marks an inventory as "our sell menu". */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final UUID owner;
        private boolean finished;

        Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final HalloweenPlugin plugin;

    public SellGui(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Messages m = plugin.messages();
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, m.get("gui-sell-title"));
        holder.inventory = inventory;

        ItemStack filler = GuiUtil.item(Material.GRAY_STAINED_GLASS_PANE, m.get("gui-filler-name"), null, 1, false);
        for (int slot = DROP_SLOTS; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(CONFIRM_SLOT, GuiUtil.item(Material.LIME_STAINED_GLASS_PANE,
                m.get("gui-confirm-name"), m.list("gui-confirm-lore"), 1, false));
        inventory.setItem(CANCEL_SLOT, GuiUtil.item(Material.RED_STAINED_GLASS_PANE,
                m.get("gui-cancel-name"), m.list("gui-cancel-lore"), 1, false));
        refresh(holder);

        player.openInventory(inventory);
        plugin.service().sound(player, "gui-open");
    }

    // ───────────────────────── live info ─────────────────────────

    private void refresh(Holder holder) {
        HalloweenService service = plugin.service();
        int items = 0;
        int points = 0;
        for (int slot = 0; slot < DROP_SLOTS; slot++) {
            ItemStack stack = holder.inventory.getItem(slot);
            int value = service.valueOf(stack);
            if (value > 0) {
                items += stack.getAmount();
                points += value * stack.getAmount();
            }
        }
        DataManager.PlayerData d = plugin.data().get(holder.owner);
        long delivered = d == null ? 0L : d.delivered;
        long after = delivered + items;
        int newKeys = Math.max(0, service.keysEarned(after) - service.keysEarned(delivered));

        Messages m = plugin.messages();
        holder.inventory.setItem(INFO_SLOT, GuiUtil.item(Material.PAPER,
                m.get("gui-info-name"),
                m.list("gui-info-lore", "items", items, "points", points, "keys", newKeys,
                        "total", after, "every", service.cycleSize()),
                1, false));
    }

    private void refreshLater(Holder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> refresh(holder));
    }

    // ───────────────────────── confirm ─────────────────────────

    private void confirm(Player player, Holder holder) {
        if (holder.finished) {
            return;
        }
        HalloweenService service = plugin.service();
        Messages m = plugin.messages();

        if (!service.isEventActive()) {
            m.send(player, "event-inactive");
            service.sound(player, "error");
            return;
        }

        int items = 0;
        int points = 0;
        for (int slot = 0; slot < DROP_SLOTS; slot++) {
            ItemStack stack = holder.inventory.getItem(slot);
            int value = service.valueOf(stack);
            if (value > 0) {
                items += stack.getAmount();
                points += value * stack.getAmount();
                holder.inventory.setItem(slot, null);
            }
        }
        if (items == 0) {
            m.send(player, "sell-nothing");
            service.sound(player, "error");
            return;
        }

        holder.finished = true;
        if (giveBack(player, holder.inventory)) {
            m.send(player, "sell-returned");
        }
        service.deliver(player, items, points);
        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
    }

    /** Puts every item from the free space back into the player's inventory. */
    private boolean giveBack(Player player, Inventory inventory) {
        boolean any = false;
        for (int slot = 0; slot < DROP_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            any = true;
        }
        return any;
    }

    // ───────────────────────── events ─────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // The menu is closing (after CONFIRM): no more changes allowed.
        if (holder.finished) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        // The bottom row is only buttons - nobody may take or move them.
        if (inTop && raw >= DROP_SLOTS) {
            event.setCancelled(true);
            if (raw == CONFIRM_SLOT) {
                confirm(player, holder);
            } else if (raw == CANCEL_SLOT) {
                Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            }
            return;
        }
        refreshLater(holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        if (holder.finished) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= DROP_SLOTS && raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
        refreshLater(holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // After CONFIRM the free space is already empty, so this only returns
        // items that are still inside (cancel, ESC, disconnect, server stop).
        holder.finished = true;
        if (giveBack(player, holder.inventory)) {
            plugin.messages().send(player, "sell-returned");
        }
    }
}
