package dev.luka.inventorytoolkit.quickstack;

import dev.hydro.hydrosbundles.RedstoneClassifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The kinds of chest that nearby sorting fills. Items that fit none of them
 * (wool, dyes, glass, shulker boxes, ...) are never moved unless a
 * custom rule gives them a category.
 */
public enum SortCategory {
	WOOD("wood", "wood", "log", "timber", "tree"),
	STONE("stone", "stone", "rock", "terrain"),
	REDSTONE("redstone", "redstone"),
	MOB_DROPS("mob_drops", "mobdrops", "mob", "drops", "loot"),
	FARMING("farming", "farm", "food", "crop"),
	NETHER("nether", "nether"),
	END("end", "end"),
	EQUIPMENT("equipment", "equipment", "gear", "tool", "armor", "armour", "weapon", "combat"),
	VALUABLES("valuables", "valuable", "ore", "ingot", "gem", "mineral", "treasure");

	private static final Set<String> VALUABLE_IDS = Set.of(
		"diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard", "coal", "netherite_scrap",
		"ancient_debris", "iron_block", "gold_block", "copper_block", "diamond_block", "emerald_block",
		"lapis_block", "netherite_block", "coal_block"
	);

	private static final Set<String> WOOD_IDS = Set.of(
		"stick", "bamboo", "ladder", "bowl", "chest", "barrel", "crafting_table", "mangrove_roots"
	);
	private static final Set<String> MOB_DROP_IDS = Set.of(
		"rotten_flesh", "bone", "bone_block", "string", "cobweb", "spider_eye",
		"fermented_spider_eye", "gunpowder", "ender_pearl", "slime_ball", "slime_block", "leather",
		"rabbit_hide", "rabbit_foot", "feather", "phantom_membrane", "ink_sac", "glow_ink_sac",
		"prismarine_shard", "prismarine_crystals", "nautilus_shell", "turtle_scute", "armadillo_scute",
		"breeze_rod", "wind_charge", "goat_horn"
	);
	private static final List<String> NETHER_PARTS = List.of(
		"nether", "quartz", "blackstone", "basalt", "soul_", "crimson", "warped", "glowstone", "magma",
		"blaze", "ghast", "shroomlight", "obsidian", "respawn_anchor", "wither_rose"
	);
	private static final List<String> END_PARTS = List.of(
		"end_stone", "purpur", "chorus", "end_rod", "end_crystal", "ender_eye", "ender_chest",
		"dragon_", "shulker_shell"
	);
	private static final Set<String> FARMING_IDS = Set.of(
		"wheat", "sugar_cane", "sugar", "cactus", "pumpkin", "carved_pumpkin", "melon", "hay_block",
		"cocoa_beans", "kelp", "dried_kelp_block", "honeycomb", "honey_bottle", "honey_block", "cake",
		"lily_pad", "vine", "composter", "pitcher_pod", "sea_pickle", "moss_block", "moss_carpet",
		"bone_meal"
	);
	private static final List<String> STONE_PARTS = List.of(
		"stone", "cobble", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "brick",
		"gravel", "dirt", "sand", "clay", "mud", "flint", "grass_block", "podzol", "mycelium"
	);

	private final String id;
	private final List<String> nameAliases;

	SortCategory(String id, String... nameAliases) {
		this.id = id;
		this.nameAliases = List.of(nameAliases);
	}

	public String id() {
		return id;
	}

	public Component displayName() {
		return Component.translatable("sort_category.inventory_toolkit." + id);
	}

	public static SortCategory fromId(String value) {
		for (SortCategory category : values()) {
			if (category.id.equals(value)) {
				return category;
			}
		}
		return null;
	}

	/**
	 * Matches a container name such as "Wood", "Redstone stuff" or "Mob Drops".
	 * The longest alias at the start of the name wins, so "Redstone" is never
	 * read as stone and "Endstone" still counts as End.
	 */
	public static SortCategory fromContainerName(String name) {
		String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		SortCategory best = null;
		int bestLength = 0;
		for (SortCategory category : values()) {
			for (String alias : category.nameAliases) {
				if (alias.length() > bestLength && normalized.startsWith(alias)) {
					best = category;
					bestLength = alias.length();
				}
			}
		}
		return best;
	}

