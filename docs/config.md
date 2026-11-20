# Clan config reference

The plugin reads its tasks from a JSON file hosted by the clan, for example as a secret GitHub Gist (use the URL without a commit hash, so it always points at the latest version). Players paste that URL into the plugin settings, together with the address of the clan's bot in **Clan bot URL**. The bot address is never read from this file, so staff hand out both links.

```json
{
  "clanName": "Slay Clan",
  "tasks": {
    "Social": [ ... ],
    "PvM": [ ... ]
  }
}
```

| Field | Meaning |
|---|---|
| `clanName` | The exact in-game clan name. Players logged into another clan never post |
| `tasks` | Either a plain list of tasks, or an object grouping tasks by tier. In the grouped form a task without its own `tier` uses the group name |
| `ignoredContent` | Optional. Boss and raid names the plugin must stay silent about, for example `["Fractured Archive"]`. The name is matched against the kill count or completion message, so it silences every `KILL:` and `RAID:` task, every `CONTRACT:` task and every personal best for that content; `CA:`, `CLOG` and `DELVE` tasks are not affected, since their messages do not name the boss. Names match like triggers, ignoring case and apostrophes, and a trailing `*` matches every name starting with the rest, so `Chambers of Xeric*` also covers Challenge Mode. A single name may be written without brackets. Use it to switch new content off within minutes when its game messages turn out to post things they should not, such as a practice mode, until a plugin release handles it |
| `pbSeasonStart` | Optional. The first day of the current PB season as `yyyy-mm-dd`, the same date staff set in the bot with `/config pb season-start`. The plugin reports a kill when it is the member's fastest at that team size since they installed the plugin; with a season set, that memory starts over at the season, so the first kill of a season is reported even when an earlier season was faster. Any change of the value starts the memory over again, so set it once per season and put an empty string between seasons, which goes back to the memory since install. Members pick a change up at their next login or within 10 minutes. The plugin does not check the format, so a mistyped date is simply a season of its own |

The plugin refreshes the file at login, every 10 minutes and when its settings change. Lines starting with `//` are ignored, but most JSON editors flag them as errors, so the grouped form is the better way to organise tasks.

## Task fields

