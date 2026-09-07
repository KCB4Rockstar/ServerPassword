package com.kcb.serverpass.command;

import com.kcb.serverpass.AuthManager;
import com.kcb.serverpass.PlayerDataStore;
import com.kcb.serverpass.ServerPassMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ServerPassCommands {
	private ServerPassCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("login")
				.then(Commands.argument("password", StringArgumentType.greedyString())
						.executes(ServerPassCommands::executeLogin)));

		dispatcher.register(Commands.literal("ppass")
				.then(Commands.argument("password", StringArgumentType.word())
						.then(Commands.argument("confirm", StringArgumentType.word())
								.executes(ServerPassCommands::executePPass))));

		dispatcher.register(Commands.literal("rpass")
				.then(Commands.argument("password", StringArgumentType.word())
						.executes(ServerPassCommands::executeRPass)));

		dispatcher.register(Commands.literal("rppass")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.argument("username", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								ServerPassMod.getInstance().getPlayerDataStore().getKnownUsernames(), builder))
						.executes(ServerPassCommands::executeForceRemovePersonalPassword)));

		dispatcher.register(Commands.literal("serverpass")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("set")
						.then(Commands.argument("password", StringArgumentType.greedyString())
								.executes(ServerPassCommands::executeSet)))
				.then(Commands.literal("reload")
						.executes(ServerPassCommands::executeReload)));
	}

	private static int executeLogin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String attempt = StringArgumentType.getString(context, "password");

		ServerPassMod mod = ServerPassMod.getInstance();
		AuthManager auth = mod.getAuthManager();
		UUID uuid = player.getUUID();

		if (auth.isAuthenticated(uuid)) {
			context.getSource().sendFailure(Component.literal("You are already logged in."));
			return 0;
		}

		String username = player.getGameProfile().name();
		PlayerDataStore personalPasswords = mod.getPlayerDataStore();

		// A personal password locks the account to that password alone - otherwise
		// it would be pointless, since anyone could still walk in with the shared
		// server password on a cracked/offline server where usernames aren't verified.
		boolean loggedIn = personalPasswords.hasPassword(username)
				? personalPasswords.checkPassword(username, attempt)
				: attempt.equals(mod.getConfig().password);

		if (loggedIn) {
			AuthManager.FrozenPos frozen = auth.getFrozenPos(uuid);
			auth.markAuthenticated(uuid);

			if (frozen != null) {
				player.setGameMode(frozen.originalGameMode());
				player.setDeltaMovement(Vec3.ZERO);
				player.fallDistance = 0;
			}
			mod.getPendingGameModeStore().clear(username);

			player.sendSystemMessage(Component.literal("§aLogin successful. Welcome!"));
			return 1;
		}

		int attempts = auth.registerFailedAttempt(uuid);
		int max = mod.getConfig().maxLoginAttempts;
		if (max > 0 && attempts >= max) {
			player.connection.disconnect(Component.literal("Too many failed login attempts."));
		} else {
			context.getSource().sendFailure(Component.literal("Incorrect password."));
		}
		return 0;
	}

	private static int executePPass(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String password = StringArgumentType.getString(context, "password");
		String confirm = StringArgumentType.getString(context, "confirm");

		if (!password.equals(confirm)) {
			context.getSource().sendFailure(Component.literal("Those passwords don't match. Usage: /ppass <password> <password>"));
			return 0;
		}

		ServerPassMod.getInstance().getPlayerDataStore()
				.setPassword(player.getGameProfile().name(), password);
		player.sendSystemMessage(Component.literal("§aPersonal password set. From now on, only this password logs your account in - the server password will no longer work for you."));
		return 1;
	}

	private static int executeRPass(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String attempt = StringArgumentType.getString(context, "password");
		String username = player.getGameProfile().name();

		PlayerDataStore personalPasswords = ServerPassMod.getInstance().getPlayerDataStore();

		if (!personalPasswords.hasPassword(username)) {
			context.getSource().sendFailure(Component.literal("You don't have a personal password set."));
			return 0;
		}

		if (!personalPasswords.checkPassword(username, attempt)) {
			context.getSource().sendFailure(Component.literal("Incorrect password."));
			return 0;
		}

		personalPasswords.removePassword(username);
		player.sendSystemMessage(Component.literal("§aPersonal password removed. The server password will log you in again."));
		return 1;
	}

	private static int executeForceRemovePersonalPassword(CommandContext<CommandSourceStack> context) {
		String username = StringArgumentType.getString(context, "username");
		boolean removed = ServerPassMod.getInstance().getPlayerDataStore().removePassword(username);

		if (removed) {
			context.getSource().sendSuccess(() -> Component.literal("Removed " + username + "'s personal password. The server password will log that account in again."), true);
			return 1;
		}

		context.getSource().sendFailure(Component.literal(username + " doesn't have a personal password set."));
		return 0;
	}

	private static int executeSet(CommandContext<CommandSourceStack> context) {
		String newPassword = StringArgumentType.getString(context, "password");
		ServerPassMod mod = ServerPassMod.getInstance();
		mod.getConfig().password = newPassword;
		mod.getConfig().save();
		context.getSource().sendSuccess(() -> Component.literal("Server password updated."), true);
		return 1;
	}

	private static int executeReload(CommandContext<CommandSourceStack> context) {
		ServerPassMod.getInstance().reloadConfig();
		context.getSource().sendSuccess(() -> Component.literal("ServerPass config reloaded."), true);
		return 1;
	}
}
