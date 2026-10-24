package dev.luka.inventorytoolkit.inventorysort;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.ScreenMessages;
import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import dev.luka.inventorytoolkit.quickstack.SortClassifier;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts the inventory under the mouse with one key, the middle mouse button
 * by default, the way ClientSort does. Hovering over a chest, barrel, shulker
 * box, or other storage container slot sorts that container; hovering over
 * the main inventory, or over nothing while only the inventory is open, sorts
 * the main inventory; hovering over the hotbar sorts the hotbar on its own.
 * Holding Shift, Ctrl, or Alt picks another sort order. Locked slots stay
 * where they are.
 */
public final class InventorySortClient {
	private static InventorySortConfig config;
	private static KeyMapping sortKey;

	private InventorySortClient() {
	}

	public static void initialize() {
		config = InventorySortConfig.load();
		sortKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.inventory_sort",
			InputConstants.Type.MOUSE,
			InputConstants.MOUSE_BUTTON_MIDDLE,
			InventoryToolkitClient.keyCategory()
		));

		// Keyboard bindings are handled in ContainerScreenMixin.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen
				&& !(screen instanceof CreativeModeInventoryScreen)) {
				ScreenMouseEvents.allowMouseClick(containerScreen).register((ignored, event) -> {
					if (!sortKey.matchesMouse(event)) {
						return true;
					}
					AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
					Slot hovered = accessor.inventoryToolkit$hoveredSlot();
					// Clicks beside the panel belong to what is there, such as
					// the recipe book, where the middle button favorites a recipe.
					if (hovered == null && !isOverPanel(accessor, event.x(), event.y())) {
						return true;
					}
					// In creative mode the middle button copies the hovered stack.
					if (client.player != null
						&& client.player.hasInfiniteMaterials()
						&& client.options.keyPickItem.matchesMouse(event)
						&& hovered != null
						&& hovered.hasItem()) {
						return true;
					}
					return !sort(client, containerScreen, hovered);
				});
			}
		});
	}

	public static InventorySortConfig config() {
		return config;
	}

	/**
	 * Called from a container screen's key handling.
	 *
	 * @return whether the key was used to sort
	 */
	public static boolean onKeyPressed(AbstractContainerScreen<?> screen, KeyEvent event, Slot hoveredSlot) {
		return !(screen instanceof CreativeModeInventoryScreen)
			&& sortKey.matches(event)
			&& sort(Minecraft.getInstance(), screen, hoveredSlot);
	}

	/**
	 * @return whether there was something to sort under the mouse
	 */
	private static boolean sort(Minecraft client, AbstractContainerScreen<?> screen, Slot hovered) {
		LocalPlayer player = client.player;
		AbstractContainerMenu menu = screen.getMenu();
		if (!config.enabled || player == null || client.gameMode == null || player.containerMenu != menu) {
			return false;
		}
		List<Slot> slots = targetSlots(menu, hovered);
		if (slots.isEmpty()) {
			return false;
		}
		if (!menu.getCarried().isEmpty()) {
			ScreenMessages.show(client, Component.translatable("message.inventory_toolkit.cursor_not_empty"));
			return true;
		}
		if (!InventoryActionCoordinator.acquire(InventoryActionCoordinator.INVENTORY_SORT)) {
			ScreenMessages.show(client, Component.translatable("message.inventory_toolkit.busy"));
			return true;
		}

		List<ItemStack> stacks = slots.stream().map(Slot::getItem).filter(stack -> !stack.isEmpty()).toList();
		Comparator<ItemStack> order = SortOrders.comparator(
			orderForModifiers(client),
			stacks,
			new SortClassifier(QuickStackClient.config().sortRules),
			player.level()
		);
		if (new InventorySorter(client, menu, slots).sort(order) == InventorySorter.Result.INTERRUPTED) {
			ScreenMessages.show(client, Component.translatable("message.inventory_toolkit.inventory_sort.interrupted"));
		}
		return true;
	}

	private static boolean isOverPanel(AbstractContainerScreenAccessor screen, double x, double y) {
		int left = screen.inventoryToolkit$leftPos();
		int top = screen.inventoryToolkit$topPos();
		return x >= left
			&& y >= top
			&& x < left + screen.inventoryToolkit$imageWidth()
			&& y < top + screen.inventoryToolkit$imageHeight();
	}

	private static InventorySortConfig.Order orderForModifiers(Minecraft client) {
		if (client.hasShiftDown()) {
			return config.shiftOrder;
		}
		if (client.hasControlDown()) {
			return config.ctrlOrder;
		}
		if (client.hasAltDown()) {
			return config.altOrder;
		}
		return config.order;
	}

	/**
	 * @return the slots to sort, in the order they should fill, or an empty
	 *     list when nothing under the mouse can be sorted
	 */
	private static List<Slot> targetSlots(AbstractContainerMenu menu, Slot hovered) {
		if (hovered == null) {
			return isStorage(menu) ? containerSlots(menu) : playerSlots(menu, false);
		}
		if (!(hovered.container instanceof Inventory)) {
			if (isStorage(menu)) {
				return containerSlots(menu);
			}
			// A crafting grid, furnace slot, and so on are never sorted, but
			// a modded storage screen, such as a backpack, sorts the slots of
			// the hovered container.
			return isVanilla(menu) ? List.of() : containerSlots(menu, hovered.container);
		}
		int index = hovered.getContainerSlot();
		if (index < 0 || index >= Inventory.INVENTORY_SIZE) {
			// Armor and offhand slots
			return List.of();
		}
		return playerSlots(menu, index < Inventory.SELECTION_SIZE);
	}

	/**
	 * @param hotbarHovered whether the sort was started over the hotbar
	 * @return the main inventory followed by the hotbar, as the hotbar scope
	 *     setting allows
	 */
	private static List<Slot> playerSlots(AbstractContainerMenu menu, boolean hotbarHovered) {
		boolean includeMain = !hotbarHovered || config.hotbarScope != InventorySortConfig.HotbarScope.SEPARATE;
		boolean includeHotbar = switch (config.hotbarScope) {
			case SEPARATE -> hotbarHovered;
			case WITH_INVENTORY -> true;
			case NEVER -> false;
		};
		if (hotbarHovered && !includeHotbar) {
			return List.of();
		}
		Slot[] byIndex = new Slot[Inventory.INVENTORY_SIZE];
		for (Slot slot : menu.slots) {
			int index = slot.getContainerSlot();
			if (slot.container instanceof Inventory
				&& index >= 0
				&& index < Inventory.INVENTORY_SIZE
				&& isSortable(slot)) {
				byIndex[index] = slot;
			}
		}
		List<Slot> slots = new ArrayList<>();
		for (int offset = 0; offset < Inventory.INVENTORY_SIZE; offset++) {
			int index = (offset + Inventory.SELECTION_SIZE) % Inventory.INVENTORY_SIZE;
			boolean hotbar = index < Inventory.SELECTION_SIZE;
			if (byIndex[index] != null && (hotbar ? includeHotbar : includeMain) && !SlotLockClient.isLocked(index)) {
				slots.add(byIndex[index]);
			}
		}
		return slots;
	}

	private static List<Slot> containerSlots(AbstractContainerMenu menu) {
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && isSortable(slot)) {
				slots.add(slot);
			}
		}
		return slots;
	}

	private static List<Slot> containerSlots(AbstractContainerMenu menu, Container container) {
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (slot.container == container && isSortable(slot)) {
				slots.add(slot);
			}
		}
		return slots;
	}

	private static boolean isSortable(Slot slot) {
		return slot.isActive() && !slot.isFake();
	}

	/**
	 * Menus whose own slots are all plain storage.
	 */
	private static boolean isStorage(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu
			|| menu instanceof ShulkerBoxMenu
			|| menu instanceof DispenserMenu
			|| menu instanceof HopperMenu;
	}

	/**
	 * Menus added by Minecraft itself rather than by another mod.
	 */
	private static boolean isVanilla(AbstractContainerMenu menu) {
		return menu.getClass().getName().startsWith("net.minecraft.");
	}
}
