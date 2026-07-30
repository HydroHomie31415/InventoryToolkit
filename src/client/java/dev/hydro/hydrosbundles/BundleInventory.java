package dev.hydro.hydrosbundles;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BundleInventory {
	public static final int PLAYER_STORAGE_SIZE = 36;

	private BundleInventory() {
	}

	public static boolean isBundle(ItemStack stack) {
		return !stack.isEmpty()
			&& stack.is(ItemTags.BUNDLES)
			&& stack.get(DataComponents.BUNDLE_CONTENTS) != null;
	}

	public static BundleContents contents(ItemStack bundle) {
		return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
	}

	public static boolean canAccept(ItemStack bundle, ItemStack candidate) {
		if (!isBundle(bundle) || candidate.isEmpty() || !BundleContents.canItemBeInBundle(candidate)) {
			return false;
		}
		BundleContents.Mutable simulated = new BundleContents.Mutable(contents(bundle));
		return simulated.tryInsert(candidate.copy()) > 0;
	}

	public static List<Integer> bundleInventoryIndices(Inventory inventory) {
		List<Integer> result = new ArrayList<>();
		for (int index = 0; index < PLAYER_STORAGE_SIZE; index++) {
			if (isBundle(inventory.getItem(index))) {
				result.add(index);
			}
		}
		return result;
	}

	public static Slot menuSlot(AbstractContainerMenu menu, Inventory inventory, int inventoryIndex) {
		for (Slot slot : menu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() == inventoryIndex) {
				return slot;
			}
		}
		return null;
	}

	public static List<Integer> insertionOrder(
		Inventory inventory,
		List<Integer> candidates,
		boolean preferMostFilled
	) {
		Comparator<Integer> comparator = Comparator.comparingDouble(
			index -> BundleItem.getFullnessDisplay(inventory.getItem(index))
		);
		if (preferMostFilled) {
			comparator = comparator.reversed();
		}
		return candidates.stream().sorted(comparator.thenComparingInt(Integer::intValue)).toList();
	}
}

