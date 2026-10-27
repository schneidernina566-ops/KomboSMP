package com.kombo.smp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Kern-Währung (Shards). Immer aktiv, unabhängig von Modulen. */
public final class Economy {

    private final KomboSMP plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    public Economy(KomboSMP plugin) {
        this.plugin = plugin;
        this.file = plugin.dataFile("shards.yml");
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                try {
                    balances.put(UUID.fromString(key), yml.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ungültige UUID in shards.yml: " + key);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        balances.forEach((id, amount) -> yml.set(id.toString(), amount));
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte shards.yml nicht speichern", e);
        }
    }

    public long balance(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public void add(UUID id, long amount) {
        if (amount <= 0) return;
        balances.merge(id, amount, Long::sum);
    }

    /** false, wenn das Guthaben nicht reicht. */
    public boolean remove(UUID id, long amount) {
        if (amount <= 0 || balance(id) < amount) return false;
        balances.merge(id, -amount, Long::sum);
        return true;
    }

    public void set(UUID id, long amount) {
        balances.put(id, Math.max(0, amount));
    }

    public List<Map.Entry<UUID, Long>> top(int n) {
        return balances.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }
}
