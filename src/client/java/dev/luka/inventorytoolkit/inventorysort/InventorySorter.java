package dev.luka.inventorytoolkit.inventorysort;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * Sorts a group of slots with ordinary left clicks, as a player would: first
 * partial stacks of the same item are merged, then every slot in turn
 * receives the stack that belongs there, swapped in from further along.
 */
final class InventorySorter {
	enum Result {
		SORTED,
		ALREADY_SORTED,
		/** The menu stopped matching what the clicks should have done. */
		INTERRUPTED
	}

	private final Minecraft client;
	private final AbstractContainerMenu menu;
	private final List<Slot> slots;
	private boolean clicked;

	/**
	 * @param slots the slots to sort, in the order they should fill
	 */
	InventorySorter(Minecraft client, AbstractContainerMenu menu, List<Slot> slots) {
		this.client = client;
		this.menu = menu;
		this.slots = slots;
	}

	Result sort(Comparator<ItemStack> order) {
		if (!mergeStacks()) {
			return Result.INTERRUPTED;
		}
		List<ItemStack> wanted = slots.stream()
			.map(Slot::getItem)
			.filter(stack -> !stack.isEmpty())
			.map(ItemStack::copy)
			.sorted(order)
			.toList();

		for (int index = 0; index < wanted.size(); index++) {
			Slot target = slots.get(index);
			ItemStack want = wanted.get(index);
			if (matches(target.getItem(), want)) {
				continue;
			}
			Slot source = find(want, index + 1);
			if (source == null) {
				return Result.INTERRUPTED;
			}
			// A different item swaps with the carried stack; a partial stack of
			// the same item fills up and leaves the rest on the cursor. Either
			// way the leftover goes back where the carried stack came from.
			click(source);
			click(target);
			if (!menu.getCarried().isEmpty()) {
				click(source);
			}
			if (!menu.getCarried().isEmpty() || !matches(target.getItem(), want)) {
				returnCarried();
				return Result.INTERRUPTED;
			}
		}
		return clicked ? Result.SORTED : Result.ALREADY_SORTED;
	}

	/**
	 * Tops up every partial stack from later stacks of the same item, so each
	 * item ends up with at most one partial stack.
	 */
	private boolean mergeStacks() {
		for (int index = 0; index < slots.size(); index++) {
			Slot target = slots.get(index);
			for (int later = index + 1; later < slots.size() && canTakeMore(target); later++) {
				Slot source = slots.get(later);
				if (!source.hasItem() || !ItemStack.isSameItemSameComponents(target.getItem(), source.getItem())) {
					continue;
				}
				click(source);
				click(target);
				if (!menu.getCarried().isEmpty()) {
					click(source);
				}
				if (!menu.getCarried().isEmpty()) {
					returnCarried();
					return false;
				}
			}
		}
		return true;
	}

	private boolean canTakeMore(Slot slot) {
		ItemStack stack = slot.getItem();
		return !stack.isEmpty()
			&& stack.getCount() < Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
	}

	private Slot find(ItemStack want, int from) {
		for (int index = from; index < slots.size(); index++) {
			if (matches(slots.get(index).getItem(), want)) {
				return slots.get(index);
			}
		}
		return null;
	}

	/**
	 * Never leaves anything on the cursor: puts it into the first empty slot.
	 */
	private void returnCarried() {
		for (Slot slot : slots) {
			if (menu.getCarried().isEmpty()) {
				return;
			}
			if (!slot.hasItem()) {
				click(slot);
			}
		}
	}

	private void click(Slot slot) {
		clicked = true;
		// A left click with a bundle on either side puts one item into the
		// bundle instead of swapping; a right click swaps different items
		// just the same without that.
		ItemStack carried = menu.getCarried();
		boolean swap = !carried.isEmpty()
			&& slot.hasItem()
			&& !ItemStack.isSameItemSameComponents(carried, slot.getItem());
		client.gameMode.handleContainerInput(menu.containerId, slot.index, swap ? 1 : 0, ContainerInput.PICKUP, client.player);
	}

	private static boolean matches(ItemStack stack, ItemStack want) {
		return ItemStack.isSameItemSameComponents(stack, want) && stack.getCount() == want.getCount();
	}
}
