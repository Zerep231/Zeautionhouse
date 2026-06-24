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
        InputStream enStream = plugin.getResource("lang/en.yml");
        if (enStream != null) {
            fallback = YamlConfiguration.loadConfiguration(
                new InputStreamReader(enStream, StandardCharsets.UTF_8));
        }

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        File langFile = new File(langDir, langCode + ".yml");
        if (!langFile.exists()) {
            InputStream res = plugin.getResource("lang/" + langCode + ".yml");
            if (res != null) {
                plugin.saveResource("lang/" + langCode + ".yml", false);
            } else {
                plugin.saveResource("lang/en.yml", false);
                langFile = new File(langDir, "en.yml");
            }
        }
        lang = YamlConfiguration.loadConfiguration(langFile);
    }

    /** Format with prefix, using consistent legacy color codes throughout. */
    public String get(String key) {
        return colored(getRaw("prefix") + getRaw(key));
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

    /** Replace placeholders and colorize with prefix. */
    public String format(String key, Object... args) {
        String msg = getRaw("prefix") + getRaw(key);
        for (int i = 0; i + 1 < args.length; i += 2)
            msg = msg.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        return colored(msg);
    }

    public String formatNoPrefix(String key, Object... args) {
        String msg = getRaw(key);
        for (int i = 0; i + 1 < args.length; i += 2)
            msg = msg.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        return colored(msg);
    }

    /** Legacy color code translation: & → § */
    public String colored(String text) {
        return text.replace("&", "\u00a7");
    }
}
