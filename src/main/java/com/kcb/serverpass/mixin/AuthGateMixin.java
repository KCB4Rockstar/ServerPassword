package com.kcb.serverpass.mixin;

import com.kcb.serverpass.ServerPassMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no general "block any command", "block chat", or "block
 * inventory action" hook, so the unauthenticated-player gate for those is
 * applied here, directly on the packet handlers. Movement is handled
 * separately (see ServerPassMod's tick loop) by snapping the player back
 * in place, which avoids client-side position desync that cancelling
 * movement packets outright would cause.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AuthGateMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
	private void serverpass$blockChat(ServerboundChatPacket packet, CallbackInfo ci) {
		if (isBlocked()) {
			ci.cancel();
			remind();
		}
	}

	@Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
	private void serverpass$blockChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
		if (isBlocked() && !isAllowed(packet.command())) {
			ci.cancel();
			remind();
		}
	}

	@Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
	private void serverpass$blockSignedChatCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
		if (isBlocked() && !isAllowed(packet.command())) {
			ci.cancel();
			remind();
		}
	}

	// Covers dropping items (Q), swapping to the offhand (F), and starting/stopping
	// block breaking - all bundled into this one packet.
	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void serverpass$blockPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (isBlocked()) {
			ci.cancel();
			remind();
		}
	}

	// Covers moving/splitting/shift-clicking items in the player's own inventory screen,
	// which is always available client-side (no "open a container" step to gate on).
	@Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
	private void serverpass$blockContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		if (isBlocked()) {
			ci.cancel();
			remind();
		}
	}

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void serverpass$blockCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		if (isBlocked()) {
			ci.cancel();
		}
	}

	private boolean isBlocked() {
		ServerPassMod mod = ServerPassMod.getInstance();
		return mod.getConfig().enabled && !mod.getAuthManager().isAuthenticated(player.getUUID());
	}

	private boolean isAllowed(String command) {
		return ServerPassMod.getInstance().getAuthManager().isCommandAllowed(command);
	}

	private void remind() {
		player.sendSystemMessage(Component.literal("§cYou must log in first. Use §6/login <password>"));
	}
}
