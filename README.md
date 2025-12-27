# GraveGuard (Paper 1.21.x)

Gravestone plugin that captures death drops into a "grave" and prevents datapack-style ArmorStand issues (e.g. trident killing marker stands),
because the interaction is done via the placed grave block, not via an entity.

## Current behavior
- On death: drops + dropped XP are captured (configurable) and stored in a grave.
- A configurable grave block is placed (default: `POLISHED_ANDESITE_WALL`) + optional hologram above it.
- Right-click the grave block to loot:
  - If you're the owner: items go directly into your inventory (overflow drops). XP fraction is granted to owner.
  - If you're not the owner:
    - if `allowOthersToLoot=false` => denied
    - else: items are dropped on the ground; optional pickup protection for the opener (`protectDropsForOpener`)
    - optional XP stealing for non-owner (`xpStealable`) as orbs or direct exp

## Commands
- `/graves reload` (op/admin)
- `/graves list` (shows your current graves)

## Build
- Java 21
- `mvn package`
