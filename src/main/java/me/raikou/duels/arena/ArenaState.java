package me.raikou.duels.arena;

public enum ArenaState {
    WAITING,
    RUNNING,
    ENDING;

    public boolean isAvailable() {
        return this == WAITING;
    }
}
