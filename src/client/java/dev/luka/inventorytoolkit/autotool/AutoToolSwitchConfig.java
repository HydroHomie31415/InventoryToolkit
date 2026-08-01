package dev.luka.inventorytoolkit.autotool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class AutoToolSwitchConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("inventory-toolkit-auto-tool-switch.json");

	public int configVersion = 1;
	public boolean enabled = true;
	public ActivationMode activationMode = ActivationMode.PAUSE_WHILE_KEY_HELD;
	public boolean allowWhileSneaking = false;
	public boolean allowInCreative = false;
	public boolean pauseWhileUsingItem = true;

	public CorrectToolPolicy correctToolPolicy = CorrectToolPolicy.PREFER;
	public double minimumMiningSpeed = 1.01;
	public int minimumSpeedImprovementPercent = 0;
	public LootPreference lootPreference = LootPreference.NONE;
	public LootPreferenceStrength lootPreferenceStrength = LootPreferenceStrength.TIEBREAKER;
	public DurabilityPreference durabilityPreference = DurabilityPreference.MOST_REMAINING;

	public int minimumRemainingDurability = 2;
	public int minimumRemainingDurabilityPercent = 0;
	public boolean allowPickaxes = true;
	public boolean allowAxes = true;
	public boolean allowShovels = true;
	public boolean allowHoes = true;
	public boolean allowShears = true;
	public boolean allowSwords = true;
	public boolean allowOtherEffectiveItems = true;
	public boolean[] enabledHotbarSlots = allSlotsEnabled();

	public ListMode blockListMode = ListMode.BLACKLIST;
	public Set<String> blockRules = new LinkedHashSet<>();
	public ListMode itemListMode = ListMode.BLACKLIST;
	public Set<String> itemRules = new LinkedHashSet<>();

	public ReturnMode returnMode = ReturnMode.AFTER_MINING;
	public int returnDelayTicks = 2;
	public boolean cancelReturnOnManualSlotChange = true;
	public boolean showSwitchMessage = false;
	public boolean showToggleMessage = true;

	public static AutoToolSwitchConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			AutoToolSwitchConfig config = new AutoToolSwitchConfig();
			config.save();
			return config;
		}

		try {
			AutoToolSwitchConfig config = GSON.fromJson(
				Files.readString(CONFIG_PATH),
				AutoToolSwitchConfig.class
			);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error(
				"Could not read {}; using auto tool switch defaults",
				CONFIG_PATH,
				exception
			);
			return new AutoToolSwitchConfig();
		}
	}

	public void save() {
		normalize();
		Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(temporaryPath, GSON.toJson(this));
			try {
				Files.move(
					temporaryPath,
					CONFIG_PATH,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			InventoryToolkitClient.LOGGER.error("Could not save {}", CONFIG_PATH, exception);
		}
	}

	public boolean isHotbarSlotEnabled(int slot) {
		return slot >= 0 && slot < enabledHotbarSlots.length && enabledHotbarSlots[slot];
	}

	public void normalize() {
		configVersion = 1;
		minimumMiningSpeed = Math.max(1.0, Math.min(100.0, minimumMiningSpeed));
		minimumSpeedImprovementPercent = clamp(minimumSpeedImprovementPercent, 0, 500);
		minimumRemainingDurability = clamp(minimumRemainingDurability, 0, 1000);
		minimumRemainingDurabilityPercent = clamp(minimumRemainingDurabilityPercent, 0, 100);
		returnDelayTicks = clamp(returnDelayTicks, 0, 40);

		if (activationMode == null) {
			activationMode = ActivationMode.PAUSE_WHILE_KEY_HELD;
		}
		if (correctToolPolicy == null) {
			correctToolPolicy = CorrectToolPolicy.PREFER;
		}
		if (lootPreference == null) {
			lootPreference = LootPreference.NONE;
		}
		if (lootPreferenceStrength == null) {
			lootPreferenceStrength = LootPreferenceStrength.TIEBREAKER;
		}
		if (durabilityPreference == null) {
			durabilityPreference = DurabilityPreference.MOST_REMAINING;
		}
		if (blockListMode == null) {
			blockListMode = ListMode.BLACKLIST;
		}
		if (itemListMode == null) {
			itemListMode = ListMode.BLACKLIST;
		}
		if (returnMode == null) {
			returnMode = ReturnMode.AFTER_MINING;
		}

		if (enabledHotbarSlots == null || enabledHotbarSlots.length != 9) {
			boolean[] replacement = allSlotsEnabled();
			if (enabledHotbarSlots != null) {
				System.arraycopy(
					enabledHotbarSlots,
					0,
					replacement,
					0,
					Math.min(enabledHotbarSlots.length, replacement.length)
				);
			}
			enabledHotbarSlots = replacement;
		}

		blockRules = normalizeRules(blockRules);
		itemRules = normalizeRules(itemRules);
	}

	private static LinkedHashSet<String> normalizeRules(Set<String> values) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		if (values == null) {
			return result;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				result.add(value.trim().toLowerCase(Locale.ROOT));
			}
		}
		return result;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static boolean[] allSlotsEnabled() {
		return new boolean[]{true, true, true, true, true, true, true, true, true};
	}

	public enum ActivationMode {
		ALWAYS,
		HOLD_KEY,
		PAUSE_WHILE_KEY_HELD
	}

	public enum CorrectToolPolicy {
		IGNORE,
		PREFER,
		REQUIRE_WHEN_NEEDED
	}

	public enum LootPreference {
		NONE,
		SILK_TOUCH,
		FORTUNE,
		UNENCHANTED
	}

	public enum LootPreferenceStrength {
		TIEBREAKER,
		PREFER,
		REQUIRE
	}

	public enum DurabilityPreference {
		MOST_REMAINING,
		LEAST_REMAINING,
		HOTBAR_ORDER
	}

	public enum ListMode {
		BLACKLIST,
		WHITELIST
	}

	public enum ReturnMode {
		NEVER,
		AFTER_MINING
	}
}
