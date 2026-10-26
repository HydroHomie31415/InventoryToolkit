package dev.luka.inventorytoolkit.quickstack;

import dev.luka.inventorytoolkit.containersearch.ContainerMemory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lets other mods (Simple Blueprints) see what the remembered containers hold
 * and ask for items to be fetched from the containers within reach. Shared
 * through Fabric's object share with plain java.util.function types, so
 * neither mod needs the other to compile or run:
 * <ul>
 *   <li>{@value #STORED_ITEMS}: {@code Supplier<Map<String, Integer>>}, item
 *   id to the count stored in this dimension, nested items included;</li>
 *   <li>{@value #FETCH_ITEMS}: {@code Function<Map<String, Integer>, Boolean>},
 *   item id to the count wanted; returns whether a fetch run started.</li>
 * </ul>
 */
public final class StorageFetch {
	public static final String STORED_ITEMS = "inventory_toolkit:stored_items";
	public static final String FETCH_ITEMS = "inventory_toolkit:fetch_items";

	private StorageFetch() {
	}

	static void register() {
		Supplier<Map<String, Integer>> stored = StorageFetch::storedItems;
		Function<Map<String, Integer>, Boolean> fetch = StorageFetch::fetch;
		FabricLoader.getInstance().getObjectShare().put(STORED_ITEMS, stored);
		FabricLoader.getInstance().getObjectShare().put(FETCH_ITEMS, fetch);
	}

	private static Map<String, Integer> storedItems() {
		Map<String, Integer> counts = new HashMap<>();
		if (!QuickStackClient.config().memoryEnabled) {
			return counts;
		}
		for (ContainerMemory.StoredContainer container : ContainerMemory.containersInCurrentDimension()) {
			for (ContainerMemory.StoredStack stack : container.items()) {
				counts.merge(stack.item(), stack.count(), Integer::sum);
			}
		}
		return counts;
	}

	private static boolean fetch(Map<String, Integer> wanted) {
		Map<Item, Integer> items = new HashMap<>();
		for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			if (item != Items.AIR && entry.getValue() > 0) {
				items.merge(item, entry.getValue(), Integer::sum);
			}
		}
		NearbyQuickStack nearby = QuickStackClient.nearby();
		if (items.isEmpty() || nearby.isRunning()) {
			return false;
		}
		nearby.startFetch(QuickStackClient.config(), items);
		return nearby.isRunning();
	}

	/**
	 * Shift-clicks whole stacks of the wanted items out of the open container,
	 * lowering the wanted counts by what arrived.
	 *
	 * @return how many items were taken, and whether the inventory filled up
	 */
	static Taken take(Minecraft client, AbstractContainerMenu menu, Map<Item, Integer> wanted) {
		LocalPlayer player = client.player;
		if (player == null
			|| client.gameMode == null
			|| player.containerMenu != menu
			|| !menu.getCarried().isEmpty()) {
			return new Taken(0, false);
		}
		int taken = 0;
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory || !slot.hasItem() || !slot.mayPickup(player)) {
				continue;
			}
			Item item = slot.getItem().getItem();
			int left = wanted.getOrDefault(item, 0);
			if (left <= 0) {
				continue;
			}
			int before = slot.getItem().getCount();
			client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
			int moved = before - slot.getItem().getCount();
			if (moved <= 0) {
				return new Taken(taken, true);
			}
			taken += moved;
			wanted.put(item, left - moved);
		}
		return new Taken(taken, false);
	}

	record Taken(int items, boolean inventoryFull) {
	}
}
