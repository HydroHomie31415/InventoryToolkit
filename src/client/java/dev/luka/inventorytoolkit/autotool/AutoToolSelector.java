package dev.luka.inventorytoolkit.autotool;

import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.CorrectToolPolicy;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.DurabilityPreference;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.ListMode;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.LootPreference;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.LootPreferenceStrength;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

final class AutoToolSelector {
	private static final double SPEED_EPSILON = 0.0001;

	private AutoToolSelector() {
	}

	static Selection findBest(
		Inventory inventory,
		BlockState state,
		AutoToolSwitchConfig config
	) {
		Selection best = null;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (!config.isHotbarSlotEnabled(slot)) {
				continue;
			}
			Selection candidate = assess(slot, inventory.getItem(slot), state, config);
			if (candidate != null && (best == null || compare(candidate, best, config) > 0)) {
				best = candidate;
			}
		}

		if (best == null) {
			return null;
		}

		int currentSlot = inventory.getSelectedSlot();
		if (best.slot() == currentSlot) {
			return best;
		}

		Selection current = assess(currentSlot, inventory.getItem(currentSlot), state, config);
		if (current != null) {
			double requiredSpeed = current.speed()
				* (1.0 + config.minimumSpeedImprovementPercent / 100.0);
			boolean bestHasSelectionPriority = hasSelectionPriority(best, current, config);
			if (!bestHasSelectionPriority && best.speed() + SPEED_EPSILON < requiredSpeed) {
				return current;
			}
		}
		return best;
	}

	static boolean isBlockAllowed(BlockState state, AutoToolSwitchConfig config) {
		boolean matched = matchesBlockRules(state, config.blockRules);
		return config.blockListMode == ListMode.WHITELIST ? matched : !matched;
	}

	private static Selection assess(
		int slot,
		ItemStack stack,
		BlockState state,
		AutoToolSwitchConfig config
	) {
		if (stack.isEmpty() || !isItemAllowed(stack, config) || !isToolClassAllowed(stack, config)) {
			return null;
		}

		float speed = stack.getDestroySpeed(state);
		boolean correct = stack.isCorrectToolForDrops(state);
		if (speed + SPEED_EPSILON < config.minimumMiningSpeed) {
			return null;
		}
		if (config.correctToolPolicy == CorrectToolPolicy.REQUIRE_WHEN_NEEDED
			&& state.requiresCorrectToolForDrops()
			&& !correct) {
			return null;
		}
		if (!hasSafeDurability(stack, config)) {
			return null;
		}

		boolean preferred = matchesLootPreference(stack, config.lootPreference);
		if (config.lootPreference != LootPreference.NONE
			&& config.lootPreferenceStrength == LootPreferenceStrength.REQUIRE
			&& !preferred) {
			return null;
		}

		return new Selection(
			slot,
			stack,
			speed,
			correct,
			preferred,
			remainingDurabilityRatio(stack)
		);
	}

	private static int compare(
		Selection first,
		Selection second,
		AutoToolSwitchConfig config
	) {
		if (config.correctToolPolicy == CorrectToolPolicy.PREFER
			&& first.correctForDrops() != second.correctForDrops()) {
			return Boolean.compare(first.correctForDrops(), second.correctForDrops());
		}
		if (config.lootPreference != LootPreference.NONE
			&& config.lootPreferenceStrength == LootPreferenceStrength.PREFER
			&& first.matchesLootPreference() != second.matchesLootPreference()) {
			return Boolean.compare(first.matchesLootPreference(), second.matchesLootPreference());
		}

		int speedComparison = Float.compare(first.speed(), second.speed());
		if (speedComparison != 0) {
			return speedComparison;
		}
		if (config.lootPreference != LootPreference.NONE
			&& config.lootPreferenceStrength == LootPreferenceStrength.TIEBREAKER
			&& first.matchesLootPreference() != second.matchesLootPreference()) {
			return Boolean.compare(first.matchesLootPreference(), second.matchesLootPreference());
		}

		int durabilityComparison = switch (config.durabilityPreference) {
			case MOST_REMAINING -> Double.compare(
				first.remainingDurabilityRatio(),
				second.remainingDurabilityRatio()
			);
			case LEAST_REMAINING -> Double.compare(
				second.remainingDurabilityRatio(),
				first.remainingDurabilityRatio()
			);
			case HOTBAR_ORDER -> 0;
		};
		if (durabilityComparison != 0) {
			return durabilityComparison;
		}
		return Integer.compare(second.slot(), first.slot());
	}

	private static boolean hasSelectionPriority(
		Selection best,
		Selection current,
		AutoToolSwitchConfig config
	) {
		if (config.correctToolPolicy == CorrectToolPolicy.PREFER
			&& best.correctForDrops()
			&& !current.correctForDrops()) {
			return true;
		}
		return config.lootPreference != LootPreference.NONE
			&& config.lootPreferenceStrength == LootPreferenceStrength.PREFER
			&& best.matchesLootPreference()
			&& !current.matchesLootPreference();
	}

	private static boolean isToolClassAllowed(ItemStack stack, AutoToolSwitchConfig config) {
		if (stack.is(ItemTags.PICKAXES)) {
			return config.allowPickaxes;
		}
		if (stack.is(ItemTags.AXES)) {
			return config.allowAxes;
		}
		if (stack.is(ItemTags.SHOVELS)) {
			return config.allowShovels;
		}
		if (stack.is(ItemTags.HOES)) {
			return config.allowHoes;
		}
		if (stack.is(Items.SHEARS)) {
			return config.allowShears;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return config.allowSwords;
		}
		return config.allowOtherEffectiveItems;
	}

	private static boolean hasSafeDurability(ItemStack stack, AutoToolSwitchConfig config) {
		if (!stack.isDamageableItem()) {
			return true;
		}
		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		if (remaining < config.minimumRemainingDurability) {
			return false;
		}
		double percent = remaining * 100.0 / stack.getMaxDamage();
		return percent + SPEED_EPSILON >= config.minimumRemainingDurabilityPercent;
	}

	private static boolean matchesLootPreference(ItemStack stack, LootPreference preference) {
		return switch (preference) {
			case NONE -> false;
			case SILK_TOUCH -> stack.getEnchantments().entrySet().stream()
				.anyMatch(entry -> entry.getKey().is(Enchantments.SILK_TOUCH));
			case FORTUNE -> stack.getEnchantments().entrySet().stream()
				.anyMatch(entry -> entry.getKey().is(Enchantments.FORTUNE));
			case UNENCHANTED -> stack.getEnchantments().isEmpty();
		};
	}

	private static boolean isItemAllowed(ItemStack stack, AutoToolSwitchConfig config) {
		boolean matched = matchesItemRules(stack, config.itemRules);
		return config.itemListMode == ListMode.WHITELIST ? matched : !matched;
	}

	private static boolean matchesBlockRules(BlockState state, Set<String> rules) {
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		for (String rule : rules) {
			if (rule.startsWith("#")) {
				Identifier tagId = Identifier.tryParse(rule.substring(1));
				if (tagId != null && state.is(TagKey.create(Registries.BLOCK, tagId))) {
					return true;
				}
			} else if (blockId.toString().equals(rule)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesItemRules(ItemStack stack, Set<String> rules) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (String rule : rules) {
			if (rule.startsWith("#")) {
				Identifier tagId = Identifier.tryParse(rule.substring(1));
				if (tagId != null && stack.is(TagKey.create(Registries.ITEM, tagId))) {
					return true;
				}
			} else if (itemId.toString().equals(rule)) {
				return true;
			}
		}
		return false;
	}

	private static double remainingDurabilityRatio(ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return 1.0;
		}
		return (stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage();
	}

	record Selection(
		int slot,
		ItemStack stack,
		float speed,
		boolean correctForDrops,
		boolean matchesLootPreference,
		double remainingDurabilityRatio
	) {
	}
}
