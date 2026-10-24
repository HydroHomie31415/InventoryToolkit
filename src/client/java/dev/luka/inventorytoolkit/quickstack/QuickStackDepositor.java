package dev.luka.inventorytoolkit.quickstack;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shift-clicks every player stack whose item already exists in the open
 * storage container. Only menus whose quick-move sends player items straight
 * into the container are supported, so a click can never reshuffle the
 * player's own inventory.
 */
public final class QuickStackDepositor {
	private QuickStackDepositor() {
	}

	public static boolean supports(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu
			|| menu instanceof ShulkerBoxMenu
			|| menu instanceof HopperMenu
			|| menu instanceof DispenserMenu;
	}

	/**
	 * @return the number of items that left the player's inventory
	 */
	public static int deposit(Minecraft client, AbstractContainerMenu menu, QuickStackConfig config) {
		LocalPlayer player = client.player;
		if (!config.enabled
			|| player == null
			|| client.gameMode == null
			|| player.isSpectator()
			|| player.containerMenu != menu
			|| !supports(menu)
			|| !menu.getCarried().isEmpty()) {
			return 0;
		}

		List<ItemStack> containerItems = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && slot.hasItem()) {
				containerItems.add(slot.getItem());
			}
		}
		if (containerItems.isEmpty()) {
			return 0;
		}

		InventoryActionCoordinator.claimManual(InventoryActionCoordinator.QUICK_STACK);
		int moved = 0;
		for (Slot slot : menu.slots) {
			if (!isEligibleSource(slot, player, config)
				|| !matchesAny(slot.getItem(), containerItems, config)) {
				continue;
			}

			int before = slot.getItem().getCount();
			client.gameMode.handleContainerInput(
				menu.containerId,
				slot.index,
				0,
				ContainerInput.QUICK_MOVE,
				player
			);
			moved += before - slot.getItem().getCount();
		}
		return moved;
	}

	private static boolean isEligibleSource(Slot slot, LocalPlayer player, QuickStackConfig config) {
		if (!(slot.container instanceof Inventory)
			|| !slot.hasItem()
			|| !slot.isActive()
			|| slot.isFake()
			|| !slot.mayPickup(player)) {
			return false;
		}

		int inventorySlot = slot.getContainerSlot();
		if (inventorySlot < 0 || inventorySlot >= Inventory.INVENTORY_SIZE || SlotLockClient.isLocked(inventorySlot)) {
			return false;
		}
		if (inventorySlot < Inventory.SELECTION_SIZE && !config.includeHotbar) {
			return false;
		}
		return config.includeUnstackable || slot.getItem().isStackable();
	}

	private static boolean matchesAny(
		ItemStack stack,
		List<ItemStack> containerItems,
		QuickStackConfig config
	) {
		for (ItemStack containerItem : containerItems) {
			boolean matches = config.matchComponents
				? ItemStack.isSameItemSameComponents(stack, containerItem)
				: ItemStack.isSameItem(stack, containerItem);
			if (matches) {
				return true;
			}
		}
		return false;
	}
}
