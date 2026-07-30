package dev.hydro.hydrosbundles;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BundleRouter {
	private final HydrosBundlesConfig config;
	private final List<RoutingRule> rules;

	public BundleRouter(HydrosBundlesConfig config) {
		this.config = config;
		this.rules = parseRules(config.routingRules);
	}

	public int findBundle(Inventory inventory, ItemStack source, int sourceIndex) {
		RoutingRule matchedRule = rules.stream().filter(rule -> rule.matches(source)).findFirst().orElse(null);
		List<Integer> candidates = new ArrayList<>();

		for (int bundleIndex : BundleInventory.bundleInventoryIndices(inventory)) {
			if (bundleIndex == sourceIndex) {
				continue;
			}
			ItemStack bundle = inventory.getItem(bundleIndex);
			if (!BundleInventory.canAccept(bundle, source)) {
				continue;
			}
			if (matchedRule != null) {
				if (nameMatches(bundle, matchedRule.bundleName())) {
					candidates.add(bundleIndex);
				}
				continue;
			}

			switch (config.unmatchedBehavior) {
				case SKIP -> {
				}
				case ANY_UNNAMED_BUNDLE -> {
					if (bundle.getCustomName() == null) {
						candidates.add(bundleIndex);
					}
				}
				case ANY_BUNDLE -> candidates.add(bundleIndex);
			}
		}

		return BundleInventory.insertionOrder(
			inventory,
			candidates,
			config.preferMostFilledBundle
		).stream().findFirst().orElse(-1);
	}

	private boolean nameMatches(ItemStack bundle, String configuredName) {
		if (bundle.getCustomName() == null) {
			return false;
		}
		String actual = bundle.getCustomName().getString().trim();
		String expected = configuredName.trim();
		if (!config.caseSensitiveBundleNames) {
			actual = actual.toLowerCase(Locale.ROOT);
			expected = expected.toLowerCase(Locale.ROOT);
		}
		return config.bundleNameMatch == BundleNameMatch.EXACT
			? actual.equals(expected)
			: actual.contains(expected);
	}

	private static List<RoutingRule> parseRules(List<String> sourceRules) {
		List<RoutingRule> result = new ArrayList<>();
		for (String sourceRule : sourceRules) {
			try {
				result.add(RoutingRule.parse(sourceRule));
			} catch (IllegalArgumentException exception) {
				HydrosBundlesClient.LOGGER.warn("Ignoring invalid routing rule '{}': {}", sourceRule, exception.getMessage());
			}
		}
		return List.copyOf(result);
	}
}

