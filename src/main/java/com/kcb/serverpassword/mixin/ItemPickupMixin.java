package com.kcb.serverpassword.mixin;

import com.kcb.serverpassword.ServerPasswordMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops unauthenticated players from auto-picking-up dropped items by simply
 * walking over them - the interaction-callback guards in ServerPasswordMod
 * only cover deliberate clicks, not passive collision pickup.
 */
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void serverpassword$blockPickup(Player player, CallbackInfo ci) {
		ServerPasswordMod mod = ServerPasswordMod.getInstance();
		if (mod.getConfig().enabled && !mod.getAuthManager().isAuthenticated(player.getUUID())) {
			ci.cancel();
		}
	}
}
