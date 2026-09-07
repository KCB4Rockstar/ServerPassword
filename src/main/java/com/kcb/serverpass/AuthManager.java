package com.kcb.serverpass;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which online players have not yet logged in this session, and where
 * they were standing when they joined so they can be snapped back in place.
 * Nothing here is persisted - every player has to log in again each time they join.
 */
public class AuthManager {
	public record FrozenPos(ServerLevel level, double x, double y, double z, float yaw, float pitch, long joinTick,
							 GameType originalGameMode) {
	}

	private final Set<UUID> unauthenticated = ConcurrentHashMap.newKeySet();
	private final Map<UUID, FrozenPos> frozenPositions = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();

	public boolean isAuthenticated(UUID uuid) {
		return !unauthenticated.contains(uuid);
	}

	public void markUnauthenticated(UUID uuid, FrozenPos pos) {
		unauthenticated.add(uuid);
		frozenPositions.put(uuid, pos);
	}

	public void markAuthenticated(UUID uuid) {
		unauthenticated.remove(uuid);
		frozenPositions.remove(uuid);
		failedAttempts.remove(uuid);
	}

	public void forget(UUID uuid) {
		unauthenticated.remove(uuid);
		frozenPositions.remove(uuid);
		failedAttempts.remove(uuid);
	}

	public FrozenPos getFrozenPos(UUID uuid) {
		return frozenPositions.get(uuid);
	}

	public int registerFailedAttempt(UUID uuid) {
		return failedAttempts.merge(uuid, 1, Integer::sum);
	}

	/**
	 * The only command an unauthenticated player is allowed to run.
	 */
	public boolean isCommandAllowed(String command) {
		String root = command.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT);
		return root.equals("login");
	}
}
