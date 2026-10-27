package com.kombo.smp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Modul "auction": Auktionshaus mit Angeboten, Steuer, Ablauf und GUI. */
public final class AuctionModule extends SmpModule {

    public record Listing(UUID id, UUID seller, String sellerName, ItemStack item, long price, long expires) {}

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45, SLOT_TOGGLE = 47, SLOT_INFO = 49, SLOT_COLLECT = 51, SLOT_NEXT = 53;

    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private File file;
    private BukkitTask expiryTask;

    public AuctionModule(KomboSMP plugin) {
        super(plugin, "auction", "ah");
    }

    @Override
    protected void onEnable() {
        listings.clear();
        file = plugin.dataFile("auction.yml");
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection ls = yml.getConfigurationSection("listings");
            if (ls != null) {
                for (String key : ls.getKeys(false)) {
                    ConfigurationSection s = ls.getConfigurationSection(key);
                    if (s == null || !(s.get("item") instanceof ItemStack stack)) continue;
                    UUID id = UUID.fromString(key);
                    listings.put(id, new Listing(id, UUID.fromString(s.getString("seller")),
                            s.getString("seller-name", "?"), stack, s.getLong("price"), s.getLong("expires")));
                }
            }
        }
        long ticks = 20L * 60L;
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::expireOld, ticks, ticks);
    }

    @Override
    protected void onDisable() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    @Override
    public void save() {
        if (file == null) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String base = "listings." + l.id() + ".";
            yml.set(base + "seller", l.seller().toString());
            yml.set(base + "seller-name", l.sellerName());
            yml.set(base + "item", l.item());
            yml.set(base + "price", l.price());
            yml.set(base + "expires", l.expires());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte auction.yml nicht speichern", e);
        }
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p == null) return true;

        if (a.length == 0) {
            open(p, 0, false);
            return true;
        }
        switch (lower(a[0])) {
            case "sell" -> {
                if (a.length != 2) {
                    Ui.send(p, "<red>Benutzung: /ah sell <preis>");
                    return true;
                }
                Long price = parsePositive(a[1]);
                if (price == null) {
                    Ui.send(p, "<red>Ungültiger Preis.");
                    return true;
                }
                listItem(p, price);
            }
            case "mine" -> open(p, 0, true);
            case "collect" -> plugin.mailbox().collect(p);
            default -> Ui.send(p, "<red>Benutzung: /ah [sell <preis> | mine | collect]");
        }
        return true;
    }

    /* ---------- Angebote ---------- */

    private void listItem(Player p, long price) {
        long min = config().getLong("min-price", 1);
        long max = config().getLong("max-price", 1_000_000_000L);
        if (price < min || price > max) {
            Ui.send(p, "<red>Preis zwischen <yellow>" + plugin.fmt(min) + "</yellow> und <yellow>" + plugin.fmt(max) + "</yellow>.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Ui.send(p, "<red>Halte das Item in deiner Haupthand.");
            return;
        }
        int maxListings = config().getInt("max-listings", 10);
        long mine = listings.values().stream().filter(l -> l.seller().equals(p.getUniqueId())).count();
        if (mine >= maxListings) {
            Ui.send(p, "<red>Du hast bereits " + maxListings + " Angebote.");
            return;
        }
        ItemStack item = hand.clone();
        p.getInventory().setItemInMainHand(null);
        long hours = config().getLong("expire-hours", 48);
        UUID id = UUID.randomUUID();
        listings.put(id, new Listing(id, p.getUniqueId(), p.getName(), item, price,
                System.currentTimeMillis() + hours * 3_600_000L));
        Ui.send(p, "<green>Angebot erstellt: <white>" + item.getAmount() + "x " + Ui.pretty(item.getType())
                + "</white> für <yellow>" + plugin.fmt(price) + "</yellow>.");
    }

    private void buy(Player buyer, UUID id) {
        Listing l = listings.get(id);
        if (l == null) {
            Ui.send(buyer, "<red>Dieses Angebot existiert nicht mehr.");
            return;
        }
        if (l.seller().equals(buyer.getUniqueId())) {
            Ui.send(buyer, "<red>Das ist dein eigenes Angebot.");
            return;
        }
        if (!plugin.economy().remove(buyer.getUniqueId(), l.price())) {
            Ui.send(buyer, "<red>Du brauchst <yellow>" + plugin.fmt(l.price()) + "</yellow> für dieses Angebot.");
            return;
        }
        listings.remove(id);

        double tax = config().getDouble("tax-percent", 5.0);
        long payout = Math.round(l.price() * (1.0 - tax / 100.0));
        plugin.economy().add(l.seller(), payout);
        plugin.mailbox().giveOrMail(buyer, l.item());

        Ui.send(buyer, "<green>Gekauft für <yellow>" + plugin.fmt(l.price()) + "</yellow>.");
        Player seller = Bukkit.getPlayer(l.seller());
        if (seller != null) {
            Ui.send(seller, "<green>Angebot verkauft. Du erhältst <yellow>" + plugin.fmt(payout) + "</yellow> (nach Steuer).");
        }
    }

    private void cancel(Player p, UUID id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller().equals(p.getUniqueId())) {
            Ui.send(p, "<red>Dieses Angebot gehört dir nicht oder existiert nicht mehr.");
            return;
        }
        listings.remove(id);
        plugin.mailbox().deliver(p.getUniqueId(), l.item());
        Ui.send(p, "<green>Angebot zurückgezogen. Das Item liegt im Postfach (/collect).");
    }

    private void expireOld() {
        long now = System.currentTimeMillis();
        Iterator<Listing> it = listings.values().iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            if (l.expires() <= now) {
                it.remove();
                plugin.mailbox().deliver(l.seller(), l.item());
                Player seller = Bukkit.getPlayer(l.seller());
                if (seller != null) Ui.send(seller, "<yellow>Dein Angebot ist abgelaufen. Das Item liegt im Postfach (/collect).");
            }
        }
    }

    /* ---------- GUI ---------- */

    private static final class Holder implements InventoryHolder {
        final int page;
        final boolean own;
        final List<UUID> shown = new ArrayList<>();
        Inventory inventory;

        Holder(int page, boolean own) {
            this.page = page;
            this.own = own;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private void open(Player p, int requestedPage, boolean own) {
        List<Listing> all = listings.values().stream()
                .filter(l -> !own || l.seller().equals(p.getUniqueId()))
                .toList();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Holder holder = new Holder(page, own);
        holder.inventory = Bukkit.createInventory(holder, 54,
                KomboSMP.mm(own ? "<gold><bold>Meine Angebote</bold></gold>" : "<gold><bold>Auktionshaus</bold></gold>"));

        long now = System.currentTimeMillis();
        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < all.size(); i++) {
            Listing l = all.get(from + i);
            holder.shown.add(l.id());
            holder.inventory.setItem(i, displayItem(l, own, now));
        }
        if (page > 0) holder.inventory.setItem(SLOT_PREV, Ui.icon(Material.ARROW, "<yellow>Vorherige Seite"));
        if (page < pages - 1) holder.inventory.setItem(SLOT_NEXT, Ui.icon(Material.ARROW, "<yellow>Nächste Seite"));
        holder.inventory.setItem(SLOT_INFO, Ui.icon(Material.BOOK, "<white>Seite " + (page + 1) + "/" + pages,
                "<gray>" + all.size() + " Angebote"));
        holder.inventory.setItem(SLOT_TOGGLE, Ui.icon(Material.CHEST, own ? "<aqua>Zum Marktplatz" : "<aqua>Meine Angebote"));
        holder.inventory.setItem(SLOT_COLLECT, Ui.icon(Material.ENDER_CHEST, "<light_purple>Postfach leeren"));
        p.openInventory(holder.inventory);
    }

    private ItemStack displayItem(Listing l, boolean own, long now) {
        ItemStack stack = l.item().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        long hours = Math.max(0, (l.expires() - now) / 3_600_000L);
        lore.add(KomboSMP.mm(""));
        lore.add(KomboSMP.mm("<gray>Preis: <yellow>" + plugin.fmt(l.price())));
        lore.add(KomboSMP.mm("<gray>Verkäufer: <white>" + Ui.esc(l.sellerName())));
        lore.add(KomboSMP.mm("<gray>Läuft ab in: <white>" + hours + " h"));
        lore.add(KomboSMP.mm(own ? "<red>Klick: Angebot zurückholen" : "<green>Klick: Kaufen"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        int slot = e.getSlot();
        if (slot == SLOT_PREV) {
            open(p, holder.page - 1, holder.own);
        } else if (slot == SLOT_NEXT) {
            open(p, holder.page + 1, holder.own);
        } else if (slot == SLOT_TOGGLE) {
            open(p, 0, !holder.own);
        } else if (slot == SLOT_COLLECT) {
            plugin.mailbox().collect(p);
            open(p, holder.page, holder.own);
        } else if (slot < holder.shown.size()) {
            UUID id = holder.shown.get(slot);
            if (holder.own) cancel(p, id);
            else buy(p, id);
            open(p, holder.page, holder.own);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
