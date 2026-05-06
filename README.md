<p align="center">
  <img src="https://img.shields.io/github/v/release/SLINIcraftet204/bGraveStones?include_prereleases&label=release" alt="GitHub release">
  <img src="https://github.com/SLINIcraftet204/bGraveStones/actions/workflows/build.yml/badge.svg" alt="Build status">
  <img src="https://img.shields.io/badge/focus%20MC%20Version-1.21.6%2B-brightgreen" alt="focus MC Version 1.21.6+">
  <img src="https://img.shields.io/badge/Server-Paper%20%7C%20Purpur-blue" alt="Paper and Purpur">
  <img src="https://img.shields.io/badge/Folia-detected%20%2F%20blocked%20by%20default-orange" alt="Folia detected but blocked by default">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Storage-YAML%20%7C%20MySQL-lightgrey" alt="YAML and MySQL storage">
  <img src="https://img.shields.io/badge/License-All%20Rights%20Reserved-red" alt="All Rights Reserved">
</p>

<p align="center">
  <a href="https://ko-fi.com/slinicraftet204">
    <img src="https://img.shields.io/badge/Support-Ko--fi-ff5f5f?logo=kofi&logoColor=white" alt="Support on Ko-fi">
  </a>
</p>

# betterGraveStones

**betterGraveStones** is a configurable Minecraft server plugin for **Minecraft/Paper 1.21.6+** that creates protected graves when players die, allowing them to recover their dropped items and experience in a controlled and server-friendly way.

It is designed for survival servers that want to reduce unfair item loss from despawn timers, lava, explosions, or unlucky deaths while still keeping death recovery fair and configurable.

> The project source may be publicly visible, but this project is currently **not licensed as open source**. Redistribution, reuploads, modified versions, or commercial use are not permitted without explicit permission from the author.

---

## Features

- Creates protected graves on player death
- Safely stores dropped items and configurable experience amounts
- Players can recover their own graves
- Admin tools for listing, inspecting, teleporting to, and managing graves
- Offline player grave lookup with tab-completion
- Configurable messages and world restrictions
- Versioned generated files for `config.yml`, `messages.yml`, and `MySQL.yml`
- Safe generated-file updates that preserve existing user values
- YAML storage with optional MySQL/MariaDB support
- Automatic YAML-to-MySQL migration
- Improved MySQL keep-alive handling
- Optional Modrinth update checker
- Optional Locator Bar integration for supported Minecraft/Paper versions
- Optional player colors, UUID gradients, and privacy-focused grave waypoints
- Protected internal marker entities for Locator Bar grave markers
- Server platform detection for Paper, Purpur, Folia and related Bukkit/Paper platforms

---

## Compatibility

| Component | Status |
| --- | --- |
| Minecraft | 1.21.6+ recommended |
| Paper | Recommended |
| Purpur | Supported / tested |
| Spigot/Bukkit | Not the primary target; may depend on used APIs/features |
| Folia | Detected, but blocked by default until native Folia support exists |
| Java | Use the Java version required by your server version |
| Storage | YAML by default, optional MySQL/MariaDB |

The optional Locator Bar integration depends on modern Minecraft/Paper waypoint support and should only be enabled on compatible server versions.

### Compatibility config

```yml
compatibility:
  logPlatformOnStartup: true

  folia:
    disablePlugin: true

  features:
    locatorBar:
      autoDisableWhenUnsupported: true
```

Folia is currently **not natively supported**. It is detected and blocked by default to prevent unsafe scheduler, teleport, world, or entity access.

---

## Installation

1. Download the latest plugin `.jar`.
2. Stop your Minecraft server.
3. Place the `.jar` file into your server's `plugins/` folder.
4. Start the server once to generate the configuration files.
5. Edit the generated files if needed:
    - `config.yml`
    - `messages.yml`
    - `MySQL.yml`
6. Restart the server after changing storage, compatibility, or Locator Bar settings.

> Avoid updating or reloading this plugin through `/reload` or plugin managers such as PlugMan. A full server restart is strongly recommended.

---

## Generated file versioning

