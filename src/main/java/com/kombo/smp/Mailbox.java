package com.kombo.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Postfach: Items, die nicht ins Inventar passen oder aus Käufen, Orders und Crates stammen. */
public final class Mailbox {

    private final KomboSMP plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> items = new HashMap<>();

    public Mailbox(KomboSMP plugin) {
        this.plugin = plugin;
        this.file = plugin.dataFile("mailbox.yml");
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                try {
                    List<ItemStack> list = new ArrayList<>();
                    for (Object o : yml.getList(key, List.of())) {
                        if (o instanceof ItemStack st) list.add(st);
                    }
                    items.put(UUID.fromString(key), list);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ungültige UUID in mailbox.yml: " + key);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        items.forEach((id, list) -> {
            if (!list.isEmpty()) yml.set(id.toString(), list);
        });
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte mailbox.yml nicht speichern", e);
        }
    }

    public void deliver(UUID id, ItemStack... stacks) {
        List<ItemStack> list = items.computeIfAbsent(id, k -> new ArrayList<>());
        for (ItemStack s : stacks) {
            if (s != null && !s.getType().isAir()) list.add(s.clone());
        }
    }

    /** Legt das Item ins Inventar. Was nicht passt, landet im Postfach. */
    public void giveOrMail(Player p, ItemStack stack) {
        p.getInventory().addItem(stack.clone()).values().forEach(leftover -> deliver(p.getUniqueId(), leftover));
    }

    public int size(UUID id) {
        return items.getOrDefault(id, List.of()).size();
    }

    public void collect(Player p) {
        List<ItemStack> list = items.remove(p.getUniqueId());
        if (list == null || list.isEmpty()) {
            Ui.send(p, "<gray>Dein Postfach ist leer.");
            return;
        }
        for (ItemStack s : list) {
            giveOrMail(p, s);
        }
        int remaining = size(p.getUniqueId());
        Ui.send(p, remaining == 0
                ? "<green>Postfach geleert."
                : "<yellow>" + remaining + " Item(s) passen nicht ins Inventar und bleiben im Postfach.");
    }
}
