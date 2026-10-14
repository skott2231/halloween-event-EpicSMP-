# 🎃 HalloweenEvent

A Halloween event plugin for Paper servers.
Players hand in **carved pumpkins**, earn **Halloween points**, and get a **Halloween key**
for every 250 pumpkins. Everything is configurable and every text can be changed.

---

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/halloween` or `/hw` | Help | – |
| `/halloween info [player]` | Points, progress to the next key, rank, time left | `halloween.use` (`halloween.info.others` for other players) |
| `/halloween sell` | Opens the hand-in menu | `halloween.sell` |
| `/halloween sellall` | Hands in every carved pumpkin from your inventory | `halloween.sellall` |
| `/halloween top` | Leaderboard | `halloween.use` |
| `/halloween shop` | Spend points on rewards | `halloween.shop` |
| `/halloween daily` | Daily bonus points | `halloween.daily` |
| `/halloween goal` | Community goal progress (the whole server together) | `halloween.use` |
| `/halloween points` | Show your points | `halloween.use` |
| `/halloween admin give/take/set <player> <n>` | Change points | `halloween.admin` |
| `/halloween admin setdelivered <player> <n>` | Set the lifetime pumpkin counter | `halloween.admin` |
| `/halloween admin reset <player>` | Reset a player | `halloween.admin` |
| `/halloween admin synckeys <player>` | Give keys that are due but were never delivered | `halloween.admin` |
| `/halloween admin event <on\|off>` | Switch the event on or off | `halloween.admin` |
| `/halloween admin reload` | Reload `config.yml` and `messages.yml` | `halloween.admin` |

All player permissions are `true` by default, the admin ones are OP only.

## Features

- **Hand-in menu**: put pumpkins in, see live how many points and keys you get, click CONFIRM.
  Closing the menu any other way gives everything back.
- **1 point per pumpkin**, **1 key per 250 pumpkins** (runs `keys give %player% halloween 1`).
  Several keys at once work too (hand in 600 pumpkins = 2 keys).
- **Shop** where points are spent. Spending points never costs key progress.
- **Milestones**: personal bonus rewards at 25 / 100 / 500 pumpkins (you can add more).
- **Community goals**: when the whole server reaches a number, everyone online gets a reward.
- **Daily bonus**, **leaderboard**, **event dates** (start and end), **join reminder**.
- **Effects**: sounds, a big title and a harmless firework when a key is earned.
- **Protection**: renamed or custom items are not accepted, items are never lost when the menu closes.
- **PlaceholderAPI** support (optional), see below.

## Build the jar

Pick ONE way:

**A) GitHub, nothing to install (easiest)**
1. Create a free GitHub account and a new repository.
2. Upload everything from this folder (including the hidden `.github` folder).
   If the web page skips `.github`, create the file `.github/workflows/build.yml` by hand and paste the content.
3. Open the **Actions** tab, run **Build plugin**, wait about a minute.
4. Open the finished run and download the artifact **HalloweenEvent**. The jar is inside.

**B) Maven on your PC**
1. Install JDK 21 and Maven.
2. In this folder run: `mvn package`
3. The jar is `target/HalloweenEvent-1.0.0.jar`.

**C) IntelliJ IDEA**
Open the folder as a Maven project, then **Maven → Lifecycle → package**.

## Install

1. Put the jar in `plugins/` and restart the server.
2. Edit `plugins/HalloweenEvent/config.yml` (rewards, shop, dates) and `messages.yml` (texts).
3. Check the key command in `config.yml → key-reward.commands`. It must work in your console.
4. `/halloween admin reload` applies changes without a restart.

Requires Paper 1.21+ and Java 21.

## Placeholders (needs PlaceholderAPI)

Use them in TAB, scoreboards and holograms:

| Placeholder | Meaning |
|---|---|
| `%halloween_points%` | spendable points |
| `%halloween_delivered%` | pumpkins handed in (lifetime) |
| `%halloween_keys%` | keys earned |
| `%halloween_next_key%` | pumpkins missing for the next key |
| `%halloween_progress%` / `%halloween_progress_max%` | progress inside the current 250 |
| `%halloween_rank%` | leaderboard position |
| `%halloween_global%` | pumpkins handed in by the whole server |
| `%halloween_time_left%` | time until the event ends |
| `%halloween_active%` | `true` / `false` |
| `%halloween_top_1_name%`, `%halloween_top_1_value%` | leaderboard (any place number) |

Example TAB scoreboard line:
```
- ' &#FF7518🎃 &fPumpkins&7: &#FF7518&l%halloween_delivered% &8(%halloween_next_key% to key)'
```

## Files

```
src/main/java/com/epicsmp/halloween/
  HalloweenPlugin.java      starts everything
  HalloweenService.java     the rules: points, keys, milestones, goals, daily
  SellGui.java              hand-in menu
  ShopGui.java              point shop
  HalloweenCommand.java     /halloween ...
  HalloweenExpansion.java   PlaceholderAPI placeholders
  DataManager.java          saves data.yml
  Messages.java             reads messages.yml
  GuiUtil.java              builds menu icons
src/main/resources/
  config.yml                rewards, shop, dates, effects
  messages.yml              every text
  plugin.yml                commands and permissions
```

## Troubleshooting

- **No key arrives**: look at the console. If the key command was refused you will see
  `The key command failed`. Fix the command in `config.yml`, then run `/halloween admin synckeys <player>`.
- **"Unknown item" in the console**: a name in `pumpkins.accepted` is not a valid item name.
- **Texts look wrong**: a line in `messages.yml` is missing a quote. Compare it with its neighbours.
- **Menu does not open**: the event dates in `config.yml` do not include today, or the event is switched off (`/halloween admin event on`).
