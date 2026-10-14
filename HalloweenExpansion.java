package com.epicsmp.halloween;

import com.epicsmp.halloween.DataManager.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * Optional PlaceholderAPI placeholders, for example for TAB scoreboards:
 *
 *   %halloween_points%          spendable Halloween points
 *   %halloween_delivered%       pumpkins handed in (lifetime)
 *   %halloween_keys%            keys earned
 *   %halloween_next_key%        pumpkins missing for the next key
 *   %halloween_progress%        progress in the current cycle, e.g. 120
 *   %halloween_progress_max%    pumpkins per key, e.g. 250
 *   %halloween_rank%            leaderboard position
 *   %halloween_global%          pumpkins handed in by the whole server
 *   %halloween_time_left%       time until the event ends
 *   %halloween_active%          true / false
 *   %halloween_top_1_name%      name of place 1 (any number)
 *   %halloween_top_1_value%     pumpkins of place 1 (any number)
 */
public final class HalloweenExpansion extends PlaceholderExpansion {

    private final HalloweenPlugin plugin;

    public HalloweenExpansion(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "halloween";
    }

    @Override
    public String getAuthor() {
        return "EPIC SMP";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        HalloweenService service = plugin.service();
        String key = params.toLowerCase(Locale.ROOT);

        // placeholders that do not need a player
        if (key.equals("global")) {
            return String.valueOf(plugin.data().communityDelivered);
        }
        if (key.equals("time_left")) {
            return service.timeLeft();
        }
        if (key.equals("active")) {
            return String.valueOf(service.isEventActive());
        }
        if (key.equals("progress_max")) {
            return String.valueOf(service.cycleSize());
        }
        if (key.startsWith("top_")) {
            return top(key);
        }

        if (player == null) {
            return "";
        }
        PlayerData d = plugin.data().get(player.getUniqueId());
        long delivered = d == null ? 0L : d.delivered;
        switch (key) {
            case "points":
                return String.valueOf(d == null ? 0L : d.points);
            case "delivered":
                return String.valueOf(delivered);
            case "keys":
                return String.valueOf(d == null ? 0 : d.keysGiven);
            case "next_key": {
                long next = service.nextKeyIn(delivered);
                return next < 0 ? plugin.messages().get("none") : String.valueOf(next);
            }
            case "progress":
                return String.valueOf(service.progressInCycle(delivered));
            case "rank": {
                int rank = d == null ? -1 : service.rankOf(d);
                return rank < 0 ? plugin.messages().get("none") : String.valueOf(rank);
            }
            default:
                return null;
        }
    }

    /** top_1_name / top_1_value */
    private String top(String key) {
        String[] parts = key.split("_");
        if (parts.length != 3) {
            return null;
        }
        int place;
        try {
            place = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        List<PlayerData> list = plugin.service().sorted();
        if (place < 1 || place > list.size()) {
            return parts[2].equals("name") ? plugin.messages().get("none") : "0";
        }
        PlayerData d = list.get(place - 1);
        if (parts[2].equals("name")) {
            return d.name;
        }
        if (parts[2].equals("value")) {
            return String.valueOf(d.delivered);
        }
        return null;
    }
}
