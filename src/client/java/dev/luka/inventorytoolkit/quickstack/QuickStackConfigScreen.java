package dev.luka.inventorytoolkit.quickstack;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class QuickStackConfigScreen {
	private QuickStackConfigScreen() {
	}

	public static Screen create(Screen parent) {
		QuickStackConfig config = QuickStackClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.quick_stack.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.quick_stack.category.general"
		));

		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(toggle(entries, "show_button", config.showButton, true,
			value -> config.showButton = value));
		category.addEntry(entries.fillKeybindingField(
				Component.translatable("config.inventory_toolkit.quick_stack.key"),
				QuickStackClient.quickStackKey()
			)
			.setTooltip(Component.translatable("config.inventory_toolkit.quick_stack.key.tooltip"))
			.build());
		category.addEntry(toggle(entries, "include_hotbar", config.includeHotbar, false,
			value -> config.includeHotbar = value));
		category.addEntry(toggle(entries, "match_components", config.matchComponents, false,
			value -> config.matchComponents = value));
		category.addEntry(toggle(entries, "include_unstackable", config.includeUnstackable, false,
			value -> config.includeUnstackable = value));
		category.addEntry(toggle(entries, "show_messages", config.showMessages, true,
			value -> config.showMessages = value));

		ConfigCategory nearby = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.quick_stack.category.nearby"
		));
		nearby.addEntry(toggle(entries, "nearby_enabled", config.nearbyEnabled, true,
			value -> config.nearbyEnabled = value));
		nearby.addEntry(toggle(entries, "show_inventory_button", config.showInventoryButton, true,
			value -> config.showInventoryButton = value));
		nearby.addEntry(toggle(entries, "require_line_of_sight", config.requireLineOfSight, true,
			value -> config.requireLineOfSight = value));
		nearby.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.quick_stack.container_delay"),
				config.containerDelayTicks,
				0,
				20
			)
			.setDefaultValue(2)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_toolkit.quick_stack.ticks",
				value
			))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.quick_stack.container_delay.tooltip"
			))
			.setSaveConsumer(value -> config.containerDelayTicks = value)
			.build());

		ConfigCategory search = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.quick_stack.category.search"
		));
		search.addEntry(toggle(entries, "memory_enabled", config.memoryEnabled, true,
			value -> config.memoryEnabled = value));
		search.addEntry(entries.fillKeybindingField(
				Component.translatable("config.inventory_toolkit.quick_stack.search_key"),
				QuickStackClient.searchKey()
			)
			.setTooltip(Component.translatable("config.inventory_toolkit.quick_stack.search_key.tooltip"))
			.build());
		search.addEntry(toggle(entries, "show_search_button", config.showSearchButton, true,
			value -> config.showSearchButton = value));
		search.addEntry(toggle(entries, "alt_click_highlight", config.altClickHighlight, true,
			value -> config.altClickHighlight = value));
		search.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.quick_stack.highlight_seconds"),
				config.highlightSeconds,
				5,
				300
			)
			.setDefaultValue(30)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_toolkit.quick_stack.seconds",
				value
			))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.quick_stack.highlight_seconds.tooltip"
			))
			.setSaveConsumer(value -> config.highlightSeconds = value)
			.build());
		search.addEntry(toggle(entries, "search_craftable", config.searchCraftable, true,
			value -> config.searchCraftable = value));

		ConfigCategory sort = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.quick_stack.category.sort"
		));
		sort.addEntry(entries.startTextDescription(Component.translatable(
			"config.inventory_toolkit.quick_stack.sort.description"
		)).build());
		sort.addEntry(toggle(entries, "sort_enabled", config.sortEnabled, true,
			value -> config.sortEnabled = value));
		sort.addEntry(entries.fillKeybindingField(
				Component.translatable("config.inventory_toolkit.quick_stack.sort_key"),
				QuickStackClient.sortKey()
			)
			.setTooltip(Component.translatable("config.inventory_toolkit.quick_stack.sort_key.tooltip"))
			.build());
		sort.addEntry(toggle(entries, "show_sort_button", config.showSortButton, true,
			value -> config.showSortButton = value));
		sort.addEntry(toggle(entries, "sort_preview", config.sortPreview, true,
			value -> config.sortPreview = value));
		sort.addEntry(toggle(entries, "sort_inventory", config.sortInventory, true,
			value -> config.sortInventory = value));
		sort.addEntry(toggle(entries, "sort_use_chest_names", config.sortUseChestNames, true,
			value -> config.sortUseChestNames = value));
		sort.addEntry(toggle(entries, "sort_include_shulker_boxes", config.sortIncludeShulkerBoxes, false,
			value -> config.sortIncludeShulkerBoxes = value));
		sort.addEntry(toggle(entries, "sort_include_backpack", config.sortIncludeBackpack, true,
			value -> config.sortIncludeBackpack = value));
		sort.addEntry(entries.startStrList(
				Component.translatable("config.inventory_toolkit.quick_stack.sort_rules"),
				config.sortRules
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setTooltip(Component.translatable("config.inventory_toolkit.quick_stack.sort_rules.tooltip"))
			.setCellErrorSupplier(value -> SortClassifier.parseRule(value) == null
				? Optional.of(Component.translatable("config.inventory_toolkit.quick_stack.sort_rules.invalid"))
				: Optional.empty())
			.setSaveConsumer(values -> config.sortRules = values.stream()
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.distinct()
				.collect(Collectors.toCollection(ArrayList::new)))
			.build());

		builder.setSavingRunnable(() -> {
			QuickStackClient.nearby().cancel();
			config.save();
			Minecraft.getInstance().options.save();
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
				Component.translatable("config.inventory_toolkit.quick_stack." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.quick_stack." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
