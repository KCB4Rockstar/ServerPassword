# ServerPass

A server-side Fabric mod for Minecraft 26.1.x/26.2 that locks the server
behind a shared password. Meant for `online-mode=false` (cracked) servers
that want more than an open door but don't want to manage a whitelist.

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
password with `/ppass`, in which case only that works.

Login isn't remembered between server restarts, but a quick reconnect is:
if a logged-in player disconnects and reconnects from the same IP address
within `reconnectGraceSeconds` (10 minutes by default), they're let straight
back in without having to log in again.

No client-side mod is needed. This only goes on the server.

## Building

```bash
./gradlew build
```

Output jar: `build/libs/serverpass-1.4.0+26.1.2.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/server/) 0.19.5+ for your server's Minecraft version.
2. Get a matching [Fabric API](https://modrinth.com/mod/fabric-api/versions) build for that exact version and put it in `mods/`.
3. Put `serverpass-1.4.0+26.1.2.jar` in `mods/` too - it works unmodified on 26.1, 26.1.1, 26.1.2, and 26.2 (every API it touches was checked identical across all four).
4. Start the server once to generate `config/serverpass/config.json`, then set a real password in it.
5. Restart, or run `/serverpass reload` to pick up the change without restarting.

## Configuration (`config/serverpass/config.json`)

```json
{
  "password": "changeme",
  "enabled": true,
  "maxLoginAttempts": 5,
  "reminderIntervalTicks": 100,
  "loginTimeoutSeconds": 60,
  "reconnectGraceSeconds": 600
}
```

- `password`: the shared server password, stored in plain text. Change it from the default.
- `enabled`: set to `false` to turn the lock off without removing the mod.
- `maxLoginAttempts`: kick after this many wrong guesses in one session (`0` disables kicking).
- `reminderIntervalTicks`: how often, in ticks, a locked player is reminded to log in.
- `loginTimeoutSeconds`: kick a locked player after this many seconds (`0` disables the timeout).
- `reconnectGraceSeconds`: skip the login prompt if a player reconnects from the
  same IP within this many seconds of disconnecting while logged in (`0` disables this,
  always requiring login).

Two other files live alongside it in `config/serverpass/`, both managed by the
mod and not meant to be hand-edited:

- `players.json`: personal passwords (hashed with SHA-256, keyed by username),
  and the IP/timestamp used for the reconnect grace period.
- `pending-gamemode.json`: tracks what gamemode a locked player should be
  restored to on login.

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
- `/serverpass set <password>`: op-only. Changes the shared password.
- `/serverpass reload`: op-only. Reloads the config from disk.

## Notes

- Ops aren't exempt. The lock is based on login state, not permission level,
  so an unlogged-in op is frozen and blocked like anyone else, and gets
  kicked by the timeout too. Console and RCON commands aren't affected,
  since those aren't player connections.
- The reconnect grace period only applies after a real successful login -
  disconnecting while still locked out doesn't start it, so it can't be used
  to skip logging in the first time.
- It checks the connecting IP only, not the username tied to it, so it won't
  help someone who's spoofing a different player's name from the same
  network - they'd still need that account's actual password.
- Sneaking and sprinting still work while locked. They're not a security
  concern on their own.
- Turning the mod off with players still mid-login won't pull them out of
  spectator mode automatically; they still need to `/login` once.
- 26.1 is the first Minecraft version shipped without obfuscation, so this
  branch is built against Mojang's real, unobfuscated names directly - there
  are no mappings involved at all. See the `main` branch for 1.21.11, the
  last version that needed them.
