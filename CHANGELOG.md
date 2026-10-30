# Changelog — FlareMessages

## 1.0.1
Small fix update. Nothing to change in your files: just replace the jar.

### Fixes
- **First join commands always run.** The starter kit, rank and other first join commands now run right when the
  new player joins, without waiting for the skin. Before, a player who left in the first 1-2 seconds never got them
  (when he came back he was not a new player anymore).
- **No more double boss bars.** Sending the same announcement (or the same quick broadcast) again while its boss bar
  is still shown now restarts that bar instead of adding a second one.
- **Less memory on busy servers.** Skin faces are removed from memory when a player leaves. The copy on disk stays,
  so when he comes back nothing is downloaded again.
- **Old faces are cleaned up.** On startup the saved faces older than `face.cache-hours` are deleted from the
  `faces` folder.
- **`/fmes about` from the console** writes the permission list right away (the console can't click `[Show list]`).

## 1.0.0
First release of FlareMessages: welcome messages, join / quit messages and automatic announcements,
with hex colors, gradients and the player's skin face right in chat.

### Compatibility
- One jar for Spigot, Paper, Purpur and forks from **1.16.5 to 26.x**, Java 8 or newer.
- On Paper and its forks every message can be clicked and hovered, join and quit messages included.
  On Spigot join and quit messages keep their colors (hex included) but can't be clicked: everything else can.

### Features
- **Welcome message** on every join and a separate **first join** message, plus an announcement to the other
  players when someone joins for the first time.
- **Skin face in chat**: the player's 8x8 face (with the hat layer) next to the welcome lines.
  Premium, SkinsRestorer and offline-mode servers are supported. Skins are downloaded in the background and cached,
  so the server never waits for them.
- **Join / quit formats** by permission and priority (e.g. a VIP format), or hide join / quit messages completely.
- **Automatic announcements** in order or random, with permission and world filters, a minimum player count
  and announcements that are only sent by hand.
- Every message can use **chat, title, action bar, boss bar, sound and commands**, in any combination.
  Empty lines `""` at the start / end of a chat message leave a space above / below it, so it stays apart
  from the rest of the chat.
- Hex colors, gradients, rainbow, click and hover in every text (MiniMessage tags and `&` codes),
  plus `<center>` to center a chat line.
- Placeholders: `{player}`, `{online}`, `{ping}`, `{world}`, `{join_number}`, `{date}`, `{time}` and more,
  plus every PlaceholderAPI placeholder.
- PlaceholderAPI expansion: `%flaremessages_join_number%`, `%flaremessages_face_1%` ... `%flaremessages_face_8%`.
- Commands: `/fmessages` (also `/fmes`, `/fmessage`, `/messages` and `/message`) with `announce`, `broadcast`,
  `preview`, `list`, `face`, `about` and `reload`. They are for staff only (`flaremessages.admin`):
  players don't even see the command, they just receive the messages you set up.
  If another plugin already uses `/messages` or `/message`, `/fmes` always works.
- Everything is set up in the files, no in-game menus: `config.yml`, `welcome.yml`, `announcements.yml`
  and `lang/en.yml`, with explanations inside. Want another language? Copy `lang/en.yml` (e.g. to `lang/it.yml`)
  and set `language: it`.
- `/fmessages reload` tells you the file and the line of any mistake in your files, in plain words.
  A broken file is never overwritten: the plugin uses the default values until you fix it.
- New options and messages are added to your files by themselves in future updates
  (on 1.16.5 - 1.17 they just use their default value, so your files and their comments are never rewritten).
- Missing PlaceholderAPI is downloaded and started automatically (`auto-install` in `config.yml`).
- **`/fmessages about`** (also `version` / `ver`, also from the console): plugin version in green when it is the
  latest or in red "OUTDATED!" with the download link, server version and type, Java, text API, update notifier
  on / off, plugin hooks (PlaceholderAPI, SkinsRestorer) and a clickable list of every permission
  (`/fmessages about permissions`). Permission: `flaremessages.command.version` (op, included in `flaremessages.admin`).
- **Update check**: on startup a new version is written in the console, and admins get a clickable message
  when they join. It reads the public GitHub releases (betas are never offered to stable servers) and keeps
  the answer for 6 hours. Turn it off with `update-check: false` in `config.yml`.

### Anonymous statistics
- TMDStats: plugin and Minecraft version, server software, Java version, player count, language, which modules are on,
  number of announcements and join formats, PlaceholderAPI on / off. No player names, no IPs.
  Turn them off with `stats: false` in `config.yml`, or for every TMDevelopment plugin in `plugins/TMDStats/config.yml`.
- The update check only reads the latest release from GitHub: it sends nothing.
