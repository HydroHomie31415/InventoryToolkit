package dev.luka.inventorytoolkit.slotlock;

import dev.luka.inventorytoolkit.config.ConfigFiles;
import net.minecraft.world.entity.player.Inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SlotLockConfig {
	private static final Path CONFIG_PATH = ConfigFiles.path("inventory-toolkit-slot-lock.json");

	public int configVersion = 1;
	public boolean enabled = true;
	public boolean blockQuickMove = true;
	public boolean showOnHotbar = true;
	/**
	 * Player inventory indices: 0–8 are the hotbar, 9–35 the main inventory.
	 */
	public List<Integer> lockedSlots = new ArrayList<>();

	public static SlotLockConfig load() {
		SlotLockConfig config = ConfigFiles.load(CONFIG_PATH, SlotLockConfig.class, SlotLockConfig::new);
		config.save();
		return config;
	}

	public void save() {
		normalize();
		ConfigFiles.save(CONFIG_PATH, this);
	}

	public void normalize() {
		configVersion = 1;
		if (lockedSlots == null) {
			lockedSlots = new ArrayList<>();
		}
		lockedSlots = new ArrayList<>(lockedSlots.stream()
			.filter(slot -> slot != null && slot >= 0 && slot < Inventory.INVENTORY_SIZE)
			.distinct()
			.sorted()
			.toList());
	}
}