betterGraveStones version-controls generated plugin files and updates them defensively.

| File | Version key |
| --- | --- |
| `config.yml` | `configVersion` |
| `messages.yml` | `messagesVersion` |
| `MySQL.yml` | `mysqlConfigVersion` |

The updater can add missing keys from the bundled default files while keeping existing user values unchanged.

Backups are created before generated files are modified:

```text
plugins/betterGraveStones/backups/generated-files/
```

This helps users update safely without losing customized messages, database settings, or gameplay options.

---

## Storage

betterGraveStones supports both YAML file storage and optional MySQL/MariaDB database storage.

By default, the plugin uses local YAML storage. If MySQL is configured correctly, the plugin can use database storage instead. When MySQL is enabled later, existing YAML graves can be migrated into the database automatically.

### YAML Storage

YAML storage is enabled automatically when MySQL is disabled, incomplete, or still contains sample values.

With YAML storage, `graveLimit` is used to limit the maximum amount of stored graves per player.

### MySQL / MariaDB Storage

To enable database storage, edit `MySQL.yml`:

```yml
mysqlConfigVersion: 1

enabled: true
host: "127.0.0.1"
port: 3306
database: "database"
username: "user"
password: "password"

useTablePrefix: false
tablePrefix: "bgstones_"
useSSL: false
parameters: "allowPublicKeyRetrieval=true"

keepAlive:
  enabled: true
  intervalSeconds: 240
  validationTimeoutSeconds: 3
```

The plugin validates and reconnects before SQL operations. The optional keep-alive task helps prevent idle database connections from being closed silently by the database server.

---

## Modrinth update checker

The plugin can check Modrinth for newer published versions.

```yml
updateChecker:
  enabled: true
  checkOnStartup: true
  checkIntervalHours: 12
  notifyOnlineAdmins: true
  notifyAdminsOnJoin: true
  includePrereleases: false
  logErrors: true
```

Manual check:

```text
/graves updatecheck
```

Permission:

```text
graves.admin
```

---

## Locator Bar Support

The plugin includes optional support for the vanilla Minecraft Locator Bar on supported Minecraft/Paper versions.

This feature can show grave-related waypoints and optionally apply colors to player waypoints. Grave marker colors are reserved, such as gray and gold, while player colors avoid those reserved grave colors.

Example configuration:

```yml
locatorBar:
  enabled: true
  forceGamerule: true

  commands:
    silent: true
    debug: false

  players:
    enabled: true
    colorMode: "uuid_hash"
    uuidHashStyle: "gradient"
    uuidGradient:
      intervalTicks: 20
      steps: 32

  graves:
    enabled: true
    color: "gray"
    nearColor: "gold"
    transmitRange: 256
    nearGlowDistance: 24
    glowWhenNear: true
    strictVisibility: true
    viewAllPermission: "graves.admin"
```

### Privacy Note

Vanilla waypoints do not provide true per-player visibility. To protect private grave locations, the plugin can limit grave waypoint visibility through `strictVisibility` and the global waypoint transmit range.

For privacy-friendly setups, avoid very high values such as `10000` and prefer a lower range:

```yml
locatorBar:
  graves:
    strictVisibility: true
    transmitRange: 256
```

---

## Commands

| Command | Description | Permission / Access |
| --- | --- | --- |
| `/graves list` | Lists your own graves | `graves.use` |
| `/graves list <page>` | Lists a specific page of your own graves | `graves.use` |
| `/graves list <player> [page]` | Lists another player's stored graves | `graves.admin` |
| `/graves tp <player> <id>` | Teleports to a grave using the visible per-player list ID | Configurable, default `graves.admin.tp` |
| `/graves reload` | Reloads the plugin configuration | `graves.admin` |
| `/graves updatecheck` | Checks Modrinth for a newer release | `graves.admin` |
| `/graves emergency` | Opens the grave block the configured emergency player is looking at | Player must be listed in `emergencyPlayers` |
| `/graves locatorall [on/off/toggle]` | Toggles admin visibility for all grave waypoints | Configurable, default `graves.admin` |

