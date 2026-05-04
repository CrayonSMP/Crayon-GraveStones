# Security Policy

This document explains how to report security issues for **betterGraveStones**.

Please do not publicly disclose security vulnerabilities before they have been reviewed and fixed.

---

## Supported versions

Security support generally applies to the latest public release.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Older releases | Best effort |
| Modified third-party builds | No |

If you are using an older version, please update to the latest release before reporting a security issue unless the vulnerability also affects the current release.

---

## What counts as a security issue?

Please report issues privately if they could allow players or attackers to:

- Bypass grave ownership or looting restrictions
- Access or steal items from protected graves
- Abuse `/graves emergency`
- Abuse `/graves tp` or teleport permissions
- Access graves from other players without permission
- Trigger item duplication or XP duplication
- Delete, corrupt, or overwrite grave data
- Break YAML or MySQL storage integrity
- Leak database credentials or sensitive configuration values
- Execute unauthorized commands
- Crash the server remotely or through normal player actions
- Abuse Locator Bar waypoints to reveal private grave locations
- Bypass configured permissions
- Cause severe console spam or performance degradation intentionally

---

## What is not a security issue?

The following should usually be reported as normal bugs instead:

- Minor visual issues
- Formatting problems in messages
- Documentation mistakes
- Feature requests
- Compatibility issues without a security impact
- Errors caused by unsupported server versions
- Issues caused by `/reload` or plugin managers after replacing the jar
- Problems caused by modified or unofficial plugin builds

---

## Reporting a vulnerability

Please report security issues privately.

Preferred options:

1. Use GitHub's private security advisory/reporting feature if it is enabled for this repository.
2. If private reporting is not available, contact the project maintainer directly through the contact methods listed on the GitHub profile or project page.

Please include as much detail as possible:

- betterGraveStones version
- Minecraft version
- Server software and build number
- Java version
- Storage backend: YAML or MySQL/MariaDB
- Relevant configuration sections
- Steps to reproduce
- Expected behavior
- Actual behavior
- Console logs or stack traces
- Whether the issue works without OP/admin permissions
- Whether the issue requires another plugin
- Whether the issue affects YAML, MySQL, or both
- Any proof-of-concept details needed to reproduce the issue

Please do not include public exploit instructions in an issue, discussion, review, or pull request.

---

## Response expectations

I will try to review valid security reports as soon as possible.

The usual process is:

1. Confirm the report was received.
2. Reproduce and assess the issue.
3. Prepare a fix.
4. Release an updated version.
5. Publicly mention the fix in the changelog if appropriate.

Response times may vary because this is a personal/community project.

---

## Responsible disclosure

Please give reasonable time to investigate and fix the issue before public disclosure.

Do not:

- Publish exploit code publicly before a fix exists
- Use the vulnerability against servers without permission
- Access, copy, delete, or modify data that does not belong to you
- Use testing methods that intentionally damage public servers
- Reupload modified builds that claim to fix the vulnerability without permission

---

## Server owner recommendations

To reduce risk when running betterGraveStones:

- Keep the plugin updated.
- Back up the plugin folder regularly.
- Back up `graves.yml` before changing storage settings.
- Back up the MySQL/MariaDB database before migration.
- Do not update using `/reload` or plugin managers such as PlugMan.
- Use a permission plugin such as LuckPerms for command access.
- Restrict `graves.admin`, teleport, and emergency permissions carefully.
- Do not give emergency access to normal players.
- Use strong MySQL credentials.
- Do not share `MySQL.yml` publicly.
- Keep database users limited to the required database only.
- Test Locator Bar privacy settings before enabling them on public servers.

---

## Scope

This policy applies to the official betterGraveStones project maintained by **SLINIcraftet204**.

It does not apply to:

- Unofficial forks
- Reuploaded jars
- Modified builds
- Server setups using unsupported plugin managers
- Vulnerabilities in Minecraft, Paper, Spigot, Purpur, Java, MySQL, MariaDB, or other third-party software

If a vulnerability is caused by a third-party dependency or server platform, please report it to that project as well.
