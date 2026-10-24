package dev.luka.inventorytoolkit.inventorysort;

import dev.luka.inventorytoolkit.quickstack.SortCategory;
import dev.luka.inventorytoolkit.quickstack.SortClassifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

import java.awt.Color;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sort orders, matching ClientSort's: each order compares its own key
 * first, and stacks it considers equal are ordered fullest first, renamed
 * before plain, then by tooltip text, dye color, and damage.
 */
final class SortOrders {
	private static final int UNDYED_LEATHER = -6265536;

	private SortOrders() {
	}

	/**
	 * @param stacks every stack being sorted, for orders that look at totals
	 */
	static Comparator<ItemStack> comparator(
		InventorySortConfig.Order order,
		List<ItemStack> stacks,
		SortClassifier classifier,
		Level level
	) {
		Comparator<ItemStack> equalItems = equalItems(level);
		Comparator<ItemStack> comparator = switch (order) {
			case CREATIVE -> Comparator.<ItemStack>comparingInt(CreativeOrder::position).thenComparing(equalItems);
			case QUANTITY -> {
				Map<Item, Integer> totals = new HashMap<>();
				for (ItemStack stack : stacks) {
					totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
				yield Comparator.<ItemStack>comparingInt(stack -> -totals.getOrDefault(stack.getItem(), 0))
					.thenComparing((a, b) -> ItemStack.isSameItemSameComponents(a, b)
						? equalItems.compare(a, b)
						: equalItems.compare(a.copyWithCount(1), b.copyWithCount(1)));
			}
			case ALPHABET -> Comparator.<ItemStack, String>comparing(
					stack -> stack.getHoverName().getString(),
					String.CASE_INSENSITIVE_ORDER
				)
				.thenComparing(equalItems);
			case RAW_ID -> Comparator.<ItemStack>comparingInt(stack -> BuiltInRegistries.ITEM.getId(stack.getItem()))
				.thenComparing(equalItems);
			case CATEGORY -> Comparator.<ItemStack>comparingInt(stack -> categoryIndex(classifier.classify(stack)))
				.thenComparingInt(CreativeOrder::position)
				.thenComparing(equalItems);
		};
		// Keeps identical stacks together when everything else ties.
		return comparator.thenComparingInt(ItemStack::hashItemAndComponents);
	}

	private static Comparator<ItemStack> equalItems(Level level) {
		Map<ItemStack, List<String>> tooltips = new IdentityHashMap<>();
		Item.TooltipContext context = Item.TooltipContext.of(level);
		return Comparator.<ItemStack>comparingInt(stack -> -stack.getCount())
			.thenComparing(stack -> !stack.has(DataComponents.CUSTOM_NAME))
			.thenComparing((a, b) -> compareTooltips(
				tooltips.computeIfAbsent(a, stack -> tooltipText(stack, context)),
				tooltips.computeIfAbsent(b, stack -> tooltipText(stack, context))
			))
			.thenComparing(SortOrders::compareDyeColors)
			.thenComparingInt(ItemStack::getDamageValue);
	}

	private static List<String> tooltipText(ItemStack stack, Item.TooltipContext context) {
		return stack.getTooltipLines(context, null, TooltipFlag.Default.NORMAL).stream()
			.map(Component::getString)
			.toList();
	}

	private static int compareTooltips(List<String> a, List<String> b) {
		for (int line = 0; line < Math.min(a.size(), b.size()); line++) {
			int compared = a.get(line).compareToIgnoreCase(b.get(line));
			if (compared != 0) {
				return compared;
			}
		}
		return Integer.compare(a.size(), b.size());
	}

	/**
	 * Dyeable items are ordered by hue, then saturation, then brightness.
	 */
	private static int compareDyeColors(ItemStack a, ItemStack b) {
		if (!a.is(b.getItem()) || !a.getItem().getDefaultInstance().has(DataComponents.DYED_COLOR)) {
			return 0;
		}
		float[] hsbA = hsb(DyedItemColor.getOrDefault(a, UNDYED_LEATHER));
		float[] hsbB = hsb(DyedItemColor.getOrDefault(b, UNDYED_LEATHER));
		for (int component = 0; component < 3; component++) {
			int compared = Float.compare(hsbA[component], hsbB[component]);
			if (compared != 0) {
				return compared;
			}
		}
		return 0;
	}

	private static float[] hsb(int color) {
		return Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, null);
	}

	private static int categoryIndex(SortCategory category) {
		return category == null ? SortCategory.values().length : category.ordinal();
	}
}
