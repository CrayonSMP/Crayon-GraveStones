---
name: Bug report
about: Create a report to help us improve
title: ''
labels: bug
assignees: SLINIcraftet204

---

## Bug description

Describe the problem clearly and briefly.

Example:  
"When a player dies in the Nether, no grave is created."

---

## Steps to reproduce

1. Start the server with betterGraveStones version `...`
2. Join as a player
3. Die with items in inventory
4. Try to open the grave
5. See the problem

---

## Expected behavior

Describe what should happen instead.

Example:  
"A grave should be created, store the player's items, and allow the owner to recover them."

---

## Actual behavior

Describe what actually happened.

Example:  
"No grave was created, and the items dropped normally."

---

## Server information

Please complete the following information:

- betterGraveStones version:
- Minecraft version:
- Server software: Paper / Spigot / Purpur / other
- Server build/version:
- Java version:
- Online mode: true / false
- Proxy setup: none / Velocity / BungeeCord / other

---

## Storage type

Which storage backend are you using?

- [ ] YAML storage
- [ ] MySQL/MariaDB storage

If using MySQL/MariaDB:

- Database type/version:
- Is `keepAlive.enabled` enabled?
- Did you migrate from YAML to MySQL?
- Are there any database errors in the console?

---

## Relevant configuration

Paste the relevant parts of your config here.

Please include only the parts related to the issue, for example:

```yml
ownerOnly:
allowOthersToLoot:
captureDrops:
graveBlock:
disabledWorlds:
xpRestoreFraction:
locatorBar:
```

---

## Console errors / logs

Paste any console errors or warnings here.

```text
Paste logs here and share the created link which will be directly copied to the clipboard as soon as you click paste: https://bin.ttt-games.at/
#http://bin.ttt-games.at/view.php?id=xxxxxxxxxxxxx
```

---

## Screenshots or videos

If useful, add screenshots or a short video showing the issue.

---

## Additional plugins

List plugins that may interact with graves, death drops, worlds, entities, protection, permissions, or commands.

Examples:

- WorldGuard
- Lands
- GriefPrevention
- LuckPerms
- Multiverse
- Essentials
- ClearLag
- AntiCheat plugins
- Entity cleanup plugins

---

## Additional context

Add anything else that may help understand the problem.

Examples:

- Does it happen only in one world?
- Does it happen only with OP/admin players?
- Does it happen only after `/reload`?
- Does it happen only after a server restart?
- Does it happen only with MySQL enabled?
