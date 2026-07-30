package dev.hydro.hydrosbundles;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class RedstoneClassifier {
	private static final Set<String> EXACT_IDS = Set.of(
		"minecraft:redstone",
		"minecraft:repeater",
		"minecraft:comparator",
		"minecraft:observer",
		"minecraft:piston",
		"minecraft:sticky_piston",
		"minecraft:dispenser",
		"minecraft:dropper",
		"minecraft:hopper",
		"minecraft:redstone_torch",
		"minecraft:redstone_wall_torch",
		"minecraft:redstone_lamp",
		"minecraft:lever",
		"minecraft:target",
		"minecraft:tripwire_hook",
		"minecraft:trapped_chest",
		"minecraft:daylight_detector",
		"minecraft:sculk_sensor",
		"minecraft:calibrated_sculk_sensor",
		"minecraft:crafter",
		"minecraft:tnt",
		"minecraft:note_block",
		"minecraft:lightning_rod"
	);

	private static final Set<String> PATH_SUFFIXES = Set.of(
		"_button",
		"_pressure_plate",
		"_rail",
		"_minecart"
	);

	private RedstoneClassifier() {
	}

	public static boolean matches(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String value = id.toString();
		if (EXACT_IDS.contains(value)) {
			return true;
		}
		String path = id.getPath();
		return PATH_SUFFIXES.stream().anyMatch(path::endsWith);
	}
}
