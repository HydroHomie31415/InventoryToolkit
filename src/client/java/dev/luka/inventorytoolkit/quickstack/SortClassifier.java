package dev.luka.inventorytoolkit.quickstack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies the custom "item_id=category" rules from the configuration on top
 * of the built-in categories. The category "none" keeps an item where it is.
 */
public final class SortClassifier {
	public static final String NONE = "none";

	private final Map<Item, Optional<SortCategory>> overrides = new HashMap<>();

	public SortClassifier(List<String> rules) {
		for (String rule : rules) {
			Rule parsed = parseRule(rule);
			if (parsed != null) {
				overrides.put(parsed.item(), Optional.ofNullable(parsed.category()));
			}
		}
	}

	public SortCategory classify(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Optional<SortCategory> override = overrides.get(stack.getItem());
		return override != null ? override.orElse(null) : SortCategory.classify(stack);
	}

	/**
	 * Whether this stack has no built-in or custom category. Explicit "none"
	 * rules are intentional exclusions, so they are not recorded as unknown.
	 */
	public boolean isUncategorized(ItemStack stack) {
		return !stack.isEmpty()
			&& !overrides.containsKey(stack.getItem())
			&& SortCategory.classify(stack) == null;
	}

	/**
	 * @return the parsed rule, or null when the item or category is unknown
	 */
	public static Rule parseRule(String rule) {
		int separator = rule.indexOf('=');
		if (separator <= 0) {
			return null;
		}
		Identifier id = Identifier.tryParse(rule.substring(0, separator).trim());
		String categoryId = rule.substring(separator + 1).trim();
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}
		SortCategory category = SortCategory.fromId(categoryId);
		if (category == null && !categoryId.equals(NONE)) {
			return null;
		}
		return new Rule(BuiltInRegistries.ITEM.getValue(id), category);
	}

	public record Rule(Item item, SortCategory category) {
	}
}
