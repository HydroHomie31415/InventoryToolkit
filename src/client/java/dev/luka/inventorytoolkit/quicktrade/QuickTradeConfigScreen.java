package dev.luka.inventorytoolkit.quicktrade;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class QuickTradeConfigScreen {
	private QuickTradeConfigScreen() {
	}

	public static Screen create(Screen parent) {
		QuickTradeConfig config = QuickTradeClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.quick_trade.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.quick_trade.category.general"
		));

		category.addEntry(entries.startTextDescription(Component.translatable(
			"config.inventory_toolkit.quick_trade.description"
		)).build());
		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(toggle(entries, "shift_click", config.shiftClickTrades, true,
			value -> config.shiftClickTrades = value));
		category.addEntry(toggle(entries, "show_button", config.showButton, true,
			value -> config.showButton = value));
		category.addEntry(entries.fillKeybindingField(
				Component.translatable("config.inventory_toolkit.quick_trade.key"),
				QuickTradeClient.quickTradeKey()
			)
			.setTooltip(Component.translatable("config.inventory_toolkit.quick_trade.key.tooltip"))
			.build());
		category.addEntry(toggle(entries, "show_messages", config.showMessages, true,
			value -> config.showMessages = value));
		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.quick_trade.delay"),
				config.delayTicks,
				0,
				10
			)
			.setDefaultValue(2)
			.setTextGetter(value -> Component.translatable("config.inventory_toolkit.quick_trade.ticks", value))
			.setTooltip(Component.translatable("config.inventory_toolkit.quick_trade.delay.tooltip"))
			.setSaveConsumer(value -> config.delayTicks = value)
			.build());

		builder.setSavingRunnable(() -> {
			QuickTradeClient.trader().cancel();
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
				Component.translatable("config.inventory_toolkit.quick_trade." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.quick_trade." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
