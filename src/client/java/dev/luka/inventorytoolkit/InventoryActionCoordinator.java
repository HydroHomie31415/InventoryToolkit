package dev.luka.inventorytoolkit;

/**
 * Ensures that separate toolkit modules do not send unrelated inventory
 * transactions during the same client tick. A module may still perform a
 * multi-click operation after it acquires the tick.
 */
public final class InventoryActionCoordinator {
	public static final String LOADOUTS = "loadouts";
	public static final String OFFHAND_SWAPPER = "offhand_swapper";
	public static final String HYDROS_BUNDLES = "hydros_bundles";
	public static final String STACK_REFILL = "stack_refill";
	public static final String SWEEPER = "sweeper";
	public static final String SHIFT_DRAG_TRANSFER = "shift_drag_transfer";
	public static final String RECIPE_BOOK = "recipe_book";
	public static final String QUICK_STACK = "quick_stack";
	public static final String COMPACT = "compact";
	public static final String QUICK_TRADE = "quick_trade";
	public static final String INVENTORY_SORT = "inventory_sort";

	private static String owner;

	private InventoryActionCoordinator() {
	}

	public static void beginTick() {
		owner = null;
	}

	public static boolean isAvailableFor(String module) {
		return owner == null || owner.equals(module);
	}

	public static boolean acquire(String module) {
		if (!isAvailableFor(module)) {
			return false;
		}
		owner = module;
		return true;
	}

	public static void claimManual(String module) {
		owner = module;
	}
}
