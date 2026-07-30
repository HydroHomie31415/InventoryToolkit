package dev.luka.inventorytoolkit;

/**
 * Ensures that separate toolkit modules do not send unrelated inventory
 * transactions during the same client tick. A module may still perform a
 * multi-click operation after it acquires the tick.
 */
public final class InventoryActionCoordinator {
	public static final String LOADOUTS = "loadouts";
	public static final String STACK_REFILL = "stack_refill";
	public static final String SWEEPER = "sweeper";

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
}
