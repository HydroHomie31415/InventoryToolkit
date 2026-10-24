package dev.luka.inventorytoolkit.mixin.client;

import dev.luka.inventorytoolkit.dropprotection.DropProtectionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class DropItemMixin {
	// Pressing the drop key outside a screen drops the selected hotbar item.
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void inventoryToolkit$protectDrop(LocalPlayer player, boolean all, CallbackInfo callbackInfo) {
		if (!DropProtectionClient.allowWorldDrop(Minecraft.getInstance())) {
			callbackInfo.cancel();
		}
	}
}
