package dev.luka.inventorytoolkit.shulkerpreview;

import dev.luka.inventorytoolkit.config.ConfigFiles;

import java.nio.file.Path;

public final class ShulkerPreviewConfig {
	private static final Path CONFIG_PATH = ConfigFiles.path("inventory-toolkit-shulker-preview.json");

	public int configVersion = 1;
	public boolean enabled = true;
	public boolean requireShift = false;
	public boolean hideItemList = true;
	public boolean colorBackground = true;

	public static ShulkerPreviewConfig load() {
		ShulkerPreviewConfig config = ConfigFiles.load(CONFIG_PATH, ShulkerPreviewConfig.class, ShulkerPreviewConfig::new);
		config.save();
		return config;
	}

	public void save() {
		configVersion = 1;
		ConfigFiles.save(CONFIG_PATH, this);
	}
}
