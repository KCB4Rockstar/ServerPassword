package com.kcb.serverpassword;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, on disk, the gamemode a locked player should be restored to once
 * they log in. This has to survive a disconnect (or a server crash) that
 * happens before the player ever logs in - otherwise the spectator mode this
 * mod applies while locked could get saved as their "real" gamemode, and the
 * next time they actually log in successfully we'd have nothing correct left
 * to restore them to.
 *
 * Once written for a username, an entry is treated as authoritative and is
 * only cleared on a confirmed successful login - never just from a
 * disconnect - so a player who repeatedly disconnects without ever logging
 * in can't have their true original gamemode overwritten by the spectator
 * state this mod put them in.
 */
public class PendingGameModeStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("serverpassword-pending-gamemode.json");
	private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	private final Map<String, String> pending = new ConcurrentHashMap<>();

	public static PendingGameModeStore load() {
		PendingGameModeStore store = new PendingGameModeStore();

		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
				if (loaded != null) {
					store.pending.putAll(loaded);
				}
			} catch (IOException e) {
				ServerPasswordMod.LOGGER.error("Failed to read pending gamemode recovery data, starting empty", e);
			}
		}

		return store;
	}

	private void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(pending, writer);
			}
		} catch (IOException e) {
			ServerPasswordMod.LOGGER.error("Failed to save pending gamemode recovery data", e);
		}
	}

	/**
	 * Call when a player is about to be locked into spectator mode. If a prior
	 * session already recorded a pending original gamemode for this username,
	 * that value is authoritative and is returned unchanged. Otherwise the
	 * player's current live gamemode is their true original, and gets recorded.
	 */
	public GameType resolveOriginal(String username, GameType currentLiveGameMode) {
		String key = key(username);
		String existing = pending.get(key);
		if (existing != null) {
			GameType parsed = parse(existing);
			if (parsed != null) {
				return parsed;
			}
		}

		pending.put(key, currentLiveGameMode.name());
		save();
		return currentLiveGameMode;
	}

	/**
	 * Call once a player has been successfully logged in and restored - clears
	 * the recovery record so future joins just track their live gamemode again.
	 */
	public void clear(String username) {
		if (pending.remove(key(username)) != null) {
			save();
		}
	}

	private static GameType parse(String name) {
		try {
			return GameType.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String key(String username) {
		return username.toLowerCase(Locale.ROOT);
	}
}
