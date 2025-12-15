package me.raikou.duels.punishment;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class PunishmentGui implements InventoryHolder {

    private final DuelsPlugin plugin;
    private final Inventory inventory;
    private final UUID targetUuid;

    public PunishmentGui(DuelsPlugin plugin, UUID targetUuid, String targetName) {
        this.plugin = plugin;
        this.targetUuid = targetUuid;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("History: " + targetName));
        loadHistory();
    }

    private void loadHistory() {
        plugin.getStorage().getPunishmentHistory(targetUuid, 45).thenAccept(history -> {
            int slot = 0;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

            for (Punishment p : history) {
                if (slot >= 53)
                    break;

                Material mat;
                String status;

                if (p.isRemoved()) {
                    mat = Material.LIME_WOOL;
                    status = "§aPardoned";
                } else if (!p.isActive() || p.isExpired()) {
                    mat = Material.YELLOW_WOOL;
                    status = "§eExpired";
                } else {
                    mat = Material.RED_WOOL;
                    status = "§cActive";
                }

                ItemBuilder builder = new ItemBuilder(mat)
                        .name("§6" + p.getType().name() + " #" + p.getId())
                        .lore(
                                "§7Status: " + status,
                                "§7Reason: §f" + p.getReason(),
                                "§7Issuer: §f" + p.getIssuerName(),
                                "§7Date: §f" + formatter.format(Instant.ofEpochMilli(p.getTimestamp())),
                                "§7Duration: §f" + PunishmentManager.getDurationString(p.getDuration()),
                                "");

                if (p.isRemoved()) {
                    builder.addLore(
                            "§7Removed By: §f" + p.getRemovedBy(),
                            "§7Removed Reason: §f" + p.getRemovedReason());
                }

                inventory.setItem(slot++, builder.build());
            }
        });
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
