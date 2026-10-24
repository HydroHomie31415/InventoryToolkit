package dev.luka.inventorytoolkit.compact;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class CompactConfigScreen {
	private CompactConfigScreen() {
	}

	public static Screen create(Screen parent) {
		CompactConfig config = CompactClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.compact.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.compact.category.general"
		));

		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(toggle(entries, "only_reversible", config.onlyReversible, true,
			value -> config.onlyReversible = value));
		category.addEntry(toggle(entries, "show_messages", config.showMessages, true,
			value -> config.showMessages = value));
		category.addEntry(entries.startStrList(
				Component.translatable("config.inventory_toolkit.compact.excluded_items"),
				config.excludedItems
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setTooltip(Component.translatable("config.inventory_toolkit.compact.excluded_items.tooltip"))
			.setCellErrorSupplier(value -> CompactConfig.parseItem(value) == null
				? Optional.of(Component.translatable("config.inventory_toolkit.compact.excluded_items.invalid"))
				: Optional.empty())
			.setSaveConsumer(values -> config.excludedItems = values.stream()
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.distinct()
				.collect(Collectors.toCollection(ArrayList::new)))
			.build());

		builder.setSavingRunnable(() -> {
			CompactClient.compactor().cancel();
			config.save();
		});
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
				Component.translatable("config.inventory_toolkit.compact." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.compact." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
