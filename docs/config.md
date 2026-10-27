# Clan config reference

The plugin reads its tasks from a JSON file hosted by the clan, for example as a secret GitHub Gist (use the URL without a commit hash, so it always points at the latest version). Players paste that URL into the plugin settings.

```json
{
  "clanName": "Slay Clan",
  "pointsWebhookUrl": "https://discord.com/api/webhooks/<id>/<token>",
  "tasks": {
    "Social": [ ... ],
    "PvM": [ ... ]
  }
}
```

| Field | Meaning |
|---|---|
| `clanName` | The exact in-game clan name. Players logged into another clan never post |
| `pointsWebhookUrl` | The Discord webhook for task and drop posts, the ones that give points. Only `discord.com` and `discordapp.com` webhooks are accepted |
| `activityWebhookUrl` | Optional. A second Discord webhook for the activity feed: every new collection log item and combat achievement, progress status, and full snapshots for the members hub. Leave it out to turn the feed off |
| `pbWebhookUrl` | Optional. A third Discord webhook for personal bests. Leave it out to turn PB posts off |
| `tasks` | Either a plain list of tasks, or an object grouping tasks by tier. In the grouped form a task without its own `tier` uses the group name |
| `ignoredContent` | Optional. Boss and raid names the plugin must stay silent about, for example `["Fractured Archive"]`. The name is matched against the kill count or completion message, so it silences every `KILL:` and `RAID:` task, every `CONTRACT:` task and every personal best for that content; `CA:`, `CLOG` and `DELVE` tasks are not affected, since their messages do not name the boss. Names match like triggers, ignoring case and apostrophes, and a trailing `*` matches every name starting with the rest, so `Chambers of Xeric*` also covers Challenge Mode. A single name may be written without brackets. Use it to switch new content off within minutes when its game messages turn out to post things they should not, such as a practice mode, until a plugin release handles it |

The plugin refreshes the file at login, every 10 minutes and when its settings change. Lines starting with `//` are ignored, but most JSON editors flag them as errors, so the grouped form is the better way to organise tasks.

## Task fields

| Field | Meaning |
|---|---|
| `id` | Unique identifier, required. The whole config is rejected when an id is missing or used twice. One-time tasks are remembered by this id, so do not reuse or rename ids |
| `name` | Shown in the Discord post |
| `trigger` | What completes the task, see below |
| `tier` | Shown in the Discord post. Defaults to the group name |
| `points` | Shown in the Discord post |
| `once` | `true` posts the task at most once per account. Use it for every task that is not repeatable |
| `minPlayers` | Minimum number of players for raid and boss tasks. Defaults to 1. Outside raids this counts everyone around you, so only use it with `clanOnly` |
| `clanOnly` | Everyone in the party must be a clan member, see below. Defaults to `false` |

## Triggers

Names in triggers and item lists ignore case and apostrophes. A trigger or item ending in `*` matches everything starting with it, so `RAID:*` covers every raid. Several triggers can be combined with `|`, for example `KILL:TzKal-Zuk|KILL:Sol Heredit`.

| Trigger | Fires when | Extra fields |
|---|---|---|
| `RAID:<raid>` | A raid is completed, for example `RAID:Chambers of Xeric`, `RAID:Chambers of Xeric Challenge Mode`, `RAID:Theatre of Blood`, `RAID:Theatre of Blood: Hard Mode`, `RAID:Tombs of Amascut`, `RAID:Tombs of Amascut: Expert Mode` or `RAID:Fractured Archive` | `minPlayers`, `maxTime`, `timeType`, `teamSize`, `minKillCount` |
| `KILL:<boss>` | The game prints a kill count for that boss, for example `KILL:Yama` or `KILL:Vardorvis (Awakened)` | `minPlayers`, `maxTime`, `timeType`, `teamSize`, `minKillCount` |
| `CA:<task>` | A combat achievement is completed, for example `CA:Perfect Olm (Trio)`. The game only announces this once. `CA:#<id>` matches the game's own id instead, which the plugin logs at each completion, so `CA:Perfect Olm (Trio)|CA:#512` survives a rename | `clanOnly` |
| `CLOG` | A new item is added to the collection log | `items`, `required` |
| `LOOT` | The Loot Tracker reports one of the items | `items`, `sources` |
| `DROP` | A single item worth at least `minValue` is dropped | `minValue`, `valuePerPoint`, `includeStackable` |
| `PET_DUPLICATE` | The game says you would have been followed by a pet you already own | |
| `DELVE` | A Doom of Mokhaiotl delve level is completed | `minLevel` |
| `CONTRACT:<contract>` | A Yama contract is used and Yama dies without the player dying or leaving the instance, within 15 minutes. `CONTRACT:*` matches any contract | |
| `EQUIP` | The listed items are worn | `items`, `required` |
| `HOLD` | The listed items are worn or in the inventory | `items`, `required` |
| `XP:<skill>` | Experience in that skill reaches `minXp`, for example `XP:Slayer` | `minXp` |
| `CLOG_COUNT` | The collection log reaches `minCount` slots | `minCount` |

