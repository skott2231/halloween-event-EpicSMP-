package com.epicsmp.halloween;

import com.epicsmp.halloween.DataManager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /halloween (alias /hw)
 *
 *   info [player]   your points, progress and rank
 *   sell            open the hand-in menu
 *   sellall         hand in every carved pumpkin from your inventory
 *   top             leaderboard
 *   shop            spend your points
 *   daily           daily bonus
 *   goal            community goal progress
 *   points          show your points
 *   admin ...       staff tools
 */
public final class HalloweenCommand implements CommandExecutor, TabCompleter {

    private final HalloweenPlugin plugin;

    public HalloweenCommand(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private HalloweenService service() {
        return plugin.service();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info", "stats" -> info(sender, args);
            case "sell" -> sell(sender);
            case "sellall" -> sellAll(sender);
            case "top", "leaderboard" -> top(sender);
            case "shop" -> shop(sender);
            case "daily" -> daily(sender);
            case "goal", "community" -> goal(sender);
            case "points", "balance" -> points(sender);
            case "admin" -> admin(sender, args);
            default -> msg().send(sender, "unknown-command", "label", label);
        }
        return true;
    }

    // ───────────────────────── help ─────────────────────────

    private void help(CommandSender sender) {
        msg().send(sender, "help-header");
        msg().send(sender, "help-info");
        if (sender.hasPermission("halloween.sell")) {
            msg().send(sender, "help-sell");
        }
        if (sender.hasPermission("halloween.sellall")) {
            msg().send(sender, "help-sellall");
        }
        msg().send(sender, "help-top");
        if (sender.hasPermission("halloween.shop")) {
            msg().send(sender, "help-shop");
        }
        if (sender.hasPermission("halloween.daily")) {
            msg().send(sender, "help-daily");
        }
        msg().send(sender, "help-goal");
        msg().send(sender, "help-points");
        if (sender.hasPermission("halloween.admin")) {
            msg().send(sender, "help-admin");
        }
    }

