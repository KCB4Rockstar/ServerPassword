package com.kcb.serverpass;

import com.kcb.serverpass.command.ServerPassCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ServerPassMod implements ModInitializer {
	public static final String MOD_ID = "serverpass";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ServerPassMod instance;

	private final AuthManager authManager = new AuthManager();
	private PlayerDataStore playerDataStore;
	private PendingGameModeStore pendingGameModeStore;
	private ModConfig config;
	private long tickCounter = 0;

	public static ServerPassMod getInstance() {
		return instance;
	}

	public AuthManager getAuthManager() {
		return authManager;
	}

	public PlayerDataStore getPlayerDataStore() {
		return playerDataStore;
	}

	public PendingGameModeStore getPendingGameModeStore() {
		return pendingGameModeStore;
	}

	public ModConfig getConfig() {
		return config;
	}

	public void reloadConfig() {
		config = ModConfig.load();
	}

	@Override
	public void onInitialize() {
		instance = this;
		config = ModConfig.load();
		playerDataStore = PlayerDataStore.load();
		pendingGameModeStore = PendingGameModeStore.load();

		if (config.password == null || config.password.isBlank() || config.password.equals("changeme")) {
			LOGGER.warn("ServerPass is using the default password! Set a real one with /serverpass set <password>, or by editing config/serverpass/config.json");
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!config.enabled) {
				return;
			}

			ServerPlayer player = handler.player;
			String username = player.getGameProfile().name();

			long graceMillis = config.reconnectGraceSeconds * 1000L;
			if (playerDataStore.isWithinReconnectGrace(username, player.getIpAddress(), graceMillis)) {
				player.sendSystemMessage(Component.literal("§eWelcome back! You reconnected quickly enough to skip logging in again."));
				return;
			}

			GameType originalGameMode = pendingGameModeStore.resolveOriginal(username, player.gameMode());

			authManager.markUnauthenticated(player.getUUID(), new AuthManager.FrozenPos(
					player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
					tickCounter, originalGameMode));

			// Spectator mode is what actually satisfies "not affected by anything until
			// logged in": vanilla already makes spectators immune to all damage (fall,
			// fire, drowning, mobs, other players, redstone-triggered traps - it's all
			// the same hurt() path), untargetable by hostile mobs, and exempt from
			// hunger loss - all for free, without us having to special-case each one.
			player.setGameMode(GameType.SPECTATOR);

			player.sendSystemMessage(Component.literal("§eThis server is password protected. Type §6/login <password>§e to continue."));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			UUID uuid = player.getUUID();

			if (config.enabled && authManager.isAuthenticated(uuid)) {
				playerDataStore.recordDisconnect(player.getGameProfile().name(), player.getIpAddress());
			}

			AuthManager.FrozenPos frozen = authManager.getFrozenPos(uuid);
			if (frozen != null) {
				// Best-effort: if this lands before the player's data is saved, their
				// saved gamemode won't incorrectly be "spectator". Not load-bearing for
				// correctness either way - PendingGameModeStore is the real safety net,
				// and is only ever cleared by a confirmed successful login, not this.
				player.setGameMode(frozen.originalGameMode());
			}

			authManager.forget(uuid);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ServerPassCommands.register(dispatcher));

		registerInteractionGuards();

		ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);

		LOGGER.info("ServerPass initialized.");
	}

	private void onEndTick(MinecraftServer server) {
		tickCounter++;
		if (!config.enabled) {
			return;
		}

		boolean sendReminder = config.reminderIntervalTicks > 0 && tickCounter % config.reminderIntervalTicks == 0;
		long timeoutTicks = config.loginTimeoutSeconds * 20L;

		// Copy the player list: disconnecting a player below would otherwise mutate
		// the server's live list while this loop is iterating over it.
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			UUID uuid = player.getUUID();
			if (authManager.isAuthenticated(uuid)) {
				continue;
			}

			AuthManager.FrozenPos frozen = authManager.getFrozenPos(uuid);
			if (frozen == null) {
				continue;
			}

			if (config.loginTimeoutSeconds > 0 && tickCounter - frozen.joinTick() >= timeoutTicks) {
				player.connection.disconnect(Component.literal("You took too long to log in."));
				continue;
			}

			boolean dimensionChanged = player.level() != frozen.level();
			boolean moved = dimensionChanged
					|| player.getX() != frozen.x()
					|| player.getY() != frozen.y()
					|| player.getZ() != frozen.z();

			if (moved) {
				if (dimensionChanged) {
					player.teleportTo(frozen.level(), frozen.x(), frozen.y(), frozen.z(),
							Set.of(), frozen.yaw(), frozen.pitch(), false);
				} else {
					player.connection.teleport(frozen.x(), frozen.y(), frozen.z(), frozen.yaw(), frozen.pitch());
				}
			}

			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0;

			if (sendReminder) {
				player.displayClientMessage(Component.literal("§eLog in with §6/login <password>§e to play."), true);
			}
		}
	}

	private void registerInteractionGuards() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> guard(player));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> guard(player));
		UseItemCallback.EVENT.register((player, level, hand) -> guard(player));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> guard(player));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> guard(player));

		// Belt-and-suspenders on top of the spectator-mode invulnerability: a
		// modified client can't skip a UI restriction that never existed for it in
		// the first place, but this closes the loop server-side regardless.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				return authManager.isAuthenticated(player.getUUID()) || !config.enabled;
			}
			return true;
		});
	}

	private InteractionResult guard(Player player) {
		if (config.enabled && !authManager.isAuthenticated(player.getUUID())) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}
}
