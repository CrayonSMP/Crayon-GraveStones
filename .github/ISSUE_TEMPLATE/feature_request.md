---
name: Feature request
about: Suggest an idea for this project
title: ''
labels: enhancement, question
assignees: ''

---

## Feature description

Describe the feature or improvement you would like to see.

Example:  
"Add an option to make graves expire automatically after a configurable amount of time."

---

## Problem or use case

What problem does this feature solve?  
Why would it be useful for your server?

Example:  
"On larger servers, old graves can stay around forever and clutter the world or storage."

---

## Suggested solution

Describe how you think the feature should work.

Example:

- Add a config option like `graveExpirationMinutes`
- Automatically remove expired graves
- Optionally notify the owner before removal
- Optionally drop or delete the stored items

---

## Alternative solutions

Have you considered another way to solve this?

Example:

- Manual cleanup command
- Per-world expiration settings
- Admin-only cleanup tool

---

## Configuration idea

If this feature needs configuration, describe your preferred config structure.

```yml
exampleFeature:
  enabled: true
  exampleValue: 10
```

---

## Commands or permissions

Would this feature need new commands or permissions?

Example:

- `/graves cleanup`
- `/graves expire <player>`
- `graves.admin.cleanup`

---

## Server information

Please complete the following information if it is relevant:

- betterGraveStones version:
- Minecraft version:
- Server software: Paper / Spigot / Purpur / other
- Server build/version:
- Storage type: YAML / MySQL/MariaDB
- Proxy setup: none / Velocity / BungeeCord / other

---

## Compatibility concerns

Could this feature interact with other plugins or systems?

Examples:

- Protection plugins
- Economy plugins
- Permission plugins
- World management plugins
- Entity cleanup plugins
- Locator Bar / waypoint behavior

---

## Additional context

Add anything else that may help explain the request.

Examples:

- Screenshots
- Mockups
- Similar plugin behavior
- Server-specific use case
- Why this would improve gameplay or administration
