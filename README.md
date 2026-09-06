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
- They're kicked if they don't log in within 60 seconds (configurable).

All of that lifts the moment they run `/login <password>` with the correct
password — either the shared server password, or their own personal password
if they've set one. Login state itself isn't persisted between joins —
everyone has to log in again each time they connect.

Because the check happens entirely server-side, players don't need to install
anything — this only needs to go on the server.

## Building

```bash
./gradlew build
```

The output jar is `build/libs/serverpassword-1.2.0.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/server/) 0.19.5+ for Minecraft 1.21.11 on your server.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api/version/0.141.1+1.21.11) for 1.21.11 and drop it in `mods/`.
3. Drop `serverpassword-1.2.0.jar` in `mods/` too.
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

## Commands

- `/login <password>` — everyone can run this; it's the only command that works before logging in. Accepts either the server password or the player's own personal password, if they've set one.
- `/ppass <password> <password>` — sets (or replaces) your own personal password; type it twice to confirm. Only works once you're already logged in.
- `/rpass <password>` — removes your personal password. Requires the current password as confirmation.
- `/rppass <username>` — force-clears a player's personal password, e.g. if they've forgotten it (op/admin only). Works even if that player is offline.
- `/serverpassword set <password>` — changes the shared server password and saves it to the config (op/admin only).
- `/serverpassword reload` — reloads the config from disk (op/admin only).

## Notes / known limitations

- Login state itself isn't remembered between joins — everyone has to log in every time they connect, even if they have a personal password set.
- **Ops get no exemption.** The lock checks login state only, never permission level — an op who forgets to log in is frozen, blocked, and will get kicked by the login timeout just like anyone else. A command typed at the server console (or via RCON) isn't gated, since that's not a player connection.
- Sneaking/sprinting toggles aren't blocked while locked, since they aren't a security concern on their own; movement, chat, commands, inventory, and world/entity interaction all are.
- This targets 1.21.11 specifically, using Mojang's official mappings (Yarn's mapping updates stop after 1.21.11, and the ecosystem has moved to Mojang mappings as the default going forward). Porting past 1.21.11 needs re-checking, since Minecraft removes obfuscation mappings entirely starting with the 26.1 game drop, which changes how mods are built.
