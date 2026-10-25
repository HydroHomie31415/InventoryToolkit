package dev.luka.inventorytoolkit.quickstack;

import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Plans a nearby sort for {@link NearbyQuickStack}. The first round only reads
 * every container. Each container is then given one {@link SortCategory}:
 * named containers keep the category their name spells, every category goes
 * to the container that already holds the most of it, and spare containers
 * take categories that have no home yet or do not fit.
 *
 * <p>Later rounds visit the containers again. Misplaced stacks are picked up
 * into empty player inventory slots and quick-moved into a container of their
 * category on a later visit. Only those slots are ever deposited, so the
 * player's own items stay where they are, unless sorting the inventory is
 * enabled: then the player's stacks that Quick Stack may move (by its hotbar
 * and unstackable settings) are put away the same way. Rounds repeat until
 * one makes no progress.
 *
 * <p>A Traveler's Backpack is read alongside the containers and visited
 * first in every later round. It never gets a category: its stacks are
 * picked up like misplaced ones whenever their category has room, so the
 * same round puts them away.
 */
final class ChestSorter {
	private static final int MAX_ROUNDS = 32;

	private final Minecraft client;
	private final SortClassifier classifier;
	private final QuickStackConfig config;
	private final boolean useChestNames;
	private final boolean sortInventory;
	private final boolean includeHotbar;
	private final boolean includeUnstackable;
	private final List<BlockPos> order;
	private final Map<BlockPos, SortedChest> chests = new HashMap<>();
	private final Set<Integer> carriedSlots = new LinkedHashSet<>();
	private SortedChest backpack;
	private boolean backpackVisit;
	private boolean scanned;
	private boolean progress;
	private int rounds;
	private int itemsMoved;

	ChestSorter(Minecraft client, List<BlockPos> containers, QuickStackConfig config) {
		this.client = client;
		this.config = config;
		this.classifier = new SortClassifier(config.sortRules);
		this.useChestNames = config.sortUseChestNames;
		this.sortInventory = config.sortInventory;
		this.includeHotbar = config.includeHotbar;
		this.includeUnstackable = config.includeUnstackable;
		this.order = List.copyOf(containers);
	}

	int itemsMoved() {
		return itemsMoved;
	}

	int containersSorted() {
		return (int) chests.values().stream().filter(chest -> chest.received).count();
	}

	/**
	 * @return items picked up from a container that could not be put away and
	 *     are still in the player's inventory
	 */
	int itemsLeftInInventory() {
		LocalPlayer player = client.player;
		if (player == null) {
			return 0;
		}
		int count = 0;
		for (int inventorySlot : carriedSlots) {
			count += player.getInventory().getItem(inventorySlot).getCount();
		}
		return count;
	}

	/**
	 * Called for every container a round opens.
	 *
	 * @param contentsArrived false when the server never sent the contents
	 */
	void visit(BlockPos pos, AbstractContainerMenu menu, Component title, boolean contentsArrived) {
		if (!contentsArrived) {
			return;
		}
		if (!scanned) {
			SortedChest chest = new SortedChest(false);
			chest.update(menu);
			rememberUncategorized(chest.items);
			if (useChestNames) {
				chest.category = SortCategory.fromContainerName(title.getString());
			}
			chests.put(pos, chest);
			return;
		}

		SortedChest chest = chests.get(pos);
		if (chest == null) {
			return;
		}
		boolean deposited = deposit(menu, chest);
		if (withdraw(menu, chest)) {
			// Withdrawing freed container slots that carried items may now use.
			deposited |= deposit(menu, chest);
		}
		chest.received |= deposited;
		chest.update(menu);
	}

	/**
	 * Called for the backpack, which each round opens before the containers.
	 *
	 * @param contentsArrived false when the server never sent the contents
	 */
	void visitBackpack(AbstractContainerMenu menu, boolean contentsArrived) {
		if (!contentsArrived) {
			return;
		}
		if (!scanned) {
			backpack = new SortedChest(true);
		} else if (backpack == null) {
			return;
		} else {
			withdraw(menu, backpack);
		}
		backpack.update(menu);
		if (!scanned) {
			rememberUncategorized(backpack.items);
		}
	}

	/**
	 * @return the containers the next round should visit, nearest first; empty
	 *     when sorting is done
	 */
	List<BlockPos> nextRound() {
		backpackVisit = false;
		if (!scanned) {
			scanned = true;
			assignCategories();
		} else if (!progress) {
			return List.of();
		}
		if (++rounds > MAX_ROUNDS) {
			return List.of();
		}
		progress = false;

		backpackVisit = backpack != null && hasWithdrawableStack(backpack);
		List<BlockPos> visits = new ArrayList<>();
		for (BlockPos pos : order) {
			SortedChest chest = chests.get(pos);
			if (chest != null
				&& (carriesFor(chest.category)
					|| hasWithdrawableStack(chest)
					|| (chest.emptySlots() > 0 && inventoryHolds(chest.category))
					|| (chest.emptySlots() > 0 && backpackVisit && chest.category != null
						&& slotsOf(backpack, chest.category) > 0))) {
				visits.add(pos);
			}
		}
		return visits;
	}

