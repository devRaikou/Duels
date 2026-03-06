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
        this.inventory = Bukkit.createInventory(this, 54,
                me.raikou.duels.util.MessageUtil.get("gui.punishment.title", "%player%", targetName));
        loadHistory();
    }

    private void loadHistory() {
        plugin.getStorage().getPunishmentHistory(targetUuid, 45).thenAccept(history -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    int slot = 0;
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                            .withZone(ZoneId.systemDefault());

                    for (Punishment p : history) {
                        if (slot >= 53)
                            break;

                        Material mat;

                        if (p.isRemoved()) {
                            mat = Material.LIME_WOOL;
                        } else if (!p.isActive() || p.isExpired()) {
                            mat = Material.YELLOW_WOOL;
                        } else {
                            mat = Material.RED_WOOL;
                        }

                        String statusStr = p.isRemoved()
                                ? me.raikou.duels.util.MessageUtil.getString("gui.punishment.status.pardoned")
                                : (!p.isActive() || p.isExpired())
                                        ? me.raikou.duels.util.MessageUtil.getString("gui.punishment.status.expired")
                                        : me.raikou.duels.util.MessageUtil.getString("gui.punishment.status.active");

                        ItemBuilder builder = new ItemBuilder(mat)
                                .name(me.raikou.duels.util.MessageUtil.get("gui.punishment.item-name", "%type%",
                                        p.getType().name(), "%id%", String.valueOf(p.getId())))
                                .lore(
                                        me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.status", "%status%",
                                                statusStr),
                                        me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.reason", "%reason%",
                                                p.getReason()),
                                        me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.issuer", "%issuer%",
                                                p.getIssuerName()),
                                        me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.date", "%date%",
                                                formatter.format(Instant.ofEpochMilli(p.getTimestamp()))),
                                        me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.duration",
                                                "%duration%",
                                                PunishmentManager.getDurationString(p.getDuration())),
                                        Component.empty());

                        if (p.isRemoved()) {
                            builder.addLore(
                                    me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.removed-by", "%player%",
                                            p.getRemovedBy()),
                                    me.raikou.duels.util.MessageUtil.get("gui.punishment.lore.removed-reason",
                                            "%reason%",
                                            p.getRemovedReason()));
                        }

                        inventory.setItem(slot++, builder.build());
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading punishment history GUI: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
