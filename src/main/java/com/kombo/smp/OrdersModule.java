package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Modul "orders": Käufer hinterlegen Geld, Verkäufer liefern Items. */
public final class OrdersModule extends SmpModule {

    public record Order(UUID id, UUID owner, String ownerName, Material material, int remaining, long price) {}

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45, SLOT_HELP = 47, SLOT_INFO = 49, SLOT_NEXT = 53;

    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private File file;

    public OrdersModule(KomboSMP plugin) {
        super(plugin, "orders", "orders", "order");
    }

    @Override
    protected void onEnable() {
        orders.clear();
        file = plugin.dataFile("orders.yml");
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("orders");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection o = sec.getConfigurationSection(key);
            if (o == null) continue;
            Material mat = Material.matchMaterial(o.getString("material", ""));
            if (mat == null) continue;
            UUID id = UUID.fromString(key);
            orders.put(id, new Order(id, UUID.fromString(o.getString("owner")), o.getString("owner-name", "?"),
                    mat, o.getInt("remaining"), o.getLong("price")));
        }
    }

    @Override
    public void save() {
        if (file == null) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Order o : orders.values()) {
            String base = "orders." + o.id() + ".";
            yml.set(base + "owner", o.owner().toString());
            yml.set(base + "owner-name", o.ownerName());
            yml.set(base + "material", o.material().name());
            yml.set(base + "remaining", o.remaining());
            yml.set(base + "price", o.price());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte orders.yml nicht speichern", e);
        }
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p == null) return true;

        if (lower(cmd.getName()).equals("orders")) {
            open(p, 0);
            return true;
        }
        if (a.length != 4 || !lower(a[0]).equals("create")) {
            Ui.send(p, "<red>Benutzung: /order create <item> <menge> <preis pro Stück>");
            return true;
        }
        Material m = Material.matchMaterial(a[1].toUpperCase(Locale.ROOT));
        Long amount = parsePositive(a[2]);
        Long price = parsePositive(a[3]);
        if (m == null || amount == null || price == null || amount > 6400) {
            Ui.send(p, "<red>Ungültige Eingabe. Beispiel: /order create diamond 64 30");
            return true;
        }
        create(p, m, amount.intValue(), price);
        return true;
    }

    private void create(Player p, Material m, int amount, long price) {
        if (!m.isItem()) {
            Ui.send(p, "<red>Das ist kein gültiges Item.");
            return;
        }
        long mine = orders.values().stream().filter(o -> o.owner().equals(p.getUniqueId())).count();
        int max = config().getInt("max-per-player", 5);
        if (mine >= max) {
            Ui.send(p, "<red>Du hast bereits " + max + " Orders.");
            return;
        }
        long escrow = price * amount;
        if (!plugin.economy().remove(p.getUniqueId(), escrow)) {
            Ui.send(p, "<red>Du brauchst <yellow>" + plugin.fmt(escrow) + "</yellow> als Sicherheit.");
            return;
        }
        UUID id = UUID.randomUUID();
        orders.put(id, new Order(id, p.getUniqueId(), p.getName(), m, amount, price));
        Ui.send(p, "<green>Order erstellt: <white>" + amount + "x " + Ui.pretty(m) + "</white> zu <yellow>"
                + plugin.fmt(price) + "</yellow> pro Stück.");
    }

    private void fill(Player seller, UUID id) {
        Order o = orders.get(id);
        if (o == null) {
            Ui.send(seller, "<red>Diese Order existiert nicht mehr.");
            return;
        }
        if (o.owner().equals(seller.getUniqueId())) {
            Ui.send(seller, "<red>Das ist deine eigene Order.");
            return;
        }
        ItemStack[] contents = seller.getInventory().getStorageContents();
        int count = 0;
        for (int i = 0; i < contents.length && count < o.remaining(); i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != o.material()) continue;
            int take = Math.min(s.getAmount(), o.remaining() - count);
            count += take;
            if (take == s.getAmount()) contents[i] = null;
            else s.setAmount(s.getAmount() - take);
        }
        if (count == 0) {
            Ui.send(seller, "<red>Du hast kein(e) " + Ui.pretty(o.material()) + " im Inventar.");
            return;
        }
        seller.getInventory().setStorageContents(contents);

        int stack = o.material().getMaxStackSize();
        int left = count;
        while (left > 0) {
            int n = Math.min(stack, left);
            plugin.mailbox().deliver(o.owner(), new ItemStack(o.material(), n));
            left -= n;
        }
        long pay = o.price() * count;
        plugin.economy().add(seller.getUniqueId(), pay);

        int remaining = o.remaining() - count;
        if (remaining <= 0) orders.remove(id);
        else orders.put(id, new Order(id, o.owner(), o.ownerName(), o.material(), remaining, o.price()));

        Ui.send(seller, "<green>Geliefert: <white>" + count + "x " + Ui.pretty(o.material())
                + "</white> für <yellow>" + plugin.fmt(pay) + "</yellow>.");
        Player owner = Bukkit.getPlayer(o.owner());
        if (owner != null) {
            Ui.send(owner, "<green>Deine Order wurde beliefert: <white>" + count + "x " + Ui.pretty(o.material())
                    + "</white> (siehe /collect).");
        }
    }

    private void cancel(Player p, UUID id) {
        Order o = orders.get(id);
        if (o == null || !o.owner().equals(p.getUniqueId())) {
            Ui.send(p, "<red>Diese Order gehört dir nicht oder existiert nicht mehr.");
            return;
        }
        orders.remove(id);
        long refund = o.price() * o.remaining();
        plugin.economy().add(p.getUniqueId(), refund);
        Ui.send(p, "<green>Order abgebrochen. <yellow>" + plugin.fmt(refund) + "</yellow> zurückerstattet.");
    }

    /* ---------- GUI ---------- */

    private static final class Holder implements InventoryHolder {
        final int page;
        final List<UUID> shown = new ArrayList<>();
        Inventory inventory;

        Holder(int page) { this.page = page; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private void open(Player p, int requestedPage) {
        List<Order> all = orders.values().stream()
                .sorted(Comparator.comparingLong(Order::price).reversed())
                .toList();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Holder holder = new Holder(page);
        holder.inventory = Bukkit.createInventory(holder, 54, KomboSMP.mm("<gold><bold>Orders</bold></gold>"));
        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < all.size(); i++) {
            Order o = all.get(from + i);
            holder.shown.add(o.id());
            holder.inventory.setItem(i, display(o, p));
        }
        if (page > 0) holder.inventory.setItem(SLOT_PREV, Ui.icon(Material.ARROW, "<yellow>Vorherige Seite"));
        if (page < pages - 1) holder.inventory.setItem(SLOT_NEXT, Ui.icon(Material.ARROW, "<yellow>Nächste Seite"));
        holder.inventory.setItem(SLOT_INFO, Ui.icon(Material.BOOK, "<white>Seite " + (page + 1) + "/" + pages,
                "<gray>" + all.size() + " offene Orders"));
        holder.inventory.setItem(SLOT_HELP, Ui.icon(Material.PAPER, "<aqua>Neue Order erstellen",
                "<gray>/order create <item> <menge> <preis>"));
        p.openInventory(holder.inventory);
    }

    private ItemStack display(Order o, Player viewer) {
        boolean own = o.owner().equals(viewer.getUniqueId());
        return Ui.icon(o.material(),
                "<white>" + Ui.pretty(o.material()) + "</white> <gray>x" + o.remaining(),
                "<gray>Preis: <yellow>" + plugin.fmt(o.price()) + "</yellow> pro Stück",
                "<gray>Gesamt offen: <yellow>" + plugin.fmt(o.price() * o.remaining()),
                "<gray>Besteller: <white>" + Ui.esc(o.ownerName()),
                own ? "<red>Klick: Order abbrechen (Geld zurück)"
                    : "<green>Klick: Passende Items aus deinem Inventar liefern");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        int slot = e.getSlot();
        if (slot == SLOT_PREV) {
            open(p, holder.page - 1);
        } else if (slot == SLOT_NEXT) {
            open(p, holder.page + 1);
        } else if (slot < holder.shown.size()) {
            UUID id = holder.shown.get(slot);
            Order o = orders.get(id);
            if (o != null && o.owner().equals(p.getUniqueId())) cancel(p, id);
            else fill(p, id);
            open(p, holder.page);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
