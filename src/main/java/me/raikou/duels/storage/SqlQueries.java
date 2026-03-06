package me.raikou.duels.storage;

final class SqlQueries {

    private SqlQueries() {
    }

    static final String CREATE_DUEL_STATS = """
            CREATE TABLE IF NOT EXISTS duel_stats (
                uuid VARCHAR(36) PRIMARY KEY,
                name VARCHAR(32) DEFAULT '',
                wins INT DEFAULT 0,
                losses INT DEFAULT 0,
                kills INT DEFAULT 0,
                deaths INT DEFAULT 0,
                current_streak INT DEFAULT 0,
                best_streak INT DEFAULT 0,
                last_played BIGINT DEFAULT 0,
                playtime BIGINT DEFAULT 0
            )
            """;

    static final String CREATE_KIT_LAYOUTS = """
            CREATE TABLE IF NOT EXISTS kit_layouts (
                uuid VARCHAR(36),
                kit_name VARCHAR(64),
                layout_data TEXT,
                PRIMARY KEY (uuid, kit_name)
            )
            """;

    static final String CREATE_ELO_RATINGS = """
            CREATE TABLE IF NOT EXISTS elo_ratings (
                uuid VARCHAR(36),
                kit_name VARCHAR(64),
                elo INT DEFAULT 1000,
                PRIMARY KEY (uuid, kit_name)
            )
            """;

    static final String CREATE_PUNISHMENTS_SQLITE = """
            CREATE TABLE IF NOT EXISTS duels_punishments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(32),
                issuer_name VARCHAR(32),
                type VARCHAR(16) NOT NULL,
                reason TEXT,
                timestamp BIGINT,
                duration BIGINT,
                active BOOLEAN,
                removed BOOLEAN,
                removed_by VARCHAR(32),
                removed_reason TEXT
            )
            """;

    static final String CREATE_PUNISHMENTS_MYSQL = """
            CREATE TABLE IF NOT EXISTS duels_punishments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(32),
                issuer_name VARCHAR(32),
                type VARCHAR(16) NOT NULL,
                reason TEXT,
                timestamp BIGINT,
                duration BIGINT,
                active BOOLEAN,
                removed BOOLEAN,
                removed_by VARCHAR(32),
                removed_reason TEXT
            )
            """;
}
