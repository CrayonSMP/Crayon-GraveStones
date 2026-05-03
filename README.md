# betterGraveStones

**betterGraveStones** is a configurable Minecraft server plugin for **Minecraft/Paper 1.21.6+** that creates protected graves when players die, allowing them to recover their dropped items and experience in a controlled and server-friendly way.

It is designed for survival servers that want to reduce unfair item loss from despawn timers, lava, explosions, or unlucky deaths while still keeping death recovery fair and configurable.

> The project source may be publicly visible, but this project is currently **not licensed as open source**. Redistribution, reuploads, modified versions, or commercial use are not permitted without explicit permission from the author.

---

## Features

- Creates protected graves on player death
- Safely stores dropped items and configurable experience amounts
- Players can recover their own graves
- Admin tools for listing, inspecting, and managing graves
- Offline player grave lookup with tab-completion
- Configurable messages and world restrictions
- YAML storage with optional MySQL/MariaDB support
- Automatic YAML-to-MySQL migration
- Improved MySQL keep-alive handling
- Optional Locator Bar integration for supported Minecraft/Paper versions
- Optional player colors, UUID gradients, and privacy-focused grave waypoints
- Protected internal marker entities for Locator Bar grave markers

---

## Compatibility

| Component | Requirement |
| --- | --- |
| Minecraft | 1.21.6+ recommended |
| Server software | Paper recommended |
| Java | Use the Java version required by your server version |
| Storage | YAML by default, optional MySQL/MariaDB |

The optional Locator Bar integration depends on modern Minecraft/Paper waypoint support and should only be enabled on compatible server versions.

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
6. Restart the server after changing storage-related settings.

> Avoid updating or reloading this plugin through `/reload` or plugin managers such as PlugMan. A full server restart is strongly recommended.

---

## Configuration Files

### `config.yml`

Main plugin configuration. It controls grave behavior, XP recovery, looting rules, holograms, world restrictions, sounds, debug options, and Locator Bar settings.

Important options include:

```yml
ownerOnly: false
allowOthersToLoot: true
protectDropsForOpener: true
protectedDropTimeoutSeconds: 180
graveBlock: ANDESITE_WALL
graveLimit: 10
xpRestoreFraction: 0.33
captureDrops: true
adminCanBreak: true
disabledWorlds: []
```

### `messages.yml`

Contains all configurable in-game messages. Color codes using `&` are supported.

### `MySQL.yml`

Controls optional MySQL/MariaDB database storage.

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

## Migration from YAML to MySQL

If the plugin detects existing YAML graves and a valid MySQL configuration, it can migrate existing graves into the database automatically.

Recommended migration steps:

1. Stop the server.
2. Back up the plugin folder, especially `graves.yml`.
3. Configure `MySQL.yml`.
4. Start the server.
5. Check the console for migration messages.
6. Verify that graves are available through `/graves list`.

If MySQL is disabled or invalid, the plugin falls back to YAML storage.

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
| `/graves list <player> [page]` | Lists another player's stored graves | `graves.admin` |
| `/graves tp <graveId>` | Teleports to a grave from the list | Configurable, default `graves.admin.tp` |
| `/graves reload` | Reloads the plugin configuration | `graves.admin` |
| `/graves emergency <player>` | Opens the newest grave of a stored player in emergency mode | Player must be listed in `emergencyPlayers` |
| `/graves locatorall [on/off/toggle]` | Toggles admin visibility for all grave waypoints | Configurable, default `graves.admin` |

Stored offline player names are supported and can be tab-completed in supported commands.

---

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `graves.use` | `true` | Allows basic use of `/graves` |
| `graves.admin` | `op` | Allows admin actions such as reload, listing other players, admin grave access, and Locator Bar admin visibility |
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

### Locator Bar waypoints are not visible

Check:

- Your server version supports vanilla waypoints.
- `locatorBar.enabled` is set to `true`.
- `locatorBar.graves.enabled` or `locatorBar.players.enabled` is set to `true`.
- The gamerule is enabled.
- `transmitRange` is not too low.
- `strictVisibility` is not hiding the marker for privacy reasons.

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