    // ───────────────────────── player commands ─────────────────────────

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        msg().send(sender, "no-permission");
        return false;
    }

    private void info(CommandSender sender, String[] args) {
        if (!allowed(sender, "halloween.use")) {
            return;
        }
        PlayerData d;
        if (args.length >= 2) {
            if (!allowed(sender, "halloween.info.others")) {
                return;
            }
            d = service().find(args[1]);
            if (d == null) {
                msg().send(sender, "player-not-found", "name", args[1]);
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                msg().send(sender, "players-only");
                return;
            }
            d = plugin.data().getOrCreate(player);
        }
        msg().sendList(sender, "info", service().stats(d));
    }

    private void sell(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "players-only");
            return;
        }
        if (!allowed(sender, "halloween.sell")) {
            return;
        }
        if (!service().isEventActive()) {
            msg().send(sender, "event-inactive");
            return;
        }
        plugin.sellGui().open(player);
    }

    private void sellAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "players-only");
            return;
        }
        if (!allowed(sender, "halloween.sellall")) {
            return;
        }
        if (!plugin.getConfig().getBoolean("sellall.enabled", true)) {
            msg().send(sender, "sellall-disabled");
            return;
        }
        if (!service().isEventActive()) {
            msg().send(sender, "event-inactive");
            return;
        }
        service().sellAll(player);
    }

    private void top(CommandSender sender) {
        if (!allowed(sender, "halloween.use")) {
            return;
        }
        List<PlayerData> list = service().sorted();
        int size = Math.max(1, plugin.getConfig().getInt("top.size", 10));
        msg().send(sender, "top-header", "size", size);
        if (list.isEmpty()) {
            msg().send(sender, "top-empty");
            return;
        }
        for (int i = 0; i < Math.min(size, list.size()); i++) {
            PlayerData d = list.get(i);
            msg().send(sender, "top-line", "pos", i + 1, "player", d.name,
                    "delivered", d.delivered, "points", d.points);
        }
    }

    private void shop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "players-only");
            return;
        }
        if (!allowed(sender, "halloween.shop")) {
            return;
        }
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) {
            msg().send(sender, "shop-disabled");
            return;
        }
        boolean openAfterEnd = plugin.getConfig().getBoolean("event.shop-open-after-end", true);
        if (!service().isEventActive() && !(service().hasEnded() && openAfterEnd)) {
            msg().send(sender, "event-inactive");
            return;
        }
        plugin.shopGui().open(player);
    }

    private void daily(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "players-only");
            return;
        }
        if (!allowed(sender, "halloween.daily")) {
            return;
        }
        if (!service().isEventActive()) {
            msg().send(sender, "event-inactive");
            return;
        }
        service().daily(player);
    }

    private void goal(CommandSender sender) {
        if (!allowed(sender, "halloween.use")) {
            return;
        }
        PlayerData d = sender instanceof Player player
                ? plugin.data().getOrCreate(player)
                : new PlayerData(new java.util.UUID(0L, 0L), "Console");
        msg().sendList(sender, "goal", service().stats(d));
    }

    private void points(CommandSender sender) {
        if (!allowed(sender, "halloween.use")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            msg().send(sender, "players-only");
            return;
        }
        PlayerData d = plugin.data().getOrCreate(player);
        msg().send(sender, "points", service().stats(d));
    }

    // ───────────────────────── admin ─────────────────────────

    private void admin(CommandSender sender, String[] args) {
        if (!allowed(sender, "halloween.admin")) {
            return;
        }
        if (args.length < 2) {
            msg().sendList(sender, "admin-usage");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadEverything();
                msg().send(sender, "admin-reloaded");
            }
            case "event" -> adminEvent(sender, args);
            case "give", "take", "set", "setdelivered" -> adminNumber(sender, sub, args);
            case "reset" -> adminReset(sender, args);
            case "synckeys" -> adminSyncKeys(sender, args);
            default -> msg().sendList(sender, "admin-usage");
        }
    }

    private void adminEvent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg().send(sender, "admin-event-status", concatStatus());
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                plugin.data().eventEnabled = true;
                plugin.data().save();
                msg().send(sender, "admin-event-on");
            }
            case "off" -> {
                plugin.data().eventEnabled = false;
                plugin.data().save();
                msg().send(sender, "admin-event-off");
            }
            default -> msg().send(sender, "admin-event-status", concatStatus());
        }
    }

    private Object[] concatStatus() {
        return new Object[]{
                "status", msg().get(service().isEventActive() ? "status-active" : "status-inactive"),
                "time_left", service().timeLeft()
        };
    }

    private void adminNumber(CommandSender sender, String sub, String[] args) {
        if (args.length < 4) {
            msg().sendList(sender, "admin-usage");
            return;
        }
        PlayerData d = service().find(args[2]);
        if (d == null) {
            msg().send(sender, "player-not-found", "name", args[2]);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException ex) {
            msg().send(sender, "invalid-number", "value", args[3]);
            return;
        }
        if (amount < 0) {
            msg().send(sender, "invalid-number", "value", args[3]);
            return;
        }
        switch (sub) {
            case "give" -> {
                d.points += amount;
                msg().send(sender, "admin-points-given", "player", d.name, "amount", amount, "points", d.points);
            }
            case "take" -> {
                d.points = Math.max(0L, d.points - amount);
                msg().send(sender, "admin-points-taken", "player", d.name, "amount", amount, "points", d.points);
            }
            case "set" -> {
                d.points = amount;
                msg().send(sender, "admin-points-set", "player", d.name, "amount", amount, "points", d.points);
            }
            default -> {
                d.delivered = amount;
                msg().send(sender, "admin-delivered-set", "player", d.name, "amount", amount);
            }
        }
        plugin.data().save();
    }

    private void adminReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg().sendList(sender, "admin-usage");
            return;
        }
        PlayerData d = service().find(args[2]);
        if (d == null) {
            msg().send(sender, "player-not-found", "name", args[2]);
            return;
        }
        d.points = 0L;
        d.delivered = 0L;
        d.keysGiven = 0;
        d.lastDaily = 0L;
        d.milestones.clear();
        d.purchases.clear();
        plugin.data().save();
        msg().send(sender, "admin-reset", "player", d.name);
    }

    private void adminSyncKeys(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg().sendList(sender, "admin-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg().send(sender, "player-not-found", "name", args[2]);
            return;
        }
        int given = service().syncKeys(target);
        msg().send(sender, "admin-synced", "player", target.getName(), "amount", given);
    }

    // ───────────────────────── tab completion ─────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            addIf(out, args[0], "info", sender.hasPermission("halloween.use"));
            addIf(out, args[0], "sell", sender.hasPermission("halloween.sell"));
            addIf(out, args[0], "sellall", sender.hasPermission("halloween.sellall"));
            addIf(out, args[0], "top", sender.hasPermission("halloween.use"));
            addIf(out, args[0], "shop", sender.hasPermission("halloween.shop"));
            addIf(out, args[0], "daily", sender.hasPermission("halloween.daily"));
            addIf(out, args[0], "goal", sender.hasPermission("halloween.use"));
            addIf(out, args[0], "points", sender.hasPermission("halloween.use"));
            addIf(out, args[0], "help", true);
            addIf(out, args[0], "admin", sender.hasPermission("halloween.admin"));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission("halloween.info.others")) {
            addPlayers(out, args[1]);
            return out;
        }
        if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("halloween.admin")) {
            if (args.length == 2) {
                for (String s : List.of("give", "take", "set", "setdelivered", "reset", "synckeys", "event", "reload")) {
                    addIf(out, args[1], s, true);
                }
            } else if (args.length == 3) {
                String sub = args[1].toLowerCase(Locale.ROOT);
                if (sub.equals("event")) {
                    addIf(out, args[2], "on", true);
                    addIf(out, args[2], "off", true);
                } else if (!sub.equals("reload")) {
                    addPlayers(out, args[2]);
                }
            }
        }
        return out;
    }

    private void addIf(List<String> out, String typed, String option, boolean allowed) {
        if (allowed && option.startsWith(typed.toLowerCase(Locale.ROOT))) {
            out.add(option);
        }
    }

    private void addPlayers(List<String> out, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(p.getName());
            }
        }
        for (String name : plugin.data().names()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(lower) && !out.contains(name)) {
                out.add(name);
            }
        }
    }
}
