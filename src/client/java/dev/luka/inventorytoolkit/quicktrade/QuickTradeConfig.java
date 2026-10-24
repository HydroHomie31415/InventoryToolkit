package dev.luka.inventorytoolkit.quicktrade;

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

public final class QuickTradeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("inventory-toolkit-quick-trade.json");

	public int configVersion = 1;
	public boolean enabled = true;
	public boolean showButton = true;
	public boolean shiftClickTrades = true;
	public boolean showMessages = true;
	public int delayTicks = 2;

	public static QuickTradeConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			QuickTradeConfig config = new QuickTradeConfig();
			config.save();
			return config;
		}

		try {
			QuickTradeConfig config = GSON.fromJson(
				Files.readString(CONFIG_PATH),
				QuickTradeConfig.class
			);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error(
				"Could not read {}; using quick trade defaults",
				CONFIG_PATH,
				exception
			);
			return new QuickTradeConfig();
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
		delayTicks = Math.max(0, Math.min(10, delayTicks));
	}
}