| Field | Meaning |
|---|---|
| `id` | Unique identifier, required. The whole config is rejected when an id is missing or used twice. One-time tasks are remembered by this id, so do not reuse or rename ids |
| `name` | Shown in the Discord post |
| `trigger` | What completes the task, see below |
| `tier` | Shown in the Discord post. Defaults to the group name |
| `points` | Shown in the Discord post |
| `once` | `true` posts the task at most once per account. Use it for every task that is not repeatable. A one-time task is also judged from what the game remembers, see [Tasks done before the plugin](#tasks-done-before-the-plugin) |
| `minPlayers` | Minimum number of players for raid and boss tasks. Defaults to 1. Outside raids this counts everyone around you, so only use it with `clanOnly` |
| `clanOnly` | Everyone in the party must be a clan member, see below. Defaults to `false` |

## Triggers

Names in triggers and item lists ignore case and apostrophes. A trigger or item ending in `*` matches everything starting with it, so `RAID:*` covers every raid. Several triggers can be combined with `|`, for example `KILL:TzKal-Zuk|KILL:Sol Heredit`.

| Trigger | Fires when | Extra fields |
|---|---|---|
| `RAID:<raid>` | A raid is completed, for example `RAID:Chambers of Xeric`, `RAID:Chambers of Xeric Challenge Mode`, `RAID:Theatre of Blood`, `RAID:Theatre of Blood: Hard Mode`, `RAID:Tombs of Amascut`, `RAID:Tombs of Amascut: Expert Mode` or `RAID:Fractured Archive`. A one-time task is also judged from the stored personal bests after login | `minPlayers`, `maxTime`, `timeType`, `teamSize`, `minKillCount` |
| `KILL:<boss>` | The game prints a kill count for that boss, for example `KILL:Yama` or `KILL:Vardorvis (Awakened)`. A one-time task is also judged from the stored personal bests after login | `minPlayers`, `maxTime`, `timeType`, `teamSize`, `minKillCount` |
| `CA:<task>` | A combat achievement is completed, for example `CA:Perfect Olm (Trio)`. The game only announces this once. `CA:#<id>` matches the game's own id instead, which the plugin logs at each completion, so `CA:Perfect Olm (Trio)|CA:#512` survives a rename. A one-time task is also judged from the completed achievements after login | `clanOnly` |
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
| `clanOnly` | For `RAID:`, `KILL:` and `CA:` tasks. `true` requires everyone in the party, or everyone around you outside raids, to be a clan member. Otherwise being in the clan yourself is enough. A clan-only task is only judged live, never from the game's records, since who was in the team cannot be told afterwards |
| `items` | Item names for `CLOG`, `LOOT`, `EQUIP` and `HOLD` |
| `required` | How many different items from `items` are needed. Defaults to 1, meaning any one of them. Every one-time task (`once: true`) is also judged on the whole collection log each time the player opens it, so items owned before the plugin was installed count, which is how `Pet Hoarder` and an already owned cape or pet are detected. A task completed by a single item is posted with that item named. A repeatable task is only judged per new item, since the whole log would complete it again at every opening |
| `sources` | For `LOOT`. Loot sources that count, as named by the Loot Tracker. Leave out to accept any source |
| `minValue` | For `DROP`. Minimum value of a single item in gp, at Jagex's Grand Exchange guide price (the price source the in-game drop broadcast uses, whatever the member set in RuneLite) |
| `valuePerPoint` | For `DROP`. Points are the item value divided by this, rounded to the nearest whole number with .5 rounding up, so 4.4M gives 4 and 4.5M gives 5 points |
| `includeStackable` | For `DROP`. `true` also counts stackable items, valued as the whole stack. Defaults to `false` |
| `minLevel` | For `DELVE`. Minimum delve level |
| `minXp` | For `XP:`. Experience needed |
| `minCount` | For `CLOG_COUNT`. Collection log slots needed |

## Activity feed and personal bests

Besides tasks and drops, the plugin sends the bot an activity feed: one post per new collection log item (`clog`), one per combat achievement (`ca`), a `status` post whenever progress changes (combat achievement counts per tier, points and tier status, collection log slots in total and per category), a `ca-snapshot` with every completed combat achievement, and a `clog-snapshot` with every owned item when the player opens their collection log. New personal bests are posted with a screenshot, every boss kill faster than the plugin has seen for that team size is posted the same way (the game keeps one PB per boss, the plugin keeps one per scale), a member's current best is reported once per boss (once per raid and login, since the game keeps raid times per team size), and a `pb-snapshot` carries every personal best RuneLite has stored for the account. Status and snapshots are checked at every login, including world hops, but only sent when their content differs from what the bot last accepted for that account, so a quiet account produces nothing. Personal best posts carry the team size and, in a `Party` field, the clan members in the team, so the bot can seed one leaderboard row per member; nobody outside the clan is ever named.

## What a post looks like

Every post is a `multipart/form-data` request to the address in the member's **Clan bot URL** setting (https, or http only to `localhost` or `127.0.0.1` for testing) with the header `Authorization: Bearer <clan token>`, a JSON part named `post` and, for tasks, drops and new personal bests, a PNG part named `screenshot`. The JSON carries `kind` (`task`, `drop`, `pb`, `clog`, `ca`, `status`, `clog-snapshot`, `ca-snapshot` or `pb-snapshot`), a `nonce` of 16 hex characters that makes a retry safe, the `account` the post was made on, `at` (when it happened, ISO 8601), `accountHash` (the game's own id for the account, the same after a rename; left out of a post made in the first ticks after login, before the game has reported it), the `fields` of that kind (`Task`, `Tier`, `Points`, `Item`, `Source`, `Party`, `Value in M`, `Boss`, `Time`, `Time type`, `Team size`, `New`, `Stored best` and so on) and, for snapshots, the `entries`. The bot answers with JSON: `status` is `recorded`, `awaiting` (the bot asked the member something in Discord), `duplicate` or `refused`, with a `message` the plugin shows in the game chat. A `refused` answer also carries the bot's `reason`. A refused snapshot or status is sent again the next time it is due. A refused post of a one-time task only closes the task when the reason is that the member already holds it (`you already have it approved.` or `it is already awaiting review.`); any other refusal, such as a name not listed in the clan sheet or a rank too low, reopens the task so it posts again once the member's setup is fixed, at the earliest after the next login (a collection log task from its next new item, or when the log is next opened). A `401` means the token is not accepted: the post is kept, the member is told once per login, and the retries resume when a new token is pasted. A `413` means the screenshot is too large: the post is dropped and the member is told to submit it by hand, since a review without the screenshot is no review; a one-time task is reopened like after any other refusal. A `429` or `5xx` keeps the post for a retry later, and the retry carries the same screenshot. A `404`, a redirect or a host that cannot be reached also keeps the post, and the member is told once per login to check the Clan bot URL; redirects are never followed. Any other `4xx` drops the post and shows the bot's message, since sending it again would fail the same way, and reopens a one-time task in the same way.

