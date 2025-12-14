# Duels Plugin

**Professional Duels Core Plugin** for Minecraft servers, developed by Raikou.

## Features
- **1v1 Duels**: Challenge other players to combat.
- **Custom Kits**: Create and manage specialized kits for duels.
- **Statistics**: Track wins, losses, and other stats (planned/in-progress).
- **Admin Tools**: Comprehensive admin commands for managing the system.

## Requirements
- Java 21+
- Paper 1.21+ (or compatible forks)

## Installation
1. Download the latest release JAR.
2. Place it in your server's `plugins/` folder.
3. Restart the server.

## Commands

### Player Commands
| Command | Alias | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/duel <player>` | `/duels` | Send a duel request to a player | None |
| `/duel accept <player>` | | Accept a duel request | None |
| `/duel deny <player>` | | Deny a duel request | None |

### Admin Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/duel admin reload` | Reload plugin configuration | `duels.admin` |
| `/duel admin kit create <name>` | Create a new kit from inventory | `duels.admin` |

## Permissions
- `duels.admin`: Grants access to all administrative commands. (Default: OP)

## Building from Source

```bash
git clone https://github.com/YOUR_USERNAME/Duels.git
cd Duels
mvn clean package
```

The compiled JAR will be in the `target/` directory.

## License
This project is licensed under the [MIT License](LICENSE).
