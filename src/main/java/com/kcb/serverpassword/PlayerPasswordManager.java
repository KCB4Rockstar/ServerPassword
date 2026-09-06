package com.kcb.serverpassword;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional per-player passwords, keyed by lowercase username (this is an
 * offline/cracked server, so usernames are the stable identity - not
 * necessarily a Mojang account UUID). Stored hashed, never in plain text.
 */
public class PlayerPasswordManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("serverpassword-players.json");
	private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	private final Map<String, String> hashedPasswords = new ConcurrentHashMap<>();

	public static PlayerPasswordManager load() {
		PlayerPasswordManager manager = new PlayerPasswordManager();

		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
				if (loaded != null) {
					manager.hashedPasswords.putAll(loaded);
				}
			} catch (IOException e) {
				ServerPasswordMod.LOGGER.error("Failed to read player passwords, starting empty", e);
			}
		}

		return manager;
	}

	private void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(hashedPasswords, writer);
			}
		} catch (IOException e) {
			ServerPasswordMod.LOGGER.error("Failed to save player passwords", e);
		}
	}

	public boolean hasPassword(String username) {
		return hashedPasswords.containsKey(key(username));
	}

	public boolean checkPassword(String username, String attempt) {
		String hash = hashedPasswords.get(key(username));
		return hash != null && hash.equals(hash(attempt));
	}

	public void setPassword(String username, String password) {
		hashedPasswords.put(key(username), hash(password));
		save();
	}

	/**
	 * @return true if a password was actually removed
	 */
	public boolean removePassword(String username) {
		boolean removed = hashedPasswords.remove(key(username)) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public Set<String> getKnownUsernames() {
		return hashedPasswords.keySet();
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
