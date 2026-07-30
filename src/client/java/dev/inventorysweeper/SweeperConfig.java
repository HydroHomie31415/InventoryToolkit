package dev.inventorysweeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SweeperConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("inventory-sweeper.json");
	private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("inventory-roomba.json");

	public int configVersion = 3;
	public boolean enabled = false;
	public boolean notifications = true;
	public boolean notifyOnDrop = true;
	public int dropDelayTicks = 0;
	public int maxActionsPerTick = 1;
	public boolean pauseWhileScreenOpen = false;
	public boolean dropContainerTransfers = true;
	public int autoDroppedItemOpacity = 25;
	public int autoDroppedItemFadeTicks = 40;
	public boolean protectCustomNamedItems = true;
	public boolean protectEnchantedItems = true;
	public boolean protectDamagedItems = true;
	public Map<String, Boolean> presets = defaultPresets();
	public Set<String> customItems = new LinkedHashSet<>();
	public Set<String> keptItems = new LinkedHashSet<>();

	public static SweeperConfig load() {
		Path sourcePath = Files.exists(CONFIG_PATH)
			? CONFIG_PATH
			: Files.exists(LEGACY_CONFIG_PATH) ? LEGACY_CONFIG_PATH : null;
		if (sourcePath == null) {
			SweeperConfig config = new SweeperConfig();
			config.save();
			return config;
		}

		try {
			SweeperConfig config = GSON.fromJson(Files.readString(sourcePath), SweeperConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			if (sourcePath.equals(LEGACY_CONFIG_PATH)) {
				InventorySweeperClient.LOGGER.info("Imported legacy configuration into {}", CONFIG_PATH);
			}
			return config;
		} catch (IOException | JsonParseException exception) {
			InventorySweeperClient.LOGGER.error("Could not read {}; using safe defaults", sourcePath, exception);
			return new SweeperConfig();
		}
	}

	public void save() {
		normalize();
		Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(temporaryPath, GSON.toJson(this));
			try {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			InventorySweeperClient.LOGGER.error("Could not save {}", CONFIG_PATH, exception);
		}
	}

	public boolean isPresetEnabled(String presetId) {
		return presets.getOrDefault(presetId, false);
	}

	public void setPresetEnabled(String presetId, boolean value) {
		presets.put(presetId, value);
		save();
	}

	private void normalize() {
		configVersion = 3;
		dropDelayTicks = Math.max(0, Math.min(100, dropDelayTicks));
		maxActionsPerTick = Math.max(1, Math.min(8, maxActionsPerTick));
		autoDroppedItemOpacity = Math.max(0, Math.min(100, autoDroppedItemOpacity));
		autoDroppedItemFadeTicks = Math.max(0, Math.min(200, autoDroppedItemFadeTicks));
		if (presets == null) {
			presets = defaultPresets();
		}
		for (String presetId : PresetRegistry.ids()) {
			presets.putIfAbsent(presetId, "classic_junk".equals(presetId));
		}
		if (customItems == null) {
			customItems = new LinkedHashSet<>();
		}
		if (keptItems == null) {
			keptItems = new LinkedHashSet<>();
		}
	}

	private static Map<String, Boolean> defaultPresets() {
		Map<String, Boolean> defaults = new LinkedHashMap<>();
		for (String presetId : PresetRegistry.ids()) {
			defaults.put(presetId, "classic_junk".equals(presetId));
		}
		return defaults;
	}
}
