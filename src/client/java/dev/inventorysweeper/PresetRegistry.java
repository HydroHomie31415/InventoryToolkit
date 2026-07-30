package dev.inventorysweeper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PresetRegistry {
	private static final Map<String, Preset> PRESETS = createPresets();

	private PresetRegistry() {
	}

	public static Collection<Preset> all() {
		return PRESETS.values();
	}

	public static Set<String> ids() {
		return PRESETS.keySet();
	}

	private static Map<String, Preset> createPresets() {
		Map<String, Preset> presets = new LinkedHashMap<>();

		add(presets, "classic_junk",
			List.of(
				"minecraft:rotten_flesh",
				"minecraft:poisonous_potato",
				"minecraft:spider_eye",
				"minecraft:wheat_seeds",
				"minecraft:beetroot_seeds",
				"minecraft:dead_bush"
			),
			List.of()
		);
		add(presets, "flowers_and_litter",
			List.of("minecraft:leaf_litter"),
			List.of("minecraft:flowers")
		);
		add(presets, "seeds_and_saplings",
			List.of(
				"minecraft:wheat_seeds",
				"minecraft:beetroot_seeds",
				"minecraft:melon_seeds",
				"minecraft:pumpkin_seeds",
				"minecraft:torchflower_seeds",
				"minecraft:pitcher_pod"
			),
			List.of("minecraft:saplings")
		);
		add(presets, "mining_rubble",
			List.of(
				"minecraft:cobblestone",
				"minecraft:cobbled_deepslate",
				"minecraft:dirt",
				"minecraft:gravel",
				"minecraft:granite",
				"minecraft:diorite",
				"minecraft:andesite",
				"minecraft:tuff",
				"minecraft:calcite",
				"minecraft:netherrack"
			),
			List.of()
		);
		add(presets, "mob_leftovers",
			List.of(
				"minecraft:rotten_flesh",
				"minecraft:bone",
				"minecraft:arrow",
				"minecraft:string",
				"minecraft:spider_eye",
				"minecraft:gunpowder",
				"minecraft:slime_ball"
			),
			List.of()
		);
		add(presets, "ocean_clutter",
			List.of(
				"minecraft:kelp",
				"minecraft:seagrass",
				"minecraft:ink_sac",
				"minecraft:glow_ink_sac",
				"minecraft:prismarine_shard",
				"minecraft:prismarine_crystals"
			),
			List.of()
		);

		return Collections.unmodifiableMap(new LinkedHashMap<>(presets));
	}

	private static void add(Map<String, Preset> presets, String id, List<String> items, List<String> tags) {
		presets.put(id, new Preset(
			id,
			items.stream().map(Identifier::parse).collect(Collectors.toUnmodifiableSet()),
			tags.stream()
				.map(Identifier::parse)
				.map(identifier -> TagKey.create(Registries.ITEM, identifier))
				.collect(Collectors.toUnmodifiableSet())
		));
	}

	public record Preset(String id, Set<Identifier> items, Set<TagKey<Item>> tags) {
		public boolean matches(ItemStack stack) {
			Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (items.contains(itemId)) {
				return true;
			}
			return tags.stream().anyMatch(tag -> stack.typeHolder().is(tag));
		}
	}
}
