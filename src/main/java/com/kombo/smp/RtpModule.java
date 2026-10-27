package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Modul "rtp": Zufalls-Teleport.
 *  rtp  = großer Radius, kostet Shards, langer Cooldown
 *  rtpq = kleiner Radius, billig, kurzer Cooldown
 * Werte stehen in modules/rtp.yml unter den Abschnitten "rtp" und "rtpq".
 */
public final class RtpModule extends SmpModule {

    private static final Random RNG = new Random();
    private static final Set<Material> UNSAFE = EnumSet.of(
            Material.LAVA, Material.WATER, Material.CACTUS, Material.MAGMA_BLOCK,
            Material.FIRE, Material.SOUL_FIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    public RtpModule(KomboSMP plugin) {
        super(plugin, "rtp", "rtp", "rtpq");
    }

    @Override
    protected void onEnable() {
        cooldowns.clear();
        busy.clear();
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p != null) teleport(p, lower(cmd.getName()));
        return true;
    }

    private void teleport(Player p, String mode) {
        ConfigurationSection cfg = config().getConfigurationSection(mode);
        if (cfg == null) {
            Ui.send(p, "<red>Dieser Befehl ist in modules/rtp.yml nicht eingerichtet.");
            return;
        }
        UUID id = p.getUniqueId();
        if (busy.contains(id)) {
            Ui.send(p, "<gray>Du wirst gerade schon teleportiert.");
            return;
        }
        long now = System.currentTimeMillis();
        long until = cooldowns.computeIfAbsent(mode, k -> new HashMap<>()).getOrDefault(id, 0L);
        if (now < until) {
            Ui.send(p, "<red>Warte noch <white>" + ((until - now + 999) / 1000) + "</white> Sekunden.");
            return;
        }
        long cost = cfg.getLong("cost", 0);
        if (cost > 0 && !plugin.economy().remove(id, cost)) {
            Ui.send(p, "<red>Du brauchst <yellow>" + plugin.fmt(cost) + "</yellow> für diesen Teleport.");
            return;
        }
        busy.add(id);
        Ui.send(p, "<gray>Suche einen sicheren Ort …");
        attempt(p, mode, cfg, cost, cfg.getInt("attempts", 15));
    }

    private void attempt(Player p, String mode, ConfigurationSection cfg, long cost, int attemptsLeft) {
        World world = p.getWorld();
        int radius = Math.max(2, cfg.getInt("radius", 5000));
        int min = Math.min(Math.max(0, cfg.getInt("min-radius", 200)), radius - 1);
        int x = randomCoord(min, radius);
        int z = randomCoord(min, radius);

        world.getChunkAtAsync(x >> 4, z >> 4)
                .thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) {
                        fail(p, cost, "");
                        return;
                    }
                    Location spot = safeSpot(world, x, z);
                    if (spot != null) {
                        busy.remove(p.getUniqueId());
                        long cooldownMs = cfg.getLong("cooldown-seconds", 300) * 1000L;
                        cooldowns.computeIfAbsent(mode, k -> new HashMap<>())
                                .put(p.getUniqueId(), System.currentTimeMillis() + cooldownMs);
                        p.teleportAsync(spot);
                        Ui.send(p, "<green>Teleportiert!");
                    } else if (attemptsLeft > 1) {
                        attempt(p, mode, cfg, cost, attemptsLeft - 1);
                    } else {
                        fail(p, cost, "<red>Kein sicherer Ort gefunden. Versuch es nochmal.");
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("RTP fehlgeschlagen: " + ex.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> fail(p, cost, "<red>Teleport fehlgeschlagen."));
                    return null;
                });
    }

    private void fail(Player p, long cost, String message) {
        busy.remove(p.getUniqueId());
        if (cost > 0) plugin.economy().add(p.getUniqueId(), cost);
        if (p.isOnline() && !message.isEmpty()) Ui.send(p, message);
    }

    private static int randomCoord(int min, int max) {
        int value = min + RNG.nextInt(Math.max(1, max - min));
        return RNG.nextBoolean() ? value : -value;
    }

    private static Location safeSpot(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight()) return null;
        Block ground = world.getBlockAt(x, y, z);
        Material type = ground.getType();
        if (!type.isSolid() || ground.isLiquid() || UNSAFE.contains(type)) return null;
        if (!world.getBlockAt(x, y + 1, z).isPassable()) return null;
        if (!world.getBlockAt(x, y + 2, z).isPassable()) return null;
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
