package dev.luka.inventorytoolkit.dropprotection;

import dev.luka.inventorytoolkit.config.ConfigFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DropProtectionConfig {
	private static final Path CONFIG_PATH = ConfigFiles.path("inventory-toolkit-drop-protection.json");

	public enum Mode {
		/** The drop goes through when it is repeated within a few seconds. */
		CONFIRM,
		/** Protected items cannot be dropped until protection is turned off. */
		BLOCK
	}

	public enum RarityThreshold {
		OFF,
		UNCOMMON,
		RARE,
		EPIC
	}

	public int configVersion = 1;
	public boolean enabled = true;
	public Mode mode = Mode.CONFIRM;
	public boolean protectEnchanted = true;
	public boolean protectNamed = true;
	public boolean protectFilledContainers = true;
	public boolean protectLockedSlots = true;
	public RarityThreshold rarity = RarityThreshold.UNCOMMON;
	public List<String> protectedItems = new ArrayList<>();

	public static DropProtectionConfig load() {
		DropProtectionConfig config = ConfigFiles.load(CONFIG_PATH, DropProtectionConfig.class, DropProtectionConfig::new);
		config.save();
		return config;
	}

	public void save() {
		normalize();
		ConfigFiles.save(CONFIG_PATH, this);
	}

	public void normalize() {
		configVersion = 1;
		if (mode == null) {
			mode = Mode.CONFIRM;
		}
		if (rarity == null) {
			rarity = RarityThreshold.UNCOMMON;
		}
		if (protectedItems == null) {
			protectedItems = new ArrayList<>();
		}
	}

	/**
	 * @return the item with this ID, or null when there is none
	 */
	public static Item parseItem(String value) {
		Identifier id = Identifier.tryParse(value.trim());
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(id);
	}
}
