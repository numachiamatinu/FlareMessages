<div align="center">

# FlareMessages

**Welcome, join / quit and announcements for Minecraft servers: hex colors, boss bars, titles and the player's skin face in chat.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5%20→%2026.x-56ccf2)
![Platforms](https://img.shields.io/badge/Spigot%20|%20Paper%20|%20Purpur-supported-2f80ed)
![Java](https://img.shields.io/badge/Java-8%2B-orange)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

---

## How it works

Everything is set up in the files, no in-game menus. Players get no commands: they just see the messages
you prepared.

- a player joins for the **first time**: he gets a welcome with his skin face, a title and a boss bar,
  and everyone else sees "New player, say hi!"
- every **next join**: a welcome back message, only for him
- **join / quit** lines in chat with your own format (one for VIPs, one for staff, none at all...)
- every few minutes an **announcement**: Discord, vote, store, tips... in chat, title, action bar or boss bar

## Features

- **Skin face in chat**: the 8x8 face of the player (with the hat layer) next to the welcome lines.
  Works on premium, SkinsRestorer and offline-mode servers. Skins are downloaded in the background and cached
- **Every message can mix chat, title, action bar, boss bar, sound and commands**
- **First join**: message to the new player, announcement to the others, commands run once (starter kit, rank...)
- **Join / quit formats** by permission and priority, or no join / quit message at all
- **Announcements**: in order or random, minimum players online, permission and world filters,
  announcements sent only by hand (`in-rotation: false`) for events and restarts
- **Quick broadcast**: `/fmes broadcast <chat|actionbar|title|bossbar> <text>`
- **Preview** any message only to you, without running its commands
- **Hex colors, gradients, rainbow, click & hover** in every text (MiniMessage and `&` codes), `<center>` for chat lines
- **Placeholders** of the plugin and of PlaceholderAPI, for every player
- **Safe files**: `/fmes reload` tells you the file and the line of a mistake; a broken file is never overwritten
- **Safe updates**: new options and texts are added to your files, your edits are never touched
- **Staff only commands**: players don't even see `/fmessages`

## Compatibility

| | |
|---|---|
| Server | Spigot, Paper, Purpur and other forks |
| Versions | 1.16.5 → 26.x (one jar for all) |
| Java | 8 or newer |
| Optional | PlaceholderAPI (downloaded automatically), SkinsRestorer |

On Spigot join and quit messages can't be clicked or hovered (colors and hex work); everything else can.
On Paper and its forks every message can be clicked.

## Installation

1. Put `FlareMessages-<version>.jar` in `plugins/`
2. Start the server, all files are created automatically
3. Edit `welcome.yml` and `announcements.yml`, then `/fmes reload`

If PlaceholderAPI is missing it is downloaded from its official pages (Hangar, Modrinth, GitHub) and started
without a restart. Turn it off with `config.yml > auto-install > enabled: false`.

## Files

| File | |
|---|---|
| `config.yml` | modules on / off, server name, date and time, boss bars, quick broadcast, skin face |
| `welcome.yml` | join / quit formats, welcome, first join |
| `announcements.yml` | automatic and manual announcements |
| `lang/en.yml` | answers of the commands |

## Commands

Main command `/fmessages`. Also `/fmes`, `/fmessage`, `/messages` and `/message`.

| Command | Description | Permission |
|---|---|---|
| `/fmes help` | Commands you can use | `flaremessages.admin` |
| `/fmes announce <id\|next>` | Send an announcement to everyone now | `flaremessages.admin` |
| `/fmes broadcast <type> <text>` | Quick message: `chat`, `actionbar`, `title` (`title \| subtitle`), `bossbar` | `flaremessages.admin` |
| `/fmes preview <message> [player]` | Show `welcome`, `firstjoin`, `firstjoin-broadcast` or an announcement only to you (or a player) | `flaremessages.admin` |
| `/fmes list` | Every announcement, click one to preview it | `flaremessages.admin` |
| `/fmes face [player]` | Show a skin face and where it comes from | `flaremessages.admin` |
| `/fmes reload` | Reload every file, with the line of any mistake | `flaremessages.admin` |
| `/fmes about` | Plugin version (green = latest, red = outdated), server, Java, update notifier, plugin hooks, clickable permission list (`about permissions`). Also `version` / `ver`, also from the console | `flaremessages.command.version` |

| Permission | Default | |
|---|---|---|
| `flaremessages.admin` | op | every command and the update message on join |
| `flaremessages.command.version` | op | `/fmes about` |
| `flaremessages.format.vip` | nobody | uses the `vip` join / quit format of `welcome.yml` (add your own formats) |

## Placeholders

In the files: `{player}` `{displayname}` `{uuid}` `{world}` `{ping}` `{level}` `{health}` `{gamemode}` `{x}` `{y}` `{z}`
`{online}` `{max_online}` `{server_name}` `{date}` `{time}` `{prefix}` `{join_number}` `{face_1}` ... `{face_8}`,
plus every PlaceholderAPI placeholder.

| PlaceholderAPI | Value |
|---|---|
| `%flaremessages_join_number%` | how many different players joined the server |
| `%flaremessages_face_1%` ... `%flaremessages_face_8%` | one row of the player's skin face |

## Anonymous statistics

This plugin sends anonymous usage statistics to TMDevelopment every 30 minutes:
plugin version, Minecraft version, server software, Java version, CPU core count,
online player count, online/offline mode and a random server id generated on your server.
The country of the server is derived from its IP address (only the country code is kept).
FlareMessages also sends a few anonymous settings of its own: the language of the messages, which modules are on,
the number of announcements and join formats, and whether PlaceholderAPI is installed.
No player names or UUIDs, no IP addresses (they are never stored), no chat, world or config data.
Data of a server is deleted 30 days after its last report; only anonymous totals are kept.
We use it to know which versions to support.
You can turn it off for this plugin with `stats: false` in `config.yml`, or for all TMDevelopment plugins
in `plugins/TMDStats/config.yml` (`enabled: false`).

The update check only reads the latest release of FlareMessages from GitHub: it sends nothing.
Turn it off with `update-check: false` in `config.yml`.

## Building

```
mvn package
```

The jar is in `target/FlareMessages-<version>.jar`.

## Support

Discord: https://discord.tmstudio.dev

## License

MIT, see [LICENSE](LICENSE).
