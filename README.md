# ServerPassword

A server-side-only Fabric mod for Minecraft 1.21.11 that locks the whole server
behind a single shared password, for `online-mode=false` (cracked) servers that
don't want an open-door policy but also don't want to manage a whitelist.

When a player joins:

- They can't move (they're snapped back in place every tick if they try).
- They can't chat.
- They can't run any command except `/login`.
- They can't break/place blocks, use items, or attack/interact with anything.

All of that lifts the moment they run `/login <password>` with the correct
password. Nothing is persisted between joins — everyone has to log in again
each time they connect.

Because the check happens entirely server-side, players don't need to install
anything — this only needs to go on the server.

## Building

```bash
./gradlew build
```

The output jar is `build/libs/serverpassword-1.0.0.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/server/) 0.19.5+ for Minecraft 1.21.11 on your server.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api/version/0.141.1+1.21.11) for 1.21.11 and drop it in `mods/`.
3. Drop `serverpassword-1.0.0.jar` in `mods/` too.
4. Start the server once to generate `config/serverpassword.json`, then edit it and set a real password.
5. Restart, or run `/serverpassword reload` after editing the file while the server is running.

## Configuration (`config/serverpassword.json`)

```json
{
  "password": "changeme",
  "enabled": true,
  "maxLoginAttempts": 5,
  "reminderIntervalTicks": 100
}
```

- `password` — the shared server password. Change this from the default.
- `enabled` — set to `false` to turn the gate off entirely without removing the mod.
- `maxLoginAttempts` — a player is kicked after this many wrong guesses in one session (`0` disables kicking).
- `reminderIntervalTicks` — how often (in ticks, 20 = 1 second) a locked player is reminded to log in.

## Commands

- `/login <password>` — everyone can run this; it's the only command that works before logging in.
- `/serverpassword set <password>` — changes the password and saves it to the config (op/admin only).
- `/serverpassword reload` — reloads the config from disk (op/admin only).

## Notes / known limitations

- Every player has to log in every time they join — there's no per-player registration, since the point is a single shared server password, not per-user accounts.
- Dropping items and sneaking/sprinting toggles aren't blocked while locked, since they aren't a security concern on their own; movement, chat, commands, and world/entity interaction are.
- This targets 1.21.11 specifically, using Mojang's official mappings (Yarn's mapping updates stop after 1.21.11, and the ecosystem has moved to Mojang mappings as the default going forward). Porting past 1.21.11 needs re-checking, since Minecraft removes obfuscation mappings entirely starting with the 26.1 game drop, which changes how mods are built.
