# Contributing to betterGraveStones

Thank you for your interest in improving **betterGraveStones**.

This project is publicly visible for transparency, issue tracking, and community feedback.  
However, it is currently published as **All Rights Reserved / No License**. This means the source code is not licensed as open source. Contributions may be accepted, but redistribution, reuploads, forks published as separate plugins, or commercial use are not permitted without explicit permission from the author.

By submitting an issue, pull request, suggestion, or code contribution, you agree that the contribution may be used in this project.

---

## Ways to contribute

You can help by:

- Reporting bugs
- Suggesting improvements
- Testing new releases
- Improving documentation
- Submitting small, focused pull requests
- Sharing compatibility information for Paper/Spigot/Purpur versions
- Reporting MySQL, YAML storage, Locator Bar, or migration issues

---

## Before opening an issue

Please check the following first:

1. You are using the latest available version of betterGraveStones.
2. You restarted the server fully after updating.
3. You are not using `/reload` or plugin managers such as PlugMan to reload the plugin.
4. Only one betterGraveStones `.jar` file exists in the `plugins/` folder.
5. Your configuration files are valid YAML.
6. Any console errors are included in your report.

For bugs, please use the **Bug report** issue template.

For feature ideas, please use the **Feature request** issue template.

---

## Bug reports

A good bug report should include:

- betterGraveStones version
- Minecraft version
- Server software and build number
- Java version
- Storage backend: YAML or MySQL/MariaDB
- Relevant configuration sections
- Full console error or stack trace
- Steps to reproduce the issue
- What you expected to happen
- What actually happened
- List of plugins that may interact with graves, entities, drops, worlds, permissions, or commands

Please avoid screenshots of console logs if possible.  
Text logs are much easier to search and debug.

---

## Feature requests

Feature requests should explain:

- What problem the feature solves
- How the feature should behave
- Whether it needs new config options
- Whether it needs new commands or permissions
- How it should interact with YAML/MySQL storage
- How it should interact with existing grave protection and Locator Bar behavior

Please keep feature requests realistic and focused.  
Large feature ideas may be split into multiple smaller tasks.

---

## Pull requests

Pull requests are welcome when they are focused and easy to review.

Please follow these guidelines:

- Keep changes small and related to one topic.
- Do not mix unrelated bug fixes and feature additions.
- Test your changes on a real Paper server when possible.
- Do not change package names unless discussed first.
- Do not reformat the entire project in one PR.
- Keep configuration changes backward-compatible when possible.
- Update `config.yml`, `messages.yml`, or documentation if your change adds new options or messages.
- Avoid hardcoding user-facing messages. Use `messages.yml` where possible.
- Avoid breaking existing YAML or MySQL grave data.

---

## Code style

The project currently uses plain Java with a simple Bukkit/Paper plugin structure.

General expectations:

- Prefer clear, readable code over clever code.
- Use descriptive method and variable names.
- Keep Bukkit API calls on the main server thread when required.
- Avoid blocking database or file operations on the main thread.
- Validate external input from commands and config files.
- Keep storage logic separated from command and listener logic where possible.
- Do not spam the console unless a debug option exists.
- Preserve existing behavior unless the PR clearly documents the change.

---

## Storage-related changes

Storage changes must be handled carefully.

When working on YAML or MySQL storage:

- Do not silently delete existing grave data.
- Keep migrations safe and repeatable.
- Make sure YAML fallback still works if MySQL is unavailable.
- Make sure MySQL reconnect/keep-alive behavior remains stable.
- Avoid changing stored data formats without a migration path.
- Test both YAML and MySQL storage when possible.

---

## Locator Bar changes

The Locator Bar integration is optional and depends on supported Minecraft/Paper versions.

When changing Locator Bar behavior:

- Keep the feature optional.
- Do not assume all servers support vanilla waypoint features.
- Keep privacy behavior in mind.
- Do not expose other players' graves unintentionally.
- Avoid excessive command output or console spam.
- Keep reserved grave colors separate from player colors.

---

## Commit messages

Please use clear commit messages.

Good examples:

```text
Fix MySQL reconnect handling after idle timeout
Add page-aware tab completion for grave lists
Improve Locator Bar waypoint privacy handling
Update README with build instructions
```

Avoid vague messages like:

```text
fix
update
stuff
changes
```

---

## Local build

If Maven support is available in the repository, build the plugin with:

```bash
mvn clean package
```

The compiled jar should be created in:

```text
target/
```

Use the generated `.jar` on a test server before opening a pull request.

---

## Testing checklist

Before submitting a pull request, test what applies:

- Server starts without errors
- Plugin enables cleanly
- Graves are created on death
- Items and XP are stored/restored correctly
- `/graves list` works
- `/graves tp <player> <id>` works where permitted
- `/graves emergency` works only for configured emergency players
- YAML storage works
- MySQL storage works, if changed
- Migration from YAML to MySQL works, if changed
- Locator Bar features work, if changed
- Permissions behave as expected
- No unnecessary console spam appears

---

## Questions

If you are unsure whether a change fits the project, open an issue first and describe the idea before spending time on a large pull request.
