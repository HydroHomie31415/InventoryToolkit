package dev.luka.inventorytoolkit.quickstack;

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
import java.util.ArrayList;
import java.util.List;

public final class QuickStackConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("inventory-toolkit-quick-stack.json");

	public int configVersion = 1;
	public boolean enabled = true;
	public boolean showButton = true;
	public boolean includeHotbar = false;
	public boolean matchComponents = false;
	public boolean includeUnstackable = false;
	public boolean nearbyEnabled = true;
	public boolean showInventoryButton = true;
	public boolean requireLineOfSight = true;
	public int containerDelayTicks = 2;
	public boolean showMessages = true;
	public boolean memoryEnabled = true;
	public boolean showSearchButton = true;
	public boolean altClickHighlight = true;
	public int highlightSeconds = 30;
	public boolean searchCraftable = true;
	public boolean sortEnabled = true;
	public boolean showSortButton = true;
	public boolean sortPreview = true;
	public boolean sortIncludeShulkerBoxes = false;
	public boolean sortUseChestNames = true;
	public boolean sortInventory = true;
	public boolean sortIncludeBackpack = true;
	public List<String> sortRules = new ArrayList<>();

	public static QuickStackConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			QuickStackConfig config = new QuickStackConfig();
			config.save();
			return config;
		}

		try {
			QuickStackConfig config = GSON.fromJson(
				Files.readString(CONFIG_PATH),
				QuickStackConfig.class
			);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error(
				"Could not read {}; using quick stack defaults",
				CONFIG_PATH,
				exception
			);
			return new QuickStackConfig();
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

	public void normalize() {
		configVersion = 1;
		containerDelayTicks = Math.max(0, Math.min(20, containerDelayTicks));
		highlightSeconds = Math.max(5, Math.min(300, highlightSeconds));
		if (sortRules == null) {
			sortRules = new ArrayList<>();
		}
	}
}
