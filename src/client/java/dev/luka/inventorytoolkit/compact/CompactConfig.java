package dev.luka.inventorytoolkit.compact;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class CompactConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("inventory-toolkit-compact.json");

	public int configVersion = 1;
	public boolean enabled = true;
	public boolean onlyReversible = true;
	public boolean showMessages = true;
	public List<String> excludedItems = new ArrayList<>();

	public static CompactConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			CompactConfig config = new CompactConfig();
			config.save();
			return config;
		}

		try {
			CompactConfig config = GSON.fromJson(
				Files.readString(CONFIG_PATH),
				CompactConfig.class
			);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error(
				"Could not read {}; using compact defaults",
				CONFIG_PATH,
				exception
			);
			return new CompactConfig();
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
		if (excludedItems == null) {
			excludedItems = new ArrayList<>();
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