Stored offline player names are supported and can be tab-completed in supported commands. Grave teleport IDs are the visible `#ID` values from `/graves list`.

---

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `graves.use` | `true` | Allows basic use of `/graves` |
| `graves.admin` | `op` | Allows admin actions such as reload, listing other players, update checks, admin grave access, and Locator Bar admin visibility |
| `graves.admin.tp` | configurable | Allows teleporting to listed graves through clickable list entries or `/graves tp` |

The teleport permission can be changed in `config.yml`:

```yml
graveTeleportPermission: "graves.admin.tp"
```

The Locator Bar admin visibility permission can be changed here:

```yml
locatorBar:
  graves:
    viewAllPermission: "graves.admin"
```

---

## Looting and XP Behavior

The plugin can be configured to allow owner-only graves, public looting, protected dropped items for the opener, and configurable XP recovery.

Important options:

```yml
ownerOnly: false
allowOthersToLoot: true
protectDropsForOpener: true
protectedDropTimeoutSeconds: 180

xpRestoreFraction: 0.33
xpStealable: true
xpDropAsOrbsForNonOwner: false

captureDrops: true
```

`xpRestoreFraction` uses values from `0.00` to `1.00`:

- `1.00` = 100%
- `0.50` = 50%
- `0.33` = 33%
- `0.00` = 0%

---

## Holograms

Graves can display a configurable text hologram with the player name and death date.

Example settings:

```yml
hologramEnabled: true
hologramFormat: "§7✝ §f{player}\n§7{date}"
hologramDateFormat: "dd.MM.yyyy\nHH:mm"
hologramTextScale: 0.55
hologramFacing: NORTH
```

---

## World Restrictions

You can disable grave creation in specific worlds:

```yml
disabledWorlds: ["world_nether", "world_the_end"]
```

---

## Updating

When updating the plugin:

1. Stop the server.
2. Back up the plugin folder.
3. Replace the old `.jar` file.
4. Start the server.
5. Check the console for warnings or migration messages.
6. Check `backups/generated-files/` if generated files were updated.

Do not update using `/reload` or plugin managers. This can cause classloader issues such as `zip file closed`.

---

## Troubleshooting

### MySQL is not used

Check that `MySQL.yml` is enabled and no longer contains sample values:

```yml
enabled: true
database: "your_database"
username: "your_user"
password: "your_password"
```

If the configuration is incomplete, the plugin will fall back to YAML storage.

### Generated files are missing new options

The generated file updater should add missing keys automatically. Check:

```text
plugins/betterGraveStones/backups/generated-files/
```

Existing user values should be preserved during normal generated-file updates.

### Folia disables the plugin

This is expected in the current compatibility state. Folia is detected, but native Folia support is not implemented yet.

### Locator Bar waypoints are not visible

Check:

- Your server version supports vanilla waypoints.
- `locatorBar.enabled` is set to `true`.
- `locatorBar.graves.enabled` or `locatorBar.players.enabled` is set to `true`.
- The gamerule is enabled.
- `transmitRange` is not too low.
- `strictVisibility` is not hiding the marker for privacy reasons.
- Compatibility auto-disable did not disable the feature.

### Console shows waypoint command output

Set:

```yml
locatorBar:
  commands:
    silent: true
    debug: false
```

### Tab completion or classes fail after replacing the jar

Stop the server fully, remove duplicate old plugin jars, place only one current jar in the `plugins/` folder, and start the server again.

---

## Wiki

Detailed documentation is available in the GitHub Wiki:

- [English Documentation](https://github.com/SLINIcraftet204/bGraveStones/wiki/English)
- [Deutsche Dokumentation](https://github.com/SLINIcraftet204/bGraveStones/wiki/Deutsch)

---

## License / Rights

This project is currently published as **All Rights Reserved / No License**.

The source code may be publicly visible for transparency and issue tracking, but the project is not licensed as open source. Redistribution, reuploads, modified versions, or commercial use are not permitted without explicit permission from the author.

---

## Author

Created by **SLINIcraftet204**.
