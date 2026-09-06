package com.kcb.serverpassword.mixin;

import com.kcb.serverpassword.ServerPasswordMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no general "block any command" or "block chat" hook, so the
 * unauthenticated-player gate for chat and commands is applied here, directly
 * on the packet handlers. Movement is handled separately (see ServerPasswordMod's
 * tick loop) by snapping the player back in place, which avoids client-side
 * position desync that cancelling movement packets outright would cause.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AuthGateMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
	private void serverpassword$blockChat(ServerboundChatPacket packet, CallbackInfo ci) {
		if (isBlocked()) {
			ci.cancel();
			remind();
		}
	}

	@Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
	private void serverpassword$blockChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
		if (isBlocked() && !isAllowed(packet.command())) {
			ci.cancel();
			remind();
		}
	}

	@Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
	private void serverpassword$blockSignedChatCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
		if (isBlocked() && !isAllowed(packet.command())) {
			ci.cancel();
			remind();
		}
	}

	private boolean isBlocked() {
		ServerPasswordMod mod = ServerPasswordMod.getInstance();
		return mod.getConfig().enabled && !mod.getAuthManager().isAuthenticated(player.getUUID());
	}

	private boolean isAllowed(String command) {
		return ServerPasswordMod.getInstance().getAuthManager().isCommandAllowed(command);
	}

	private void remind() {
		player.sendSystemMessage(Component.literal("§cYou must log in first. Use §6/login <password>"));
	}
}