	/**
	 * Whether the round {@link #nextRound()} just planned should open the
	 * backpack before the containers.
	 */
	boolean visitsBackpack() {
		return backpackVisit;
	}

	/**
	 * Plans categories from the first round without moving anything.
	 *
	 * @return the category of every container that was read, nearest first;
	 *     null for containers that stay as they are
	 */
	Map<BlockPos, SortCategory> preview() {
		if (!scanned) {
			scanned = true;
			assignCategories();
		}
		Map<BlockPos, SortCategory> plan = new LinkedHashMap<>();
		for (BlockPos pos : order) {
			SortedChest chest = chests.get(pos);
			if (chest != null) {
				plan.put(pos, chest.category);
			}
		}
		return plan;
	}

	/**
	 * Estimates how many items a sort following {@link #preview()} would
	 * move: misplaced container items and sortable inventory items whose
	 * category has a container.
	 */
	int itemsToMove() {
		Set<SortCategory> homed = EnumSet.noneOf(SortCategory.class);
		for (SortedChest chest : chests.values()) {
			if (chest.category != null) {
				homed.add(chest.category);
			}
		}
		int items = 0;
		for (SortedChest chest : chests.values()) {
			for (ItemStack stack : chest.items) {
				SortCategory category = classifier.classify(stack);
				if (isMisplaced(category, chest) && homed.contains(category)) {
					items += stack.getCount();
				}
			}
		}
		if (backpack != null) {
			for (ItemStack stack : backpack.items) {
				if (homed.contains(classifier.classify(stack))) {
					items += stack.getCount();
				}
			}
		}
		Inventory inventory = client.player.getInventory();
		for (int inventorySlot = 0; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
			if (isSortableInventorySlot(inventorySlot)
				&& homed.contains(classifier.classify(inventory.getItem(inventorySlot)))) {
				items += inventory.getItem(inventorySlot).getCount();
			}
		}
		return items;
	}

	private void assignCategories() {
		Map<SortCategory, Integer> totals = new EnumMap<>(SortCategory.class);
		List<Claim> claims = new ArrayList<>();
		Set<SortCategory> homed = EnumSet.noneOf(SortCategory.class);
		for (BlockPos pos : order) {
			SortedChest chest = chests.get(pos);
			if (chest == null) {
				continue;
			}
			if (chest.category != null) {
				homed.add(chest.category);
			}
			Map<SortCategory, Integer> counts = new EnumMap<>(SortCategory.class);
			for (ItemStack stack : chest.items) {
				SortCategory category = classifier.classify(stack);
				if (category != null) {
					counts.merge(category, stack.getCount(), Integer::sum);
				}
			}
			counts.forEach((category, count) -> {
				totals.merge(category, count, Integer::sum);
				if (chest.category == null) {
					claims.add(new Claim(chest, category, count));
				}
			});
		}

		// The backpack's items need a home, but it never claims a category.
		if (backpack != null) {
			for (ItemStack stack : backpack.items) {
				SortCategory category = classifier.classify(stack);
				if (category != null) {
					totals.merge(category, stack.getCount(), Integer::sum);
				}
			}
		}
		Inventory inventory = client.player.getInventory();
		for (int inventorySlot = 0; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
			rememberUncategorized(inventory.getItem(inventorySlot));
			if (isSortableInventorySlot(inventorySlot)) {
				ItemStack stack = inventory.getItem(inventorySlot);
				totals.merge(classifier.classify(stack), stack.getCount(), Integer::sum);
			}
		}

		// Each category goes to the container that already holds the most of
		// it. The sort is stable, so the nearer container wins a tie.
		claims.sort(Comparator.comparingInt(Claim::count).reversed());
		for (Claim claim : claims) {
			if (claim.chest().category == null && !homed.contains(claim.category())) {
				claim.chest().category = claim.category();
				homed.add(claim.category());
			}
		}

		// Categories whose holders all went to bigger categories move into the
		// emptiest spare container.
		List<SortCategory> homeless = totals.keySet().stream()
			.filter(category -> !homed.contains(category))
			.sorted(Comparator.comparingInt((SortCategory category) -> totals.get(category)).reversed())
			.toList();
		for (SortCategory category : homeless) {
			SortedChest spare = spareChest(SortedChest::emptySlots);
			if (spare == null) {
				break;
			}
			spare.category = category;
			homed.add(category);
		}

		// Spare containers take the overflow of categories that do not fit.
		while (true) {
			SortCategory worst = null;
			int worstDeficit = 0;
			for (SortCategory category : homed) {
				int deficit = stacksOf(category) - capacityFor(category, homed);
				if (deficit > worstDeficit) {
					worst = category;
					worstDeficit = deficit;
				}
			}
			if (worst == null) {
				break;
			}
			SortCategory overflowing = worst;
			SortedChest spare = spareChest(chest -> slotsOf(chest, overflowing) * 1000 + chest.emptySlots());
			if (spare == null) {
				break;
			}
			spare.category = overflowing;
		}
	}

