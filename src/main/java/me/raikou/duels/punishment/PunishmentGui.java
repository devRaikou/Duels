package me.raikou.duels.punishment;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.ItemBuilder;

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
        this.inventory = Bukkit.createInventory(this, 54,
                me.raikou.duels.util.MessageUtil.get("gui.punishment.title", "%player%", targetName));
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
                    status = me.raikou.duels.util.MessageUtil.getString("gui.punishment.status.pardoned");
                } else if (!p.isActive() || p.isExpired()) {
                    mat = Material.YELLOW_WOOL;
                    status = me.raikou.duels.util.MessageUtil.getString("gui.punishment.status.expired");
                } else {
                    mat = Material.RED_WOOL;
                    status = me.raikou.duels.util.MessageUtil.getString("gui.punishment.status.active");
                }

                ItemBuilder builder = new ItemBuilder(mat)
                        .name(me.raikou.duels.util.MessageUtil.getString("gui.punishment.item-name")
                                .replace("%type%", p.getType().name()).replace("%id%", String.valueOf(p.getId())))
                        .lore(
                                me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.status")
                                        .replace("%status%", status),
                                me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.reason")
                                        .replace("%reason%", p.getReason()),
                                me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.issuer")
                                        .replace("%issuer%", p.getIssuerName()),
                                me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.date").replace("%date%",
                                        formatter.format(Instant.ofEpochMilli(p.getTimestamp()))),
                                me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.duration")
                                        .replace("%duration%", PunishmentManager.getDurationString(p.getDuration())),
                                "");

                if (p.isRemoved()) {
                    builder.addLore(
                            me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.removed-by")
                                    .replace("%player%", p.getRemovedBy()),
                            me.raikou.duels.util.MessageUtil.getString("gui.punishment.lore.removed-reason")
                                    .replace("%reason%", p.getRemovedReason()));
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
