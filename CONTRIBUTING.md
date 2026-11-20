# Contributing

This guide is for the clan's development group and anyone else who wants to change the plugin. The [README](README.md) explains what the plugin does for members, and [docs/config.md](docs/config.md) is the reference for the clan config and for every post the plugin sends.

## How the pieces fit together

```
 RuneLite + this plugin            clan config (JSON, hosted by the clan)
        |   reads tasks, triggers, times, team sizes every 10 minutes
        |
        |  HTTPS post with the member's token and a screenshot
        v
 clan Discord bot  ------------->  approval post in Discord, staff press Approve
        |
        v
 Google Sheet (the bot's database: members, tasks, points, PB board)
        |
        v
 clan website and Wise Old Man read from there
```

- **The plugin** watches the game for clan tasks, valuable drops, personal bests, collection log and combat achievement progress. It decides whether a task counts (whole party in the clan, time and team size within the task's limits, one-time tasks only once) and posts it. It stores nothing itself apart from which one-time tasks it already posted, the posts waiting for a retry, the fastest kill per boss and team size and the Tombs of Amascut total records, kept in the member's RuneLite profile, and the screenshots of those waiting posts, kept as PNG files in the plugin's data folder until the bot has answered.
- **The clan config** is a JSON file the clan hosts. It lists every task the plugin watches, so staff change tasks without a plugin release. Its task names and tiers must match the bot's task list exactly, since the bot looks a post up by name and tier.
- **The bot** is a separate project with its own maintainer and repository. It checks the token, writes the submission to the sheet, posts the approval message and answers the plugin at once with `recorded`, `awaiting`, `duplicate` or `refused`, which the plugin shows in the game chat. Tokens are created with `/plugin token`, only their SHA-256 hash is stored, and `/plugin revoke` turns one off.
- **The sheet** is the database and also the admin interface for staff, who add tasks, fix rows and sort and filter there.

The post format between plugin and bot is described under "What a post looks like" in [docs/config.md](docs/config.md). A change to that format needs a matching change in the bot, made and reviewed there separately.

## Code layout

All code is under `src/main/java/com/slayclan`, with tests mirroring it under `src/test`.

| Package | What it holds |
|---|---|
| `trigger` | Detectors that turn game messages and widgets into events: kill counts, durations, collection log, combat achievements, contracts |
| `task` | Matching events against the tasks in the clan config, timed and threshold tasks |
| `drop` | Pricing loot and turning valuable drops into posts |
| `raid` | Reading the raid party |
| `clan` | Clan membership checks and which game worlds count |
| `activity` | Collection log, combat achievement and progress snapshots for the activity feed |
| `discord` | Building posts, sending them to the bot, retries and screenshots |
| `config` | Loading and parsing the clan config |
| `progress` | Remembering which one-time tasks were posted |

`SlayClanPlugin` wires it together and holds the RuneLite event subscriptions.

## Building and running

You need Java 11.

```
./gradlew build      # compile and run all tests
./gradlew run        # start a development RuneLite client with the plugin loaded
```

Log in to the development client as described in [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Point **Clan config URL** at a copy of the config and **Clan bot URL** at a test bot, never at the clan's live bot, so nothing you try reaches the real approval channel or the sheet.

Never automate game input to test something. Only a person playing can confirm a change works in game.

## Rules for the code

[AGENTS.md](AGENTS.md) lists the Plugin Hub rules every change must follow: no reflection, no process execution, no class loading, no serialization, no injected input, no new menu entries that send actions to the server, no network or disk work on the client thread, `gameval` constants instead of magic numbers. A plugin that breaks them is rejected by the Plugin Hub review.

On top of that:

- Everything the plugin sends is listed in [docs/config.md](docs/config.md). A change that sends anything new updates that list in the same pull request.
- The plugin only ever talks to the two URLs the member pastes in its settings. `src/main` contains no `http://`, `https://`, `ws://` or `wss://` string at all, and the build fails if one appears.
- No secrets in the repository. The plugin has none: the config file is public and each member's token lives only in their own RuneLite settings.
- Tests come with the change. For a bug, first a test that fails, then the fix.
- One logical change per commit, with a one-line imperative message, for example "Post Hallowed Sepulchre coffin loot as drops".
- Do not mix reformatting with behaviour changes in one commit.

## Pull requests

Every change goes through a pull request against `main`, also the maintainer's own. Direct pushes to `main` are blocked.

1. Create a branch, commit, push, open a pull request. The template asks four questions, answer them.
2. These checks must pass before a merge:
   - **build**, compiles and runs every test, and checks the Gradle wrapper
   - **Plugin Hub rules**, fails on forbidden APIs (list in `.github/forbidden-apis.txt`) and on any URL in `src/main`
   - **CodeQL**, static analysis for security and quality
   - **review** (Dependency review), fails when a new dependency has a known vulnerability of moderate severity or higher
   - **submit** (Dependency submission), sends the runtime dependencies to GitHub's dependency graph
3. One approval from the `developers` team is required, switched on as soon as the first group member has joined the team. CODEOWNERS asks the team automatically. A new push after an approval needs a new approval.
4. Merge with squash. The branch is deleted afterwards.

The Hub rules check is a tripwire for the obvious cases, not a proof. CodeQL and a person reading the diff are the real control, so review the change itself, not just the green checks.

## Releasing to the Plugin Hub

The Plugin Hub builds the plugin from a commit hash listed in [runelite/plugin-hub](https://github.com/runelite/plugin-hub). A release is a pull request there that changes `commit=` in `plugins/slay-clan` to a commit on `main`. The RuneLite team reviews the diff since the last released commit before it reaches members. Changes are collected and released together rather than one hub update per merge.

The Plugin Hub keeps only the repository URL, the commit hash and the compiled jar, not the source. Forks of this repository are its backup.

## Security setup

- Organization owned repository, two-factor authentication required for every member.
- `main` protected by a ruleset: no deletion, no force push, required checks, and one required approval from the `developers` team once its first member has joined. Nobody can bypass it, the maintainer included.
- Secret scanning with push protection, Dependabot alerts and security updates, weekly Dependabot version updates for Gradle and GitHub Actions.
- GitHub Actions pinned to commit hashes, workflow tokens read only unless a job needs more, only GitHub-owned and verified actions allowed, workflows from outside contributors need approval before they run.
- OpenSSF Scorecard runs on every push to `main` and weekly, results at [scorecard.dev](https://scorecard.dev/viewer/?uri=github.com/slay-clan/slay-clan).
- Vulnerabilities are reported privately, see [SECURITY.md](SECURITY.md).
