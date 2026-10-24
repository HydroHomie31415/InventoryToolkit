package dev.luka.inventorytoolkit.config;

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
import java.util.function.Supplier;

/**
 * Reads and atomically writes the JSON files of the smaller toolkit modules.
 */
public final class ConfigFiles {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigFiles() {
	}

	public static Path path(String fileName) {
		return FabricLoader.getInstance().getConfigDir().resolve(fileName);
	}

	/**
	 * @return the stored configuration, or the defaults when the file is
	 *     missing or unreadable
	 */
	public static <T> T load(Path path, Class<T> type, Supplier<T> defaults) {
		if (!Files.exists(path)) {
			return defaults.get();
		}
		try {
			T config = GSON.fromJson(Files.readString(path), type);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			return config;
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error("Could not read {}; using defaults", path, exception);
			return defaults.get();
		}
	}

	public static void save(Path path, Object config) {
		Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(temporaryPath, GSON.toJson(config));
			try {
				Files.move(
					temporaryPath,
					path,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			InventoryToolkitClient.LOGGER.error("Could not save {}", path, exception);
		}
	}
}
