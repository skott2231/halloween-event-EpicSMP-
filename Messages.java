package com.epicsmp.halloween;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads messages.yml and turns texts into colored chat messages.
 *
 * Placeholders look like {name}. They are filled from pairs:
 *   get("key", "name", value, "other", value2)
 * {prefix} is always replaced by the "prefix" line of messages.yml.
 */
public final class Messages {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final HalloweenPlugin plugin;
    private YamlConfiguration yaml;

    public Messages(HalloweenPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        yaml = YamlConfiguration.loadConfiguration(file);

        // Missing lines (for example after an update) fall back to the built-in file.
        InputStream builtIn = plugin.getResource("messages.yml");
        if (builtIn != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(builtIn, StandardCharsets.UTF_8)));
        }
    }

    /** Turns &a and &#RRGGBB into real colors. */
    public static String color(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    /** Fills {placeholders} and colors a raw text (also used for texts from config.yml). */
    public String format(String raw, Object... pairs) {
        String out = raw.replace("{prefix}", yaml.getString("prefix", ""));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return color(out);
    }

    public String get(String key, Object... pairs) {
        String raw = yaml.getString(key);
        if (raw == null) {
            return color("&cMissing message: " + key);
        }
        return format(raw, pairs);
    }

    public List<String> list(String key, Object... pairs) {
        List<String> lines = new ArrayList<>();
        for (String raw : yaml.getStringList(key)) {
            lines.add(format(raw, pairs));
        }
        return lines;
    }

    public void send(CommandSender to, String key, Object... pairs) {
        String message = get(key, pairs);
        if (!message.isEmpty()) {
            to.sendMessage(message);
        }
    }

    public void sendList(CommandSender to, String key, Object... pairs) {
        for (String line : list(key, pairs)) {
            to.sendMessage(line);
        }
    }

    public void broadcast(String key, Object... pairs) {
        String message = get(key, pairs);
        if (!message.isEmpty()) {
            Bukkit.broadcastMessage(message);
        }
    }
}
