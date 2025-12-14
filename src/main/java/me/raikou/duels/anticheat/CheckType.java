package me.raikou.duels.anticheat;

/**
 * Types of anti-cheat checks
 */
public enum CheckType {
    COMBAT, // KillAura, Reach, AutoClicker
    MOVEMENT, // Flight, Speed, NoFall
    PLAYER // Invalid packets, etc.
}
