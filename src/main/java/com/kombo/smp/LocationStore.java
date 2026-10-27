package com.kombo.smp;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/** Speichert Orte als YAML (für Homes und Warps). */
public final class LocationStore {

    private final KomboSMP plugin;
    private final File file;
    private final Map<String, Location> data = new TreeMap<>();

    public LocationStore(KomboSMP plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                Location loc = yml.getLocation(key);
                if (loc != null) data.put(key, loc);
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        data.forEach(yml::set);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte " + file.getName() + " nicht speichern", e);
        }
    }

    public Location get(String key) { return data.get(key); }

    public void set(String key, Location loc) { data.put(key, loc); }

    public boolean remove(String key) { return data.remove(key) != null; }

    public List<String> keys(String prefix) {
        return data.keySet().stream().filter(k -> k.startsWith(prefix)).toList();
    }
}
