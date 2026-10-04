package dev.luka.inventorytoolkit.quickstack;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

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
