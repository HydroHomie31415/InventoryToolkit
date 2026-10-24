package dev.luka.inventorytoolkit.inventorysort;

import dev.luka.inventorytoolkit.mixin.client.CreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Positions of items in the creative inventory's search tab, which lists
 * every item in the game's own order. Variants such as enchanted books and
 * potions have their own positions; other stacks use the position of their
 * item's first entry.
 */
final class CreativeOrder {
	private static final Map<StackKey, Integer> variantPositions = new HashMap<>();
	private static final Map<Item, Integer> itemPositions = new HashMap<>();
	private static ClientLevel builtFor;

	private CreativeOrder() {
	}

	/**
	 * @return the stack's position, or {@link Integer#MAX_VALUE} for items
	 *     the creative inventory does not list
	 */
	static int position(ItemStack stack) {
		refresh();
		Integer variant = variantPositions.get(new StackKey(stack));
		if (variant != null) {
			return variant;
		}
		return itemPositions.getOrDefault(stack.getItem(), Integer.MAX_VALUE);
	}

	/**
	 * Rebuilds the positions for each new level, since enabled features and
	 * registries can differ between worlds and servers.
	 */
	private static void refresh() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || level == builtFor) {
			return;
		}
		List<ItemStack> stacks;
		try {
			CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
			stacks = CreativeModeTabs.searchTab().getDisplayItems().stream().map(ItemStack::copy).toList();
		} finally {
			// Forces the creative screen to rebuild with its own parameters
			// and refresh its search index the next time it opens.
			CreativeModeTabsAccessor.inventoryToolkit$setCachedParameters(null);
		}

		variantPositions.clear();
		itemPositions.clear();
		for (int position = 0; position < stacks.size(); position++) {
			ItemStack stack = stacks.get(position);
			variantPositions.putIfAbsent(new StackKey(stack), position);
			itemPositions.putIfAbsent(stack.getItem(), position);
		}
		builtFor = level;
	}

	private static final class StackKey {
		private final ItemStack stack;
		private final int hash;

		private StackKey(ItemStack stack) {
			this.stack = stack;
			this.hash = ItemStack.hashItemAndComponents(stack);
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof StackKey other && ItemStack.isSameItemSameComponents(stack, other.stack);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}
}
