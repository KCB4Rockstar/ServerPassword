package com.kcb.serverpassword.command;

import com.kcb.serverpassword.AuthManager;
import com.kcb.serverpassword.PlayerPasswordManager;
import com.kcb.serverpassword.ServerPasswordMod;
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

import java.util.UUID;

public final class ServerPasswordCommands {
	private ServerPasswordCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("login")
				.then(Commands.argument("password", StringArgumentType.greedyString())
						.executes(ServerPasswordCommands::executeLogin)));

		dispatcher.register(Commands.literal("ppass")
				.then(Commands.argument("password", StringArgumentType.word())
						.then(Commands.argument("confirm", StringArgumentType.word())
								.executes(ServerPasswordCommands::executePPass))));

		dispatcher.register(Commands.literal("rpass")
				.then(Commands.argument("password", StringArgumentType.word())
						.executes(ServerPasswordCommands::executeRPass)));

		dispatcher.register(Commands.literal("rppass")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.argument("username", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								ServerPasswordMod.getInstance().getPlayerPasswordManager().getKnownUsernames(), builder))
						.executes(ServerPasswordCommands::executeForceRemovePersonalPassword)));

		dispatcher.register(Commands.literal("serverpassword")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("set")
						.then(Commands.argument("password", StringArgumentType.greedyString())
								.executes(ServerPasswordCommands::executeSet)))
				.then(Commands.literal("reload")
						.executes(ServerPasswordCommands::executeReload)));
	}

	private static int executeLogin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String attempt = StringArgumentType.getString(context, "password");

		ServerPasswordMod mod = ServerPasswordMod.getInstance();
		AuthManager auth = mod.getAuthManager();
		UUID uuid = player.getUUID();

		if (auth.isAuthenticated(uuid)) {
			context.getSource().sendFailure(Component.literal("You are already logged in."));
			return 0;
		}

		String username = player.getGameProfile().name();
		PlayerPasswordManager personalPasswords = mod.getPlayerPasswordManager();

		// A personal password locks the account to that password alone - otherwise
		// it would be pointless, since anyone could still walk in with the shared
		// server password on a cracked/offline server where usernames aren't verified.
		boolean loggedIn = personalPasswords.hasPassword(username)
				? personalPasswords.checkPassword(username, attempt)
				: attempt.equals(mod.getConfig().password);

		if (loggedIn) {
			auth.markAuthenticated(uuid);
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

		ServerPasswordMod.getInstance().getPlayerPasswordManager()
				.setPassword(player.getGameProfile().name(), password);
		player.sendSystemMessage(Component.literal("§aPersonal password set. From now on, only this password logs your account in - the server password will no longer work for you."));
		return 1;
	}

	private static int executeRPass(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String attempt = StringArgumentType.getString(context, "password");
		String username = player.getGameProfile().name();

		PlayerPasswordManager personalPasswords = ServerPasswordMod.getInstance().getPlayerPasswordManager();

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
		boolean removed = ServerPasswordMod.getInstance().getPlayerPasswordManager().removePassword(username);

		if (removed) {
			context.getSource().sendSuccess(() -> Component.literal("Removed " + username + "'s personal password. The server password will log that account in again."), true);
			return 1;
		}

		context.getSource().sendFailure(Component.literal(username + " doesn't have a personal password set."));
		return 0;
	}

	private static int executeSet(CommandContext<CommandSourceStack> context) {
		String newPassword = StringArgumentType.getString(context, "password");
		ServerPasswordMod mod = ServerPasswordMod.getInstance();
		mod.getConfig().password = newPassword;
		mod.getConfig().save();
		context.getSource().sendSuccess(() -> Component.literal("Server password updated."), true);
		return 1;
	}

	private static int executeReload(CommandContext<CommandSourceStack> context) {
		ServerPasswordMod.getInstance().reloadConfig();
		context.getSource().sendSuccess(() -> Component.literal("ServerPassword config reloaded."), true);
		return 1;
	}
}