	/**
	 * The default category of a stack, or null when it fits none. The checks
	 * run in order, so for example a netherite pickaxe is equipment rather
	 * than valuables, and redstone ore is valuables rather than redstone.
	 */
	public static SortCategory classify(ItemStack stack) {
		if (stack.isEmpty() || stack.is(ItemTags.SHULKER_BOXES) || stack.is(ItemTags.BUNDLES)) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

		if (isEquipment(stack, path)) {
			return EQUIPMENT;
		}
		if (isValuable(stack, path)) {
			return VALUABLES;
		}
		if (RedstoneClassifier.matches(stack)
			|| stack.is(ItemTags.RAILS)
			|| path.contains("redstone")
			|| path.contains("piston")
			|| path.contains("copper_bulb")) {
			return REDSTONE;
		}
		if (isWood(stack, path)) {
			return WOOD;
		}
		if (MOB_DROP_IDS.contains(path)
			|| stack.is(ItemTags.SKULLS)
			|| stack.is(ItemTags.CREEPER_DROP_MUSIC_DISCS)) {
			return MOB_DROPS;
		}
		if (containsAny(path, NETHER_PARTS)) {
			return NETHER;
		}
		if (containsAny(path, END_PARTS)) {
			return END;
		}
		if (isFarming(stack, path)) {
			return FARMING;
		}
		if (containsAny(path, STONE_PARTS)
			|| stack.is(ItemTags.STONE_BRICKS)
			|| stack.is(ItemTags.STONE_CRAFTING_MATERIALS)
			|| stack.is(ItemTags.DIRT)
			|| stack.is(ItemTags.SAND)
			|| stack.is(ItemTags.MUD)) {
			return STONE;
		}
		return null;
	}

	private static boolean isEquipment(ItemStack stack, String path) {
		return stack.has(DataComponents.TOOL)
			|| stack.has(DataComponents.WEAPON)
			|| stack.has(DataComponents.BLOCKS_ATTACKS)
			|| stack.has(DataComponents.STORED_ENCHANTMENTS)
			|| stack.isDamageableItem()
			|| (stack.has(DataComponents.EQUIPPABLE) && !(stack.getItem() instanceof BlockItem))
			|| stack.is(ItemTags.ARROWS)
			|| path.equals("totem_of_undying")
			|| path.equals("firework_rocket");
	}

	/**
	 * Ores, raw ores, ingots, nuggets, gems, and their storage blocks.
	 */
	private static boolean isValuable(ItemStack stack, String path) {
		return VALUABLE_IDS.contains(path)
			|| path.endsWith("_ore")
			|| path.endsWith("_ingot")
			|| path.startsWith("raw_")
			|| stack.is(ItemTags.METAL_NUGGETS);
	}

	private static boolean isWood(ItemStack stack, String path) {
		return WOOD_IDS.contains(path)
			|| stack.is(ItemTags.LOGS)
			|| stack.is(ItemTags.PLANKS)
			|| stack.is(ItemTags.WOODEN_SLABS)
			|| stack.is(ItemTags.WOODEN_STAIRS)
			|| stack.is(ItemTags.WOODEN_FENCES)
			|| stack.is(ItemTags.FENCE_GATES)
			|| stack.is(ItemTags.WOODEN_DOORS)
			|| stack.is(ItemTags.WOODEN_TRAPDOORS)
			|| stack.is(ItemTags.WOODEN_SHELVES)
			|| stack.is(ItemTags.LEAVES)
			|| stack.is(ItemTags.BAMBOO_BLOCKS)
			|| stack.is(ItemTags.SIGNS)
			|| stack.is(ItemTags.HANGING_SIGNS)
			|| stack.is(ItemTags.BOATS)
			|| stack.is(ItemTags.CHEST_BOATS);
	}

	private static boolean isFarming(ItemStack stack, String path) {
		return FARMING_IDS.contains(path)
			|| path.endsWith("_seeds")
			|| stack.has(DataComponents.FOOD)
			|| stack.is(ItemTags.SAPLINGS)
			|| stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
			|| stack.is(ItemTags.BEE_FOOD)
			|| stack.is(ItemTags.MUSHROOMS)
			|| stack.is(ItemTags.EGGS)
			|| stack.is(ItemTags.FISHES);
	}

	private static boolean containsAny(String path, List<String> parts) {
		for (String part : parts) {
			if (path.contains(part)) {
				return true;
			}
		}
		return false;
	}
}