The screenshot is the RuneLite window as it was when the task completed. A task judged from the game's own records (the whole collection log, the completed combat achievements, a stored personal best, or a threshold such as worn items, experience or collection log slots) shows whatever the member had open, so the plugin adds a strip under the frame with the icons and names of the items that completed it, or the name of the combat achievement, the stored time or the count when there is no item. At most 8 entries are drawn, followed by how many were left out. Several tasks judged at once are sent one after another, each with its own image. Posts made from something that just happened, such as a kill, a drop or a new collection log item, carry the plain frame.

A post kept for a retry keeps its screenshot as a PNG file in the plugin's data folder (`.runelite/plugin-data/slay-clan/pending-screenshots`), named after the post's `nonce`. The file is deleted when the bot has answered the post or the post is pushed out of the full queue. The folder is shared by every RuneLite client and profile on the computer, so the plugin never deletes a file just because it does not know the post. At startup and whenever a screenshot is kept, files older than a week are removed, and when the folder holds more than 64 MB the oldest files go first. If the file is missing when the post is retried, the post is sent without a screenshot.

## How group tasks are checked

Tasks with `clanOnly` require that everyone in the party is a clan member. Raids read the whole party, including members who died or stand far away: Chambers of Xeric from the raid side panel, Theatre of Blood, Tombs of Amascut and the Fractured Archive from the party list. When the party cannot be read, the players currently visible are used. Chambers of Xeric additionally compares the names with the team size from the completion message, and the Fractured Archive with the party slots the game keeps; fewer names than that and nothing is posted, since the missing players cannot be checked against the clan. Outside raids there is no party, so every player around you counts. A `clanOnly` boss task therefore only works at instanced bosses. A raid or boss task done in an instance also names the clan members in the team in a `Party` field, so staff see who was there; outsiders are never named, and nobody is named outside an instance, where the players around are bystanders. Each player with the plugin still posts their own completion with their own token, so a raid with two plugin users produces two posts, one per person.

Loot from other players (PvP) and from loot keys is never counted for `DROP` or `LOOT` tasks.

## Thresholds and equipment

An `EQUIP`, `HOLD`, `XP:` or `CLOG_COUNT` task is posted as soon as the plugin sees the condition met, also when the player reached it before installing the plugin. These tasks are always posted at most once.

## Tasks done before the plugin

A one-time task is posted for work done before the plugin was installed whenever the game itself remembers it, so a member who installs the plugin late still gets the tasks the clan can verify:

- Collection log tasks (`CLOG`) are judged on the whole log each time the player opens it.
- Combat achievement tasks (`CA:`) are judged on the game's list of completed achievements after login.
- Raid and boss tasks (`RAID:` and `KILL:`) are judged on the personal bests RuneLite stores after login; a stored best proves the kill, and a task with `maxTime` needs the best within it. RuneLite stores the time the game prints without a "total" prefix: the room time in the Theatre of Blood and the Tombs of Amascut, so only a `timeType: "challenge"` task is judged there, the one duration in the Chambers of Xeric, and the total time in the Fractured Archive. A raid best is stored per team size, so a task with `teamSize` is only judged by a best stored for exactly that size; a best stored for a range like "11-15 players" or a boss best, which carries no team size, only matches a task without `teamSize`. A task with `minKillCount` is left to the live run.
- Threshold tasks are posted at the first check, see above.

A `clanOnly` task is never judged this way. Repeatable tasks, `LOOT`, `DROP`, `PET_DUPLICATE`, `DELVE` and `CONTRACT:` tasks come only from the live game.

The judgement runs a few seconds after login, once the game has reported the clan, and the first judgement of an account's achievements or personal bests counts every satisfied task, also one the plugin had already marked done, so a member whose earlier post was refused gets it posted again. The bot refuses a task the member already holds, which the plugin reports once in the chat and then remembers. A task posted or refused earlier in the same login is not posted again. Later judgements only post tasks that are still open.
