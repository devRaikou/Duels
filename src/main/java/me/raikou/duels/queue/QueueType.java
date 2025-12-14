package me.raikou.duels.queue;

import lombok.Getter;

@Getter
public enum QueueType {
    SOLO(2),
    TEAM(4); // 2v2

    private final int requiredPlayers;

    QueueType(int requiredPlayers) {
        this.requiredPlayers = requiredPlayers;
    }
}
