# ServerPassword

A server-side Fabric mod for Minecraft 1.21.11 that locks the server behind a
shared password. Meant for `online-mode=false` (cracked) servers that want
more than an open door but don't want to manage a whitelist.

When a player joins, until they log in:

- They can't move. They're snapped back in place if they try.
- They can't chat or run any command except `/login`.
- They can't interact with blocks, items, entities, or their own inventory.
- They're switched to spectator mode, so they take no damage, lose no
  hunger, and can't be targeted by mobs. If they disconnect mid-fall, they
  just float until they log back in.
- They're kicked if they don't log in within 60 seconds (configurable).

Logging in with the correct password restores their gamemode and unlocks
everything, right where they were standing. The correct password is the
shared server password, unless the account has set its own personal
password with `/ppass`, in which case only that works. Login isn't
remembered between joins, so everyone logs in again each time they connect.

No client-side mod is needed. This only goes on the server.

## Building

```bash
./gradlew build
```

Output jar: `build/libs/serverpassword-1.3.0.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/server/) 0.19.5+ for Minecraft 1.21.11.
2. Get [Fabric API](https://modrinth.com/mod/fabric-api/version/0.141.1+1.21.11) for 1.21.11 and put it in `mods/`.
3. Put `serverpassword-1.3.0.jar` in `mods/` too.
4. Start the server once to generate `config/serverpassword.json`, then set a real password in it.
5. Restart, or run `/serverpassword reload` to pick up the change without restarting.

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

- `password`: the shared server password, stored in plain text. Change it from the default.
- `enabled`: set to `false` to turn the lock off without removing the mod.
- `maxLoginAttempts`: kick after this many wrong guesses in one session (`0` disables kicking).
- `reminderIntervalTicks`: how often, in ticks, a locked player is reminded to log in.
- `loginTimeoutSeconds`: kick a locked player after this many seconds (`0` disables the timeout).

Personal passwords are stored separately in `config/serverpassword-players.json`,
keyed by username and hashed with SHA-256. You shouldn't need to edit this file.

`config/serverpassword-pending-gamemode.json` tracks what gamemode a locked
player should be restored to on login. Also not meant to be edited by hand.

## Commands

- `/login <password>`: works before logging in, and is the only command that
  does. Uses the account's personal password if it has one set, otherwise the
  shared server password.
- `/ppass <password> <password>`: sets your personal password (type it twice
  to confirm). Requires already being logged in. Once set, the shared
  password no longer works for that account, only this one does.
- `/rpass <password>`: removes your personal password, given the current one.
  The account goes back to using the shared password.
- `/rppass <username>`: op-only. Clears a player's personal password, e.g. if
  they forgot it and are locked out. Works on offline players.
- `/serverpassword set <password>`: op-only. Changes the shared password.
- `/serverpassword reload`: op-only. Reloads the config from disk.

## Notes

- Everyone logs in fresh each join, even with a personal password set.
- Ops aren't exempt. The lock is based on login state, not permission level,
  so an unlogged-in op is frozen and blocked like anyone else, and gets
  kicked by the timeout too. Console and RCON commands aren't affected,
  since those aren't player connections.
- Sneaking and sprinting still work while locked. They're not a security
  concern on their own.
- Turning the mod off with players still mid-login won't pull them out of
  spectator mode automatically; they still need to `/login` once.
- Built against Mojang's official mappings for 1.21.11, the last version
  Yarn mappings will be updated for. Porting past 1.21.11 will need a fresh
  look, since Minecraft drops obfuscation mappings entirely starting with
  the 26.1 game drop.
