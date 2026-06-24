package dev.zerep.zeah.lang;

import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LangManager {

    private final JavaPlugin plugin;
    private FileConfiguration lang;
    private FileConfiguration fallback;

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String langCode) {
        // Load fallback (English) first
        InputStream enStream = plugin.getResource("lang/en.yml");
        if (enStream != null) {
            fallback = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));
        }

        // Save and load the configured lang file
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        File langFile = new File(langDir, langCode + ".yml");
        if (!langFile.exists()) {
            InputStream res = plugin.getResource("lang/" + langCode + ".yml");
            if (res != null) {
                plugin.saveResource("lang/" + langCode + ".yml", false);
            } else {
                // Fallback to en
                plugin.saveResource("lang/en.yml", false);
                langFile = new File(langDir, "en.yml");
            }
        }

        lang = YamlConfiguration.loadConfiguration(langFile);
    }

    public String get(String key) {
        String prefix = getRaw("prefix");
        String msg = getRaw(key);
        return ColorUtil.strip(prefix).isEmpty() ? ColorUtil.color(msg).toString() : colored(prefix + msg);
    }

    public String getNoPrefix(String key) {
        return colored(getRaw(key));
    }

    public String getRaw(String key) {
        String val = lang != null ? lang.getString(key) : null;
        if (val == null && fallback != null) val = fallback.getString(key);
        return val != null ? val : "&c[Missing: " + key + "]";
    }

    public List<String> getList(String key) {
        List<String> val = lang != null ? lang.getStringList(key) : null;
        if ((val == null || val.isEmpty()) && fallback != null) val = fallback.getStringList(key);
        return val != null ? val : List.of();
    }

    public String colored(String text) {
        return text.replace("&", "\u00a7");
    }

    /** Replace placeholders and colorize. */
    public String format(String key, Object... args) {
        String msg = getRaw("prefix") + getRaw(key);
        // args come in pairs: placeholder, value
        for (int i = 0; i + 1 < args.length; i += 2) {
            msg = msg.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return colored(msg);
    }

    public String formatNoPrefix(String key, Object... args) {
        String msg = getRaw(key);
        for (int i = 0; i + 1 < args.length; i += 2) {
            msg = msg.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return colored(msg);
    }
}
