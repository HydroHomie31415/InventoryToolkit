package dev.luka.inventorytoolkit.quickstack;

import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Opens a Traveler's Backpack the way its open key does, so Chest Sorting can
 * empty it into nearby containers. The mod is optional and only reached
 * through reflection.
 */
public final class TravelersBackpack {
	private static final String MOD_ID = "travelersbackpack";
	private static final Identifier BACKPACK_MENU = Identifier.fromNamespaceAndPath(MOD_ID, "backpack_item");
	private static final String STORAGE_SLOT = "com.tiviacz.travelersbackpack.inventory.menu.slot.BackpackSlotItemHandler";
	// Action types of the mod's ServerboundActionTagPacket
	private static final int OPEN_WORN = 1;
	private static final int OPEN_FROM_INVENTORY = 2;
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	private static Method isWearingBackpack;
	private static Method sendAction;
	private static Class<?> backpackItem;
	private static boolean unavailable = !LOADED;

	private TravelersBackpack() {
	}

	public static boolean isLoaded() {
		return LOADED && !unavailable;
	}

	public static boolean isBackpackMenu(MenuType<?> type) {
		return LOADED && BACKPACK_MENU.equals(BuiltInRegistries.MENU.getKey(type));
	}

	/**
	 * Whether a backpack menu slot is one of the backpack's storage slots,
	 * rather than a tool, upgrade, crafting, tank, or player inventory slot.
	 */
	static boolean isStorageSlot(Slot slot) {
		for (Class<?> type = slot.getClass(); type != null && type != Slot.class; type = type.getSuperclass()) {
			if (type.getName().equals(STORAGE_SLOT)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Asks the server to open the worn backpack, or else the first one in the
	 * inventory.
	 *
	 * @return whether there was a backpack to open
	 */
	static boolean requestOpen(LocalPlayer player) {
		if (!isLoaded() || !resolve()) {
			return false;
		}
		try {
			if ((boolean) isWearingBackpack.invoke(null, player)) {
				sendAction.invoke(null, OPEN_WORN, new Object[0]);
				return true;
			}
			List<ItemStack> items = player.getInventory().getNonEquipmentItems();
			for (int index = 0; index < items.size(); index++) {
				if (backpackItem.isInstance(items.get(index).getItem())) {
					sendAction.invoke(null, OPEN_FROM_INVENTORY, new Object[] {index, false});
					return true;
				}
			}
			return false;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			disable(exception);
			return false;
		}
	}

	private static boolean resolve() {
		if (sendAction != null) {
			return true;
		}
		try {
			isWearingBackpack = Class.forName("com.tiviacz.travelersbackpack.attachment.AttachmentUtils")
				.getMethod("isWearingBackpack", Player.class);
			sendAction = Class.forName("com.tiviacz.travelersbackpack.network.ServerboundActionTagPacket")
				.getMethod("create", int.class, Object[].class);
			backpackItem = Class.forName("com.tiviacz.travelersbackpack.item.TravelersBackpackItem");
			return true;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			disable(exception);
			return false;
		}
	}

	private static void disable(Exception exception) {
		unavailable = true;
		sendAction = null;
		InventoryToolkitClient.LOGGER.warn("Could not open Traveler's Backpack; Chest Sorting will skip it", exception);
	}
}
