package dev.luka.inventorytoolkit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings that belong to the toolkit as a whole rather than one module.
 */
public final class InventoryToolkitConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("inventory-toolkit.json");

	public boolean welcomeShown = false;

	public static InventoryToolkitConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			return new InventoryToolkitConfig();
		}
		try {
			InventoryToolkitConfig config = GSON.fromJson(
				Files.readString(CONFIG_PATH),
				InventoryToolkitConfig.class
			);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			return config;
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error("Could not read {}; using defaults", CONFIG_PATH, exception);
			return new InventoryToolkitConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(this));
		} catch (IOException exception) {
			InventoryToolkitClient.LOGGER.error("Could not save {}", CONFIG_PATH, exception);
		}
	}
}
