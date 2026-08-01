package dev.hydro.hydrosrecipebook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RecipeBookConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("inventory-toolkit-recipe-book.json");

	public int configVersion = 1;
	public boolean enabled = true;

	public static RecipeBookConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			RecipeBookConfig config = new RecipeBookConfig();
			config.save();
			return config;
		}

		try {
			RecipeBookConfig config = GSON.fromJson(
				Files.readString(CONFIG_PATH),
				RecipeBookConfig.class
			);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.configVersion = 1;
			return config;
		} catch (IOException | JsonParseException exception) {
			HydrosRecipeBookLog.warn(
				"Could not read " + CONFIG_PATH + "; using defaults",
				exception
			);
			return new RecipeBookConfig();
		}
	}

	public void save() {
		configVersion = 1;
		Path temporaryPath = CONFIG_PATH.resolveSibling(
			CONFIG_PATH.getFileName() + ".tmp"
		);

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
				Files.move(
					temporaryPath,
					CONFIG_PATH,
					StandardCopyOption.REPLACE_EXISTING
				);
			}
		} catch (IOException exception) {
			HydrosRecipeBookLog.warn("Could not save " + CONFIG_PATH, exception);
		}
	}
}
