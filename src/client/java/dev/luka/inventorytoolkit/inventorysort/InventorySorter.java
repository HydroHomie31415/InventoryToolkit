package dev.luka.inventorytoolkit.inventorysort;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * Sorts a group of slots with ordinary left clicks, as a player would: first
 * partial stacks of the same item are merged, then every slot in turn
 * receives the stack that belongs there, swapped in from further along.
 * Stacks such as bundles and backpacks, which take in items clicked onto
 * them, are swapped through the offhand instead.
 */
final class InventorySorter {
	enum Result {
		SORTED,
		ALREADY_SORTED,
		/** The menu stopped matching what the clicks should have done. */
		INTERRUPTED
	}

	private static final ClassValue<Boolean> CLICK_BEHAVIOUR = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			return overridesClicks(type);
		}
	};

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
			if (hasClickBehaviour(source.getItem()) || hasClickBehaviour(target.getItem())) {
				if (!swapThroughOffhand(source, target)) {
					return Result.INTERRUPTED;
				}
			} else {
				// A different item swaps with the carried stack; a partial stack
				// of the same item fills up and leaves the rest on the cursor.
				// Either way the leftover goes back where the carried stack
				// came from.
				click(source);
				click(target);
				if (!menu.getCarried().isEmpty()) {
					click(source);
				}
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
				if (!source.hasItem()
					|| !ItemStack.isSameItemSameComponents(target.getItem(), source.getItem())
					|| hasClickBehaviour(source.getItem())) {
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

	/**
	 * Swaps two stacks by way of the offhand, as the offhand key does, so
	 * neither stack's own click behaviour comes into play. The offhand ends up
	 * holding what it held before.
	 *
	 * @return whether the offhand could hold both stacks in turn
	 */
	private boolean swapThroughOffhand(Slot source, Slot target) {
		ItemStack offhand = client.player.getInventory().getItem(Inventory.SLOT_OFFHAND);
		if (!offhand.isEmpty() && !(fits(source, offhand) && fits(target, offhand))) {
			return false;
		}
		ItemStack sourceItem = source.getItem();
		if (!fits(target, sourceItem) || !fits(source, target.getItem())) {
			return false;
		}
		swapWithOffhand(source);
		swapWithOffhand(target);
		swapWithOffhand(source);
		return true;
	}

	private static boolean fits(Slot slot, ItemStack stack) {
		return stack.isEmpty() || (slot.mayPlace(stack) && stack.getCount() <= slot.getMaxStackSize(stack));
	}

	private void swapWithOffhand(Slot slot) {
		clicked = true;
		client.gameMode.handleContainerInput(
			menu.containerId,
			slot.index,
			Inventory.SLOT_OFFHAND,
			ContainerInput.SWAP,
			client.player
		);
	}

	private void click(Slot slot) {
		clicked = true;
		client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, client.player);
	}

	/**
	 * Whether clicking with or onto the stack can do something other than
	 * pick up, place, or swap it, the way bundles and backpacks take in the
	 * items clicked onto them.
	 */
	private static boolean hasClickBehaviour(ItemStack stack) {
		return !stack.isEmpty() && CLICK_BEHAVIOUR.get(stack.getItem().getClass());
	}

	private static boolean overridesClicks(Class<?> type) {
		try {
			return type.getMethod(
					"overrideStackedOnOther", ItemStack.class, Slot.class, ClickAction.class, Player.class
				).getDeclaringClass() != Item.class
				|| type.getMethod(
					"overrideOtherStackedOnMe",
					ItemStack.class,
					ItemStack.class,
					Slot.class,
					ClickAction.class,
					Player.class,
					SlotAccess.class
				).getDeclaringClass() != Item.class;
		} catch (NoSuchMethodException exception) {
			return true;
		}
	}

	private static boolean matches(ItemStack stack, ItemStack want) {
		return ItemStack.isSameItemSameComponents(stack, want) && stack.getCount() == want.getCount();
	}
}
