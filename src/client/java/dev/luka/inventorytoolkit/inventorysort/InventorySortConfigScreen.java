package dev.luka.inventorytoolkit.inventorysort;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

public final class InventorySortConfigScreen {
	private InventorySortConfigScreen() {
	}

	public static Screen create(Screen parent) {
		InventorySortConfig config = InventorySortClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.inventory_sort.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.inventory_sort.category.general"
		));

		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(order(entries, "order", config.order, InventorySortConfig.Order.CREATIVE,
			value -> config.order = value));
		category.addEntry(order(entries, "shift_order", config.shiftOrder, InventorySortConfig.Order.QUANTITY,
			value -> config.shiftOrder = value));
		category.addEntry(order(entries, "ctrl_order", config.ctrlOrder, InventorySortConfig.Order.ALPHABET,
			value -> config.ctrlOrder = value));
		category.addEntry(order(entries, "alt_order", config.altOrder, InventorySortConfig.Order.RAW_ID,
			value -> config.altOrder = value));
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.inventory_sort.hotbar_scope"),
				InventorySortConfig.HotbarScope.class,
				config.hotbarScope
			)
			.setDefaultValue(InventorySortConfig.HotbarScope.SEPARATE)
			.setEnumNameProvider(value -> enumName("hotbar_scope", value))
			.setTooltip(Component.translatable("config.inventory_toolkit.inventory_sort.hotbar_scope.tooltip"))
			.setSaveConsumer(value -> config.hotbarScope = value)
			.build());

		builder.setSavingRunnable(config::save);
		return builder.build();
	}

	private static AbstractConfigListEntry<?> order(
		ConfigEntryBuilder entries,
		String name,
		InventorySortConfig.Order value,
		InventorySortConfig.Order defaultValue,
		Consumer<InventorySortConfig.Order> saveConsumer
	) {
		return entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.inventory_sort." + name),
				InventorySortConfig.Order.class,
				value
			)
			.setDefaultValue(defaultValue)
			.setEnumNameProvider(order -> enumName("order", order))
			.setTooltip(Component.translatable("config.inventory_toolkit.inventory_sort." + name + ".tooltip"))
			.setSaveConsumer(saveConsumer)
			.build();
	}

	private static Component enumName(String field, Enum<?> value) {
		return Component.translatable(
			"config.inventory_toolkit.inventory_sort." + field + "." + value.name().toLowerCase(Locale.ROOT)
		);
	}

	private static AbstractConfigListEntry<?> toggle(
		ConfigEntryBuilder entries,
		String name,
		boolean value,
		boolean defaultValue,
		Consumer<Boolean> saveConsumer
	) {
		return entries.startBooleanToggle(
				Component.translatable("config.inventory_toolkit.inventory_sort." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.inventory_sort." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
