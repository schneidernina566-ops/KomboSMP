package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Modul "earning": Shards für Mob-Kills, abgebaute Erze und passives Einkommen. */
public final class EarningModule extends SmpModule {

    private final Map<EntityType, Long> mobRewards = new EnumMap<>(EntityType.class);
    private final Map<Material, Long> blockRewards = new EnumMap<>(Material.class);
    /** Von Spielern platzierte Erze bringen keine Shards (Anti-Exploit). */
    private final Set<Location> placedBlocks = new HashSet<>();
    private BukkitTask playtimeTask;

    public EarningModule(KomboSMP plugin) {
        super(plugin, "earning");
    }

    @Override
    protected void onEnable() {
        mobRewards.clear();
        blockRewards.clear();
        placedBlocks.clear();

        ConfigurationSection mobs = config().getConfigurationSection("mobs");
        if (mobs != null) {
            for (String key : mobs.getKeys(false)) {
                try {
                    mobRewards.put(EntityType.valueOf(key.toUpperCase(Locale.ROOT)), mobs.getLong(key));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unbekannter Mob in modules/earning.yml: " + key);
                }
            }
        }

        ConfigurationSection blocks = config().getConfigurationSection("blocks");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("Unbekanntes Material in modules/earning.yml: " + key);
                    continue;
                }
                blockRewards.put(mat, blocks.getLong(key));
            }
        }

        long amount = config().getLong("playtime.amount", 0);
        long minutes = Math.max(1, config().getLong("playtime.interval-minutes", 10));
        if (amount > 0) {
            long ticks = minutes * 60L * 20L;
            playtimeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.economy().add(p.getUniqueId(), amount);
                }
            }, ticks, ticks);
        }
    }

    @Override
    protected void onDisable() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
            playtimeTask = null;
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        Long reward = mobRewards.get(e.getEntityType());
        if (reward != null) plugin.economy().add(killer.getUniqueId(), reward);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (blockRewards.containsKey(e.getBlockPlaced().getType())) {
            placedBlocks.add(e.getBlockPlaced().getLocation());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Long reward = blockRewards.get(b.getType());
        if (reward == null) return;
        if (placedBlocks.remove(b.getLocation())) return;
        plugin.economy().add(e.getPlayer().getUniqueId(), reward);
    }
}
