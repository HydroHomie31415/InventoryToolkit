package dev.hydro.hydrosbundles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class HydrosBundlesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("hydros-bundles.json");

	public int configVersion = 1;

	public boolean enabled = true;
	public boolean includeHotbar = false;
	public int actionDelayTicks = 2;
	public int maxTransfersPerRun = 128;
	public boolean preferMostFilledBundle = true;

	public BundleNameMatch bundleNameMatch = BundleNameMatch.EXACT;
	public boolean caseSensitiveBundleNames = false;
	public UnmatchedBehavior unmatchedBehavior = UnmatchedBehavior.ANY_UNNAMED_BUNDLE;
	public List<String> routingRules = defaultRules();

	public boolean notifications = true;
	public boolean notifySkippedItems = false;
	public boolean rememberLastBundle = true;
	public boolean showPlayerInventory = true;

	public static HydrosBundlesConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			HydrosBundlesConfig config = new HydrosBundlesConfig();
			config.save();
			return config;
		}

		try {
			HydrosBundlesConfig config = GSON.fromJson(Files.readString(CONFIG_PATH), HydrosBundlesConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			HydrosBundlesClient.LOGGER.error("Could not read {}; using defaults", CONFIG_PATH, exception);
			return new HydrosBundlesConfig();
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
			HydrosBundlesClient.LOGGER.error("Could not save {}", CONFIG_PATH, exception);
		}
	}

	public void normalize() {
		configVersion = 1;
		actionDelayTicks = Math.max(0, Math.min(40, actionDelayTicks));
		maxTransfersPerRun = Math.max(1, Math.min(512, maxTransfersPerRun));
		if (bundleNameMatch == null) {
			bundleNameMatch = BundleNameMatch.EXACT;
		}
		if (unmatchedBehavior == null) {
			unmatchedBehavior = UnmatchedBehavior.ANY_UNNAMED_BUNDLE;
		}
		if (routingRules == null) {
			routingRules = defaultRules();
		}
		routingRules = routingRules.stream()
			.filter(value -> value != null && !value.isBlank())
			.map(String::trim)
			.distinct()
			.toList();
	}

	private static List<String> defaultRules() {
		return new ArrayList<>(List.of(
			"redstone = $redstone",
			"blocks = $blocks"
		));
	}
}
