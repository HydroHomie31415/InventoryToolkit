package dev.luka.stackrefill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

public final class StackRefillConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("stack-refill.json");

	public int configVersion = 1;

	public boolean enabled = true;
	public int refillAtCount = 0;
	public int refillDelayTicks = 0;
	public int cooldownTicks = 1;
	public RefillMode refillMode = RefillMode.REPLACE_STACK;

	public MatchMode matchMode = MatchMode.EXACT_COMPONENTS;
	public SourceStrategy sourceStrategy = SourceStrategy.LARGEST_STACK;
	public boolean searchMainInventory = true;
	public boolean searchOtherHotbarSlots = false;
	public int minimumSourceCount = 1;

	public boolean onlyBlockItems = true;
	public boolean pauseWhileScreenOpen = true;
	public boolean requireOriginalSlotSelected = false;
	public boolean allowWhileSneaking = true;
	public boolean[] enabledHotbarSlots = allSlotsEnabled();

	public ListMode listMode = ListMode.BLACKLIST;
	public Set<String> itemRules = new LinkedHashSet<>();

	public boolean notifications = true;
	public boolean notifyWhenNoMatch = false;

	public static StackRefillConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			StackRefillConfig config = new StackRefillConfig();
			config.save();
			return config;
		}

		try {
			StackRefillConfig config = GSON.fromJson(Files.readString(CONFIG_PATH), StackRefillConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			StackRefillClient.LOGGER.error("Could not read {}; using defaults", CONFIG_PATH, exception);
			return new StackRefillConfig();
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
			StackRefillClient.LOGGER.error("Could not save {}", CONFIG_PATH, exception);
		}
	}

	public boolean isHotbarSlotEnabled(int slot) {
		return slot >= 0 && slot < enabledHotbarSlots.length && enabledHotbarSlots[slot];
	}

	public void normalize() {
		configVersion = 1;
		refillAtCount = Math.max(0, Math.min(63, refillAtCount));
		refillDelayTicks = Math.max(0, Math.min(100, refillDelayTicks));
		cooldownTicks = Math.max(0, Math.min(40, cooldownTicks));
		minimumSourceCount = Math.max(1, Math.min(64, minimumSourceCount));
		if (refillMode == null) {
			refillMode = RefillMode.REPLACE_STACK;
		}
		if (matchMode == null) {
			matchMode = MatchMode.EXACT_COMPONENTS;
		}
		if (sourceStrategy == null) {
			sourceStrategy = SourceStrategy.LARGEST_STACK;
		}
		if (listMode == null) {
			listMode = ListMode.BLACKLIST;
		}
		if (enabledHotbarSlots == null || enabledHotbarSlots.length != 9) {
			boolean[] replacement = allSlotsEnabled();
			if (enabledHotbarSlots != null) {
				System.arraycopy(enabledHotbarSlots, 0, replacement, 0, Math.min(enabledHotbarSlots.length, replacement.length));
			}
			enabledHotbarSlots = replacement;
		}
		if (itemRules == null) {
			itemRules = new LinkedHashSet<>();
		}
		itemRules.removeIf(value -> value == null || value.isBlank());
		if (!searchMainInventory && !searchOtherHotbarSlots) {
			searchMainInventory = true;
		}
	}

	private static boolean[] allSlotsEnabled() {
		return new boolean[]{true, true, true, true, true, true, true, true, true};
	}
}
