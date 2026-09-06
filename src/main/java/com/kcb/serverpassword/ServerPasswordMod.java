package com.kcb.serverpassword;

import com.kcb.serverpassword.command.ServerPasswordCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

public class ServerPasswordMod implements ModInitializer {
	public static final String MOD_ID = "serverpassword";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ServerPasswordMod instance;

	private final AuthManager authManager = new AuthManager();
	private ModConfig config;
	private long tickCounter = 0;

	public static ServerPasswordMod getInstance() {
		return instance;
	}

	public AuthManager getAuthManager() {
		return authManager;
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

		if (config.password == null || config.password.isBlank() || config.password.equals("changeme")) {
			LOGGER.warn("ServerPassword is using the default password! Set a real one with /serverpassword set <password>, or by editing config/serverpassword.json");
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!config.enabled) {
				return;
			}

			ServerPlayer player = handler.player;
			authManager.markUnauthenticated(player.getUUID(), new AuthManager.FrozenPos(
					player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

			player.sendSystemMessage(Component.literal("§eThis server is password protected. Type §6/login <password>§e to continue."));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				authManager.forget(handler.player.getUUID()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ServerPasswordCommands.register(dispatcher));

		registerInteractionGuards();

		ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);

		LOGGER.info("ServerPassword initialized.");
	}

	private void onEndTick(MinecraftServer server) {
		tickCounter++;
		if (!config.enabled) {
			return;
		}

		boolean sendReminder = config.reminderIntervalTicks > 0 && tickCounter % config.reminderIntervalTicks == 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID uuid = player.getUUID();
			if (authManager.isAuthenticated(uuid)) {
				continue;
			}

			AuthManager.FrozenPos frozen = authManager.getFrozenPos(uuid);
			if (frozen == null) {
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
	}

	private InteractionResult guard(Player player) {
		if (config.enabled && !authManager.isAuthenticated(player.getUUID())) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}
}
