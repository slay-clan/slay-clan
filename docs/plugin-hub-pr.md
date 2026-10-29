# Plugin Hub PR draft

Title: Add Slay Clan

Body:

Slay Clan is a clan plugin that posts completed clan tasks, valuable drops, personal bests and collection log and combat achievement progress to the clan's own bot, which turns them into the approval posts the clan's staff already review by hand today.

Why not a PR to Dink or another webhook plugin: the plugin does not send events, it decides whether a clan task was completed. The tasks, tiers, points, time limits and team sizes live in a JSON config the clan hosts, so staff change them without a plugin release. A task only counts when the whole party is in the clan (read from the raid party list, or the players in the instance), when the kill or raid time is under the task's limit and the team size matches, and one-time tasks are remembered per RuneLite account so they are posted once. Posts go to the clan's bot over HTTPS with a personal token per member, in the bot's own format, and the bot answers at once so the plugin can tell the member in the chat whether the post was recorded, refused and why, or is waiting for an answer in Discord. None of that fits a generic webhook plugin.

Nothing is hard-wired to Slay Clan: the clan name, the bot address and every task come from the config file, so any clan can run it with a config and a bot of its own.

What is sent: the member's token, in-game name, the task details and a screenshot of the RuneLite window, only to the server named in the clan's config. Posting is off by default and the config warning says so.
