package dev.hydro.hydrosrecipebook;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class InstantCraftManager {
	private static final int TIMEOUT_TICKS = 30;
	private static PendingCraft pending;

	private InstantCraftManager() {
	}

	public static void arm(int containerId, ItemStack expectedResult) {
		if (!expectedResult.isEmpty()) {
			pending = new PendingCraft(containerId, expectedResult.copy(), TIMEOUT_TICKS, 2);
		}
	}

	public static void cancel() {
		pending = null;
	}

	public static void tick(Minecraft minecraft) {
		if (pending == null || minecraft.player == null || minecraft.gameMode == null) {
			return;
		}

		AbstractContainerMenu menu = minecraft.player.containerMenu;
		if (menu.containerId != pending.containerId || !(menu instanceof AbstractCraftingMenu craftingMenu)) {
			pending = null;
			return;
		}

		if (pending.delayTicks > 0) {
			pending.delayTicks--;
			return;
		}

		Slot resultSlot = craftingMenu.getResultSlot();
		ItemStack result = resultSlot.getItem();
		if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, pending.expectedResult)) {
			if (!InventoryActionCoordinator.acquire(
				InventoryActionCoordinator.RECIPE_BOOK
			)) {
				return;
			}
			int menuSlot = menu.slots.indexOf(resultSlot);
			if (menuSlot >= 0) {
				minecraft.gameMode.handleContainerInput(
					menu.containerId,
					menuSlot,
					0,
					ContainerInput.QUICK_MOVE,
					minecraft.player
				);
			}
			pending = null;
			return;
		}

		pending.remainingTicks--;
		if (pending.remainingTicks <= 0) {
			pending = null;
		}
	}

	private static final class PendingCraft {
		private final int containerId;
		private final ItemStack expectedResult;
		private int remainingTicks;
		private int delayTicks;

		private PendingCraft(int containerId, ItemStack expectedResult, int remainingTicks, int delayTicks) {
			this.containerId = containerId;
			this.expectedResult = expectedResult;
			this.remainingTicks = remainingTicks;
			this.delayTicks = delayTicks;
		}
	}
}