	private void rememberUncategorized(List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			rememberUncategorized(stack);
		}
	}

	private void rememberUncategorized(ItemStack stack) {
		if (classifier.isUncategorized(stack)) {
			config.rememberUncategorized(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}
	}

	private SortedChest spareChest(ToIntFunction<SortedChest> preference) {
		SortedChest best = null;
		for (BlockPos pos : order) {
			SortedChest chest = chests.get(pos);
			if (chest != null
				&& chest.category == null
				&& (best == null || preference.applyAsInt(chest) > preference.applyAsInt(best))) {
				best = chest;
			}
		}
		return best;
	}

	private int stacksOf(SortCategory category) {
		int stacks = 0;
		for (SortedChest chest : chests.values()) {
			stacks += slotsOf(chest, category);
		}
		if (backpack != null) {
			stacks += slotsOf(backpack, category);
		}
		Inventory inventory = client.player.getInventory();
		for (int inventorySlot = 0; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
			if (isSortableInventorySlot(inventorySlot)
				&& classifier.classify(inventory.getItem(inventorySlot)) == category) {
				stacks++;
			}
		}
		return stacks;
	}

	/**
	 * Whether a player stack that was not picked up from a container should
	 * be put away, following the Quick Stack hotbar and unstackable settings.
	 */
	private boolean isSortableInventorySlot(int inventorySlot) {
		if (!sortInventory
			|| inventorySlot < 0
			|| inventorySlot >= Inventory.INVENTORY_SIZE
			|| carriedSlots.contains(inventorySlot)
			|| SlotLockClient.isLocked(inventorySlot)
			|| (inventorySlot < Inventory.SELECTION_SIZE && !includeHotbar)) {
			return false;
		}
		ItemStack stack = client.player.getInventory().getItem(inventorySlot);
		return classifier.classify(stack) != null && (includeUnstackable || stack.isStackable());
	}

	private boolean inventoryHolds(SortCategory category) {
		if (category == null) {
			return false;
		}
		Inventory inventory = client.player.getInventory();
		for (int inventorySlot = 0; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
			if (isSortableInventorySlot(inventorySlot)
				&& classifier.classify(inventory.getItem(inventorySlot)) == category) {
				return true;
			}
		}
		return false;
	}

	private int slotsOf(SortedChest chest, SortCategory category) {
		int slots = 0;
		for (ItemStack stack : chest.items) {
			if (classifier.classify(stack) == category) {
				slots++;
			}
		}
		return slots;
	}

	/**
	 * Slots the containers of a category can offer once every item with a
	 * home elsewhere has left them.
	 */
	private int capacityFor(SortCategory category, Set<SortCategory> homed) {
		int capacity = 0;
		for (SortedChest chest : chests.values()) {
			if (chest.category != category) {
				continue;
			}
			for (ItemStack stack : chest.items) {
				SortCategory stackCategory = classifier.classify(stack);
				if (stack.isEmpty() || stackCategory == category || (stackCategory != null && homed.contains(stackCategory))) {
					capacity++;
				}
			}
		}
		return capacity;
	}

	/**
	 * Empty slots the containers of a category had at their last visit, less
	 * the stacks already carried for them.
	 */
	private int freeSlotsFor(SortCategory category) {
		int free = 0;
		for (SortedChest chest : chests.values()) {
			if (chest.category == category) {
				free += chest.emptySlots();
			}
		}
		Inventory inventory = client.player.getInventory();
		for (int inventorySlot : carriedSlots) {
			if (classifier.classify(inventory.getItem(inventorySlot)) == category) {
				free--;
			}
		}
		return free;
	}

	private boolean carriesFor(SortCategory category) {
		if (category == null) {
			return false;
		}
		Inventory inventory = client.player.getInventory();
		for (int inventorySlot : carriedSlots) {
			if (classifier.classify(inventory.getItem(inventorySlot)) == category) {
				return true;
			}
		}
		return false;
	}

	private boolean hasWithdrawableStack(SortedChest chest) {
		for (ItemStack stack : chest.items) {
			SortCategory category = classifier.classify(stack);
			if (isMisplaced(category, chest) && freeSlotsFor(category) > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean isMisplaced(SortCategory category, SortedChest chest) {
		return category != null && category != chest.category;
	}

	/**
	 * Quick-moves every carried stack of this container's category into it,
	 * followed by the player's own stacks of that category.
	 *
	 * @return whether any item went in
	 */
	private boolean deposit(AbstractContainerMenu menu, SortedChest chest) {
		if (chest.category == null) {
			return false;
		}
		boolean deposited = false;
		Iterator<Integer> carried = carriedSlots.iterator();
		while (carried.hasNext()) {
			Slot slot = playerSlot(menu, carried.next());
			if (slot == null || !slot.hasItem()) {
				carried.remove();
				continue;
			}
			if (classifier.classify(slot.getItem()) != chest.category) {
				continue;
			}

			int before = slot.getItem().getCount();
			click(menu, slot, ContainerInput.QUICK_MOVE);
			int moved = before - slot.getItem().getCount();
			if (moved > 0) {
				itemsMoved += moved;
				progress = true;
				deposited = true;
			}
			if (!slot.hasItem()) {
				carried.remove();
			}
		}

		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)
				|| !slot.hasItem()
				|| !isSortableInventorySlot(slot.getContainerSlot())
				|| classifier.classify(slot.getItem()) != chest.category) {
				continue;
			}
			int before = slot.getItem().getCount();
			click(menu, slot, ContainerInput.QUICK_MOVE);
			int moved = before - slot.getItem().getCount();
			if (moved > 0) {
				itemsMoved += moved;
				progress = true;
				deposited = true;
			}
		}
		return deposited;
	}

	/**
	 * Picks up misplaced stacks into empty player inventory slots while their
	 * category has room somewhere.
	 *
	 * @return whether any stack was picked up
	 */
	private boolean withdraw(AbstractContainerMenu menu, SortedChest chest) {
		boolean withdrew = false;
		for (Slot slot : menu.slots) {
			if (!chest.holds(slot) || !slot.hasItem()) {
				continue;
			}
			SortCategory category = classifier.classify(slot.getItem());
			if (!isMisplaced(category, chest) || freeSlotsFor(category) <= 0) {
				continue;
			}
			Slot target = emptyPlayerSlot(menu);
			if (target == null) {
				break;
			}

			click(menu, slot, ContainerInput.PICKUP);
			if (menu.getCarried().isEmpty()) {
				continue;
			}
			click(menu, target, ContainerInput.PICKUP);
			if (!menu.getCarried().isEmpty()) {
				// Put it back rather than leave it on the cursor.
				click(menu, slot, ContainerInput.PICKUP);
				break;
			}
			carriedSlots.add(target.getContainerSlot());
			progress = true;
			withdrew = true;
		}
		return withdrew;
	}

	/**
	 * Prefers the main inventory, so the hotbar fills last. Locked slots stay
	 * empty.
	 */
	private Slot emptyPlayerSlot(AbstractContainerMenu menu) {
		Inventory inventory = client.player.getInventory();
		for (int offset = 0; offset < Inventory.INVENTORY_SIZE; offset++) {
			int inventorySlot = (offset + Inventory.SELECTION_SIZE) % Inventory.INVENTORY_SIZE;
			if (inventory.getItem(inventorySlot).isEmpty()
				&& !carriedSlots.contains(inventorySlot)
				&& !SlotLockClient.isLocked(inventorySlot)) {
				Slot slot = playerSlot(menu, inventorySlot);
				if (slot != null) {
					return slot;
				}
			}
		}
		return null;
	}

	private static Slot playerSlot(AbstractContainerMenu menu, int inventorySlot) {
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory && slot.getContainerSlot() == inventorySlot) {
				return slot;
			}
		}
		return null;
	}

	private void click(AbstractContainerMenu menu, Slot slot, ContainerInput input) {
		client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, input, client.player);
	}

	private static final class SortedChest {
		private final List<ItemStack> items = new ArrayList<>();
		private final boolean backpack;
		private SortCategory category;
		private boolean received;

		private SortedChest(boolean backpack) {
			this.backpack = backpack;
		}

		/**
		 * Whether a menu slot is one of this container's own slots.
		 */
		private boolean holds(Slot slot) {
			return backpack ? TravelersBackpack.isStorageSlot(slot) : !(slot.container instanceof Inventory);
		}

		private void update(AbstractContainerMenu menu) {
			items.clear();
			for (Slot slot : menu.slots) {
				if (holds(slot)) {
					items.add(slot.getItem().copy());
				}
			}
		}

		private int emptySlots() {
			int empty = 0;
			for (ItemStack stack : items) {
				if (stack.isEmpty()) {
					empty++;
				}
			}
			return empty;
		}
	}

	private record Claim(SortedChest chest, SortCategory category, int count) {
	}
}
