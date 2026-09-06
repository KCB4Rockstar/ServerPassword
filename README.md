# ServerPassword

A server-side-only Fabric mod for Minecraft 1.21.11 that locks the whole server
behind a single shared password, for `online-mode=false` (cracked) servers that
don't want an open-door policy but also don't want to manage a whitelist.

When a player joins:

- They can't move (they're snapped back in place every tick if they try).
- They can't chat.
- They can't run any command except `/login`.
- They can't break/place blocks, use items, or attack/interact with anything.
- They can't pick up or drop items, or touch their inventory (including creative-mode edits) at all.
- They're switched to spectator mode, so they can't take any damage (fall, fire, drowning, mobs, other players, redstone-triggered traps — all of it), don't lose hunger, and can't be targeted by hostile mobs. If they were mid-fall when they disconnected, they simply stop falling until they log back in.
- They're kicked if they don't log in within 60 seconds (configurable).

All of that lifts the moment they run `/login <password>` with the correct
password — the shared server password, unless that account has set its own
personal password, in which case only that one works (see `/ppass` below).
Logging in restores their original gamemode from before they were locked,
exactly where they were standing. Login state itself isn't persisted between
joins — everyone has to log in again each time they connect.

Because the check happens entirely server-side, players don't need to install
anything — this only needs to go on the server.

## Building

```bash
./gradlew build
```

The output jar is `build/libs/serverpassword-1.3.0.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/server/) 0.19.5+ for Minecraft 1.21.11 on your server.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api/version/0.141.1+1.21.11) for 1.21.11 and drop it in `mods/`.
3. Drop `serverpassword-1.3.0.jar` in `mods/` too.
4. Start the server once to generate `config/serverpassword.json`, then edit it and set a real password.
5. Restart, or run `/serverpassword reload` after editing the file while the server is running.

## Configuration (`config/serverpassword.json`)

```json
{
  "password": "changeme",
  "enabled": true,
  "maxLoginAttempts": 5,
  "reminderIntervalTicks": 100,
  "loginTimeoutSeconds": 60
}
```

- `password` — the shared server password. Change this from the default. This is stored in plain text, since it's meant to be an admin-edited file.
- `enabled` — set to `false` to turn the gate off entirely without removing the mod.
- `maxLoginAttempts` — a player is kicked after this many wrong guesses in one session (`0` disables kicking).
- `reminderIntervalTicks` — how often (in ticks, 20 = 1 second) a locked player is reminded to log in.
- `loginTimeoutSeconds` — a locked player is kicked if they haven't logged in within this many seconds (`0` disables the timeout).

Personal passwords live separately in `config/serverpassword-players.json`, keyed
by username and stored as SHA-256 hashes (never plain text). You normally
won't need to touch this file directly — use the commands below.

There's also `config/serverpassword-pending-gamemode.json`, which just remembers
what gamemode a locked player should be restored to on login. It's only ever
populated while someone is actually locked out and waiting to log in — don't
edit it.

## Commands

- `/login <password>` — everyone can run this; it's the only command that works before logging in. If the account has a personal password set, **only** that password works; otherwise the shared server password works.
- `/ppass <password> <password>` — sets (or replaces) your own personal password; type it twice to confirm. Only works once you're already logged in. Once set, the shared server password no longer logs that account in — this is what makes a personal password actually protect a username on a cracked/offline server, where anyone could otherwise type in a name that isn't theirs.
- `/rpass <password>` — removes your personal password, reverting the account back to the shared server password. Requires the current password as confirmation.
- `/rppass <username>` — force-clears a player's personal password, e.g. if they've forgotten it and are now locked out (op/admin only). Reverts that account to the shared server password. Works even if that player is offline.
- `/serverpassword set <password>` — changes the shared server password and saves it to the config (op/admin only).
- `/serverpassword reload` — reloads the config from disk (op/admin only).

## Notes / known limitations

- Login state itself isn't remembered between joins — everyone has to log in every time they connect, even if they have a personal password set.
- **Ops get no exemption.** The lock checks login state only, never permission level — an op who forgets to log in is frozen, blocked, and will get kicked by the login timeout just like anyone else. A command typed at the server console (or via RCON) isn't gated, since that's not a player connection.
- Sneaking/sprinting toggles aren't blocked while locked, since they aren't a security concern on their own; movement, chat, commands, inventory, and world/entity interaction all are.
- Turning `enabled` off with `/serverpassword reload` while players are actively locked won't pull them out of spectator mode on its own — they'd still need to `/login` once to get restored, even though the lock itself stops enforcing anything else in the meantime. This is a narrow edge case (toggling the mod off mid-session while people are actively joining) rather than something you'd hit in normal use.
- This targets 1.21.11 specifically, using Mojang's official mappings (Yarn's mapping updates stop after 1.21.11, and the ecosystem has moved to Mojang mappings as the default going forward). Porting past 1.21.11 needs re-checking, since Minecraft removes obfuscation mappings entirely starting with the 26.1 game drop, which changes how mods are built.
