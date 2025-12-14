package me.raikou.duels.arena;

import lombok.Data;
import org.bukkit.Location;

@Data
public class Arena {
    private final String name;
    private Location spawn1;
    private Location spawn2;
    private Location spectatorSpawn;
    private ArenaState state;

    public Arena(String name) {
        this.name = name;
        this.state = ArenaState.WAITING;
    }

    public boolean isSetup() {
        return spawn1 != null && spawn2 != null;
    }
}
