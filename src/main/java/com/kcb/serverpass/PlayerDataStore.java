package com.kcb.serverpass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player data that needs to survive a restart: optional personal
 * passwords (this is an offline/cracked server, so usernames are the stable
 * identity - not necessarily a Mojang account UUID), keyed by lowercase
 * username and stored hashed, never in plain text; and a record of each
 * player's most recent disconnect, used for the reconnect grace period.
 */
public class PlayerDataStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("serverpass").resolve("players.json");

	private record RecentSession(String ip, long disconnectedAt) {
	}

	private static class FileFormat {
		Map<String, String> passwords = new HashMap<>();
		Map<String, RecentSession> recentSessions = new HashMap<>();
	}

	private final Map<String, String> passwords = new ConcurrentHashMap<>();
	private final Map<String, RecentSession> recentSessions = new ConcurrentHashMap<>();

	public static PlayerDataStore load() {
		PlayerDataStore store = new PlayerDataStore();

		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				FileFormat loaded = GSON.fromJson(reader, FileFormat.class);
				if (loaded != null) {
					if (loaded.passwords != null) {
						store.passwords.putAll(loaded.passwords);
					}
					if (loaded.recentSessions != null) {
						store.recentSessions.putAll(loaded.recentSessions);
					}
				}
			} catch (IOException e) {
				ServerPassMod.LOGGER.error("Failed to read player data, starting empty", e);
			}
		}

		return store;
	}

	private void save() {
		try {
			Files.createDirectories(PATH.getParent());
			FileFormat toSave = new FileFormat();
			toSave.passwords = passwords;
			toSave.recentSessions = recentSessions;
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(toSave, writer);
			}
		} catch (IOException e) {
			ServerPassMod.LOGGER.error("Failed to save player data", e);
		}
	}

	// --- personal passwords ---

	public boolean hasPassword(String username) {
		return passwords.containsKey(key(username));
	}

	public boolean checkPassword(String username, String attempt) {
		String hash = passwords.get(key(username));
		return hash != null && hash.equals(hash(attempt));
	}

	public void setPassword(String username, String password) {
		passwords.put(key(username), hash(password));
		save();
	}

	/**
	 * @return true if a password was actually removed
	 */
	public boolean removePassword(String username) {
		boolean removed = passwords.remove(key(username)) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public Set<String> getKnownUsernames() {
		return passwords.keySet();
	}

	// --- reconnect grace period ---

	/**
	 * Call when an authenticated player disconnects, so a quick reconnect from
	 * the same address can skip the login prompt.
	 */
	public void recordDisconnect(String username, String ip) {
		if (ip == null) {
			return;
		}
		recentSessions.put(key(username), new RecentSession(ip, System.currentTimeMillis()));
		save();
	}

	/**
	 * @return true if this username disconnected from this exact address within
	 * the last graceMillis milliseconds
	 */
	public boolean isWithinReconnectGrace(String username, String ip, long graceMillis) {
		if (ip == null || graceMillis <= 0) {
			return false;
		}
		RecentSession session = recentSessions.get(key(username));
		if (session == null || !ip.equals(session.ip())) {
			return false;
		}
		return System.currentTimeMillis() - session.disconnectedAt() <= graceMillis;
	}

	private static String key(String username) {
		return username.toLowerCase(Locale.ROOT);
	}

	private static String hash(String password) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hashBytes.length * 2);
			for (byte b : hashBytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is guaranteed to be available on every JVM.
			throw new IllegalStateException(e);
		}
	}
}
