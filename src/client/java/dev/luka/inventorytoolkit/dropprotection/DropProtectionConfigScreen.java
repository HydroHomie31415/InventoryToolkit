package dev.luka.inventorytoolkit.dropprotection;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class DropProtectionConfigScreen {
	private DropProtectionConfigScreen() {
	}

	public static Screen create(Screen parent) {
		DropProtectionConfig config = DropProtectionClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.drop_protection.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.drop_protection.category.general"
		));

		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.drop_protection.mode"),
				DropProtectionConfig.Mode.class,
				config.mode
			)
			.setDefaultValue(DropProtectionConfig.Mode.CONFIRM)
			.setEnumNameProvider(value -> enumName("mode", value))
			.setTooltip(Component.translatable("config.inventory_toolkit.drop_protection.mode.tooltip"))
			.setSaveConsumer(value -> config.mode = value)
			.build());
		category.addEntry(toggle(entries, "protect_enchanted", config.protectEnchanted, true,
			value -> config.protectEnchanted = value));
		category.addEntry(toggle(entries, "protect_named", config.protectNamed, true,
			value -> config.protectNamed = value));
		category.addEntry(toggle(entries, "protect_filled_containers", config.protectFilledContainers, true,
			value -> config.protectFilledContainers = value));
		category.addEntry(toggle(entries, "protect_locked_slots", config.protectLockedSlots, true,
			value -> config.protectLockedSlots = value));
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.drop_protection.rarity"),
				DropProtectionConfig.RarityThreshold.class,
				config.rarity
			)
			.setDefaultValue(DropProtectionConfig.RarityThreshold.UNCOMMON)
			.setEnumNameProvider(value -> enumName("rarity", value))
			.setTooltip(Component.translatable("config.inventory_toolkit.drop_protection.rarity.tooltip"))
			.setSaveConsumer(value -> config.rarity = value)
			.build());
		category.addEntry(entries.startStrList(
				Component.translatable("config.inventory_toolkit.drop_protection.protected_items"),
				config.protectedItems
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setTooltip(Component.translatable("config.inventory_toolkit.drop_protection.protected_items.tooltip"))
			.setCellErrorSupplier(value -> DropProtectionConfig.parseItem(value) == null
				? Optional.of(Component.translatable("config.inventory_toolkit.drop_protection.protected_items.invalid"))
				: Optional.empty())
			.setSaveConsumer(values -> config.protectedItems = values.stream()
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.distinct()
				.collect(Collectors.toCollection(ArrayList::new)))
			.build());

		builder.setSavingRunnable(DropProtectionClient::onConfigChanged);
		return builder.build();
	}

	private static Component enumName(String field, Enum<?> value) {
		return Component.translatable(
			"config.inventory_toolkit.drop_protection." + field + "."
				+ value.name().toLowerCase(Locale.ROOT)
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
				Component.translatable("config.inventory_toolkit.drop_protection." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.drop_protection." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
