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
 * The kinds of chest that nearby sorting fills. Vanilla items have a home
 * category, with shulker boxes and bundles intentionally left alone; unknown
 * items from other mods stay put until given a custom rule.
 */
public enum SortCategory {
	WOOD("wood", "wood", "log", "timber", "tree"),
	STONE("stone", "stone", "rock", "terrain"),
	REDSTONE("redstone", "redstone"),
	MOB_DROPS("mob_drops", "mobdrops", "mob", "drops", "loot"),
	FARMING("farming", "farm", "food", "crop"),
	NATURE("nature", "nature", "plants", "flowers"),
	BUILDING("building", "building", "build", "blocks"),
	DECORATION("decoration", "decoration", "decor", "furniture"),
	LIGHTING("lighting", "lighting", "light"),
	TRANSPORT("transport", "transport", "vehicles"),
	UTILITY("utility", "utility", "utilities", "workstations"),
	NETHER("nether", "nether"),
	END("end", "end"),
	EQUIPMENT("equipment", "equipment", "gear", "tool", "armor", "armour", "weapon", "combat"),
	VALUABLES("valuables", "valuable", "ore", "ingot", "gem", "mineral", "treasure"),
	MISCELLANEOUS("miscellaneous", "misc", "miscellaneous", "other");

	private static final Set<String> VALUABLE_IDS = Set.of(
		"diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard", "coal", "netherite_scrap",
		"ancient_debris", "iron_block", "gold_block", "copper_block", "diamond_block", "emerald_block",
		"lapis_block", "netherite_block", "coal_block", "sulfur", "potent_sulfur", "cinnabar",
		"heart_of_the_sea", "echo_shard", "disc_fragment_5"
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
		"composter", "pitcher_pod", "sea_pickle", "bone_meal"
	);
	private static final List<String> NATURE_PARTS = List.of(
		"flower", "tulip", "orchid", "daisy", "cornflower", "eyeblossom", "shrub", "bush", "grass",
		"fern", "sapling", "propagule", "mushroom", "fungus", "dripleaf", "coral", "seagrass",
		"hanging_roots", "leaf_litter", "pink_petals", "wildflowers", "azalea", "lily_pad", "moss_"
	);
	private static final List<String> DECORATION_PARTS = List.of(
		"_bed", "banner", "carpet", "painting", "item_frame", "flower_pot", "decorated_pot",
		"pottery_sherd", "_candle"
	);
	private static final List<String> LIGHTING_PARTS = List.of(
		"torch", "lantern", "sea_lantern", "froglight", "campfire", "jack_o_lantern", "glow_lichen"
	);
	private static final List<String> UTILITY_PARTS = List.of(
		"furnace", "smoker", "blast_furnace", "crafting_table", "cartography_table", "fletching_table",
		"smithing_table", "loom", "brewing_stand", "cauldron", "anvil", "lectern", "jukebox", "spawner",
		"beacon", "conduit", "hopper", "dispenser", "dropper", "crafter", "observer", "repeater",
		"comparator", "sculk_sensor", "daylight_detector", "command_block", "structure_block", "jigsaw",
		"minecart", "bucket", "potion", "map", "compass", "clock", "spyglass", "book", "paper", "lead",
		"name_tag", "brush", "bundle", "trial_key", "ominous_bottle", "experience_bottle", "fire_charge"
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
			|| path.endsWith("_spawn_egg")
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
		if (isDecoration(path)) {
			return DECORATION;
		}
		if (isNature(stack, path)) {
			return NATURE;
		}
		if (containsAny(path, LIGHTING_PARTS)) {
			return LIGHTING;
		}
		if (path.endsWith("_minecart") || path.equals("minecart")) {
			return TRANSPORT;
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
		if (containsAny(path, UTILITY_PARTS)) {
			return UTILITY;
		}
		if (stack.getItem() instanceof BlockItem || isBuildingMaterial(path)) {
			return BUILDING;
		}
		if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) {
			return MISCELLANEOUS;
		}
		return null;
	}

	private static boolean isDecoration(String path) {
		return containsAny(path, DECORATION_PARTS)
			|| path.equals("glass")
			|| path.equals("tinted_glass")
			|| path.equals("glass_pane")
			|| path.endsWith("_stained_glass")
			|| path.endsWith("_stained_glass_pane")
			|| path.endsWith("_banner_pattern");
	}

	private static boolean isNature(ItemStack stack, String path) {
		return containsAny(path, NATURE_PARTS)
			|| stack.is(ItemTags.SAPLINGS)
			|| stack.is(ItemTags.MUSHROOMS);
	}

	private static boolean isBuildingMaterial(String path) {
		return path.endsWith("_wool")
			|| path.endsWith("_concrete")
			|| path.endsWith("_concrete_powder")
			|| path.endsWith("_terracotta")
			|| path.endsWith("_glazed_terracotta")
			|| path.equals("terracotta")
			|| path.equals("sponge")
			|| path.equals("wet_sponge")
			|| path.equals("ice")
			|| path.equals("packed_ice")
			|| path.equals("blue_ice")
			|| path.equals("snow")
			|| path.equals("snow_block")
			|| path.contains("coral")
			|| path.contains("prismarine")
			|| path.startsWith("sculk")
			|| path.startsWith("resin_");
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
