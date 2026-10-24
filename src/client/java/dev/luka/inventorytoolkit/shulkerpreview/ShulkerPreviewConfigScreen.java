package dev.luka.inventorytoolkit.shulkerpreview;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class ShulkerPreviewConfigScreen {
	private ShulkerPreviewConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ShulkerPreviewConfig config = ShulkerPreviewClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.shulker_preview.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.shulker_preview.category.general"
		));

		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(toggle(entries, "require_shift", config.requireShift, false,
			value -> config.requireShift = value));
		category.addEntry(toggle(entries, "hide_item_list", config.hideItemList, true,
			value -> config.hideItemList = value));
		category.addEntry(toggle(entries, "color_background", config.colorBackground, true,
			value -> config.colorBackground = value));

		builder.setSavingRunnable(config::save);
		return builder.build();
	}

	private static AbstractConfigListEntry<?> toggle(
		ConfigEntryBuilder entries,
		String name,
		boolean value,
		boolean defaultValue,
		Consumer<Boolean> saveConsumer
	) {
		return entries.startBooleanToggle(
				Component.translatable("config.inventory_toolkit.shulker_preview." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.shulker_preview." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
