package com.kcb.serverpassword.command;

import com.kcb.serverpassword.AuthManager;
import com.kcb.serverpassword.ServerPasswordMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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

		if (attempt.equals(mod.getConfig().password)) {
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
