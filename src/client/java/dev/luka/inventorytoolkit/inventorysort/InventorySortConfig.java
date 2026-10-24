package dev.luka.inventorytoolkit.inventorysort;

import dev.luka.inventorytoolkit.config.ConfigFiles;

import java.nio.file.Path;

public final class InventorySortConfig {
	private static final Path CONFIG_PATH = ConfigFiles.path("inventory-toolkit-inventory-sort.json");
	private static final int CURRENT_VERSION = 2;

	public enum Order {
		/** The order of the creative inventory's search tab. */
		CREATIVE,
		/** Items you have the most of first. */
		QUANTITY,
		/** Alphabetical by item name. */
		ALPHABET,
		/** The order items are registered in the game. */
		RAW_ID,
		/** Chest Sorting categories, then the creative order within each. */
		CATEGORY
	}

	public enum HotbarScope {
		/** The hotbar is sorted on its own when one of its slots is hovered. */
		SEPARATE,
		/** The hotbar is sorted together with the main inventory. */
		WITH_INVENTORY,
		/** The hotbar is never sorted. */
		NEVER
	}

	public int configVersion = CURRENT_VERSION;
	public boolean enabled = true;
	public Order order = Order.CREATIVE;
	public Order shiftOrder = Order.QUANTITY;
	public Order ctrlOrder = Order.ALPHABET;
	public Order altOrder = Order.RAW_ID;
	public HotbarScope hotbarScope = HotbarScope.SEPARATE;

	public static InventorySortConfig load() {
		InventorySortConfig config = ConfigFiles.load(CONFIG_PATH, InventorySortConfig.class, InventorySortConfig::new);
		config.save();
		return config;
	}

	public void save() {
		// Version 1 sorted by category or name only; start over with the
		// ClientSort-style defaults.
		if (configVersion < CURRENT_VERSION) {
			InventorySortConfig defaults = new InventorySortConfig();
			order = defaults.order;
			shiftOrder = defaults.shiftOrder;
			ctrlOrder = defaults.ctrlOrder;
			altOrder = defaults.altOrder;
			hotbarScope = defaults.hotbarScope;
		}
		configVersion = CURRENT_VERSION;
		if (order == null) {
			order = Order.CREATIVE;
		}
		if (shiftOrder == null) {
			shiftOrder = Order.QUANTITY;
		}
		if (ctrlOrder == null) {
			ctrlOrder = Order.ALPHABET;
		}
		if (altOrder == null) {
			altOrder = Order.RAW_ID;
		}
		if (hotbarScope == null) {
			hotbarScope = HotbarScope.SEPARATE;
		}
		ConfigFiles.save(CONFIG_PATH, this);
	}
}