## Extra fields

| Field | Meaning |
|---|---|
| `maxTime` | Only counts when the time is at or under this, for example `"1:00"` |
| `timeType` | `"total"` (default) uses the total completion time in ToB, ToA and the Fractured Archive, `"challenge"` uses the time inside the rooms in ToB and ToA. The Fractured Archive prints only a total time. Other content prints a single time, which is used either way |
| `teamSize` | Only counts with exactly this many players |
| `minKillCount` | Only counts from this kill count on, for example `2` for a second Infernal cape |
| `clanOnly` | For `RAID:`, `KILL:` and `CA:` tasks. `true` requires everyone in the party, or everyone around you outside raids, to be a clan member. Otherwise being in the clan yourself is enough |
| `items` | Item names for `CLOG`, `LOOT`, `EQUIP` and `HOLD` |
| `required` | How many different items from `items` are needed. Defaults to 1, meaning any one of them. A one-time task (`once: true`) needing more than one is also judged on the whole collection log each time the player opens it, so items owned before the plugin was installed count, which is how `Pet Hoarder` is detected. A repeatable task is only judged per new item, since the whole log would complete it again at every opening |
| `sources` | For `LOOT`. Loot sources that count, as named by the Loot Tracker. Leave out to accept any source |
| `minValue` | For `DROP`. Minimum GE value of a single item in gp |
| `valuePerPoint` | For `DROP`. Points are the item value divided by this, rounded to the nearest whole number with .5 rounding up, so 4.4M gives 4 and 4.5M gives 5 points |
| `includeStackable` | For `DROP`. `true` also counts stackable items, valued as the whole stack. Defaults to `false` |
| `minLevel` | For `DELVE`. Minimum delve level |
| `minXp` | For `XP:`. Experience needed |
| `minCount` | For `CLOG_COUNT`. Collection log slots needed |

## Activity feed and personal bests

With `activityWebhookUrl` set, the plugin posts to that webhook: one post per new collection log item (`clog`), one per combat achievement (`ca`), a `status` post whenever progress changes (combat achievement counts per tier, points and tier status, collection log slots in total and per category), a `ca-snapshot` with every completed combat achievement as a JSON file, and a `clog-snapshot` with every owned item when the player opens their collection log. With `pbWebhookUrl` set, new personal bests are posted with a screenshot, every boss kill faster than the plugin has seen for that team size is posted the same way (the game keeps one PB per boss, the plugin keeps one per scale), a member's current best is reported once per boss (once per raid and login, since the game keeps raid times per team size), and a `pb-snapshot` carries every personal best RuneLite has stored for the account. Status and snapshots are checked at every login, including world hops, but only sent when their content differs from what Discord last accepted for that account, so a quiet account produces nothing. Personal best posts carry the team size and, in a `Party` field, the clan members in the team, so the bot can seed one leaderboard row per member; nobody outside the clan is ever named. Every post names the kind and a unique id in its footer, `slayclan:<kind>:<nonce>`, and carries an `Account` field with the RSN.

## How group tasks are checked

Tasks with `clanOnly` require that everyone in the party is a clan member. Raids read the whole party, including members who died or stand far away: Chambers of Xeric from the raid side panel, Theatre of Blood, Tombs of Amascut and the Fractured Archive from the party list. When the party cannot be read, the players currently visible are used. Chambers of Xeric additionally compares the names with the team size from the completion message, and the Fractured Archive with the party slots the game keeps; fewer names than that and nothing is posted, since the missing players cannot be checked against the clan. Outside raids there is no party, so every player around you counts. A `clanOnly` boss task therefore only works at instanced bosses. A raid or boss task done in an instance also names the clan members in the team in a `Party` field, so staff see who was there; outsiders are never named, and nobody is named outside an instance, where the players around are bystanders. Each player with the plugin still posts their own completion under their Discord name, so a raid with two plugin users produces two posts, one per person.

Loot from other players (PvP) and from loot keys is never counted for `DROP` or `LOOT` tasks.

## Thresholds and equipment

The first time the plugin checks an `EQUIP`, `HOLD`, `XP:` or `CLOG_COUNT` task for an account, it only records whether the condition is already met. Players who already reached it before installing the plugin get no automatic post and submit those tasks manually. These tasks are always posted at most once.
