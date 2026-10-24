package dev.luka.inventorytoolkit.mixin.client;

import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.impl.menu.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus with extra opening data, such as Traveler's Backpack's, arrive through
 * Fabric's own packet instead of ClientboundOpenScreenPacket. A backpack that
 * Chest Sorting asked for is kept without a screen, like its containers (see
 * ClientPacketListenerMixin).
 */
@Mixin(targets = "net.fabricmc.fabric.impl.menu.client.ClientNetworking")
abstract class FabricMenuClientNetworkingMixin {
	@Inject(method = "openScreen", at = @At("HEAD"), cancellable = true, require = 0)
	private void inventoryToolkit$openBackpackSilently(
		Networking.OpenScreenPayload<?> payload,
		CallbackInfo callbackInfo
	) {
		MenuType<?> type = BuiltInRegistries.MENU.getValue(payload.identifier());
		if (!(type instanceof ExtendedMenuType<?, ?> extendedType)
			|| payload.data() == null
			|| !QuickStackClient.nearby().expectsBackpack(type)) {
			return;
		}
		Inventory inventory = Minecraft.getInstance().player.getInventory();
		QuickStackClient.nearby().openBackpackSilently(
			inventoryToolkit$create(extendedType, payload.containerId(), inventory, payload.data())
		);
		callbackInfo.cancel();
	}

	@SuppressWarnings("unchecked")
	private static <D> AbstractContainerMenu inventoryToolkit$create(
		ExtendedMenuType<?, D> type,
		int containerId,
		Inventory inventory,
		Object data
	) {
		return type.create(containerId, inventory, (D) data);
	}
}
