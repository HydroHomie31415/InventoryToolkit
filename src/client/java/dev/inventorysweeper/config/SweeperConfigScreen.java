package dev.inventorysweeper.config;

import dev.inventorysweeper.InventorySweeperClient;
import dev.inventorysweeper.PresetRegistry;
import dev.inventorysweeper.SweeperConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class SweeperConfigScreen {
	private SweeperConfigScreen() {
	}

	public static Screen create(Screen parent) {
		SweeperConfig config = InventorySweeperClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_sweeper.title"))
			.setDoesConfirmSave(true)
			.setShouldTabsSmoothScroll(true)
			.setShouldListSmoothScroll(true)
			.alwaysShowTabs();
		ConfigEntryBuilder entries = builder.entryBuilder();

		addGeneralCategory(builder, entries, config);
		addVisualsCategory(builder, entries, config);
		addPresetsCategory(builder, entries, config);
		addCustomRulesCategory(builder, entries, config);
		addSafetyCategory(builder, entries, config);

		builder.setSavingRunnable(() -> {
			config.save();
			InventorySweeperClient.cleaner().resnapshot();
		});
		return builder.build();
	}

	private static void addVisualsCategory(ConfigBuilder builder, ConfigEntryBuilder entries, SweeperConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.inventory_sweeper.category.visuals")
		);
		category.setDescription(new FormattedText[]{
			Component.translatable("config.inventory_sweeper.category.visuals.description")
		});

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_sweeper.dropped_item_opacity"),
				config.autoDroppedItemOpacity,
				0,
				100
			)
			.setDefaultValue(25)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_sweeper.dropped_item_opacity.value",
				value
			))
			.setTooltip(Component.translatable("config.inventory_sweeper.dropped_item_opacity.tooltip"))
			.setSaveConsumer(value -> config.autoDroppedItemOpacity = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_sweeper.dropped_item_fade"),
				config.autoDroppedItemFadeTicks,
				0,
				200
			)
			.setDefaultValue(40)
			.setTextGetter(value -> value == 0
				? Component.translatable("config.inventory_sweeper.dropped_item_fade.never")
				: Component.translatable(
					"config.inventory_sweeper.dropped_item_fade.value",
					String.format(Locale.ROOT, "%.2f", value / 20.0)
				))
			.setTooltip(Component.translatable("config.inventory_sweeper.dropped_item_fade.tooltip"))
			.setSaveConsumer(value -> config.autoDroppedItemFadeTicks = value)
			.build());
	}

	private static void addGeneralCategory(ConfigBuilder builder, ConfigEntryBuilder entries, SweeperConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.inventory_sweeper.category.general")
		);
		category.setDescription(new FormattedText[]{
			Component.translatable("config.inventory_sweeper.category.general.description")
		});

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.enabled"),
				config.enabled
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.inventory_sweeper.enabled.tooltip"))
			.setSaveConsumer(value -> config.enabled = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.notifications"),
				config.notifications
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_sweeper.notifications.tooltip"))
			.setSaveConsumer(value -> config.notifications = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.notify_on_drop"),
				config.notifyOnDrop
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_sweeper.notify_on_drop.tooltip"))
			.setSaveConsumer(value -> config.notifyOnDrop = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_sweeper.drop_delay"),
				config.dropDelayTicks,
				0,
				100
			)
			.setDefaultValue(0)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_sweeper.drop_delay.value",
				value,
				String.format(Locale.ROOT, "%.2f", value / 20.0)
			))
			.setTooltip(Component.translatable("config.inventory_sweeper.drop_delay.tooltip"))
			.setSaveConsumer(value -> config.dropDelayTicks = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_sweeper.actions_per_tick"),
				config.maxActionsPerTick,
				1,
				8
			)
			.setDefaultValue(1)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_sweeper.actions_per_tick.value",
				value
			))
			.setTooltip(Component.translatable("config.inventory_sweeper.actions_per_tick.tooltip"))
			.setSaveConsumer(value -> config.maxActionsPerTick = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.pause_in_screens"),
				config.pauseWhileScreenOpen
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.inventory_sweeper.pause_in_screens.tooltip"))
			.setSaveConsumer(value -> config.pauseWhileScreenOpen = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.container_transfers"),
				config.dropContainerTransfers
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_sweeper.container_transfers.tooltip"))
			.setSaveConsumer(value -> config.dropContainerTransfers = value)
			.build());
	}

	private static void addPresetsCategory(ConfigBuilder builder, ConfigEntryBuilder entries, SweeperConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.inventory_sweeper.category.presets")
		);
		category.setDescription(new FormattedText[]{
			Component.translatable("config.inventory_sweeper.category.presets.description")
		});
		category.addEntry(entries.startTextDescription(
			Component.translatable("config.inventory_sweeper.presets.explanation")
		).build());

		for (PresetRegistry.Preset preset : PresetRegistry.all()) {
			String id = preset.id();
			category.addEntry(entries.startBooleanToggle(
					Component.translatable("config.inventory_sweeper.preset." + id),
					config.isPresetEnabled(id)
				)
				.setDefaultValue("classic_junk".equals(id))
				.setTooltip(Component.translatable("preset.inventory_sweeper." + id))
				.setSaveConsumer(value -> config.presets.put(id, value))
				.build());
		}
	}

	private static void addCustomRulesCategory(ConfigBuilder builder, ConfigEntryBuilder entries, SweeperConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.inventory_sweeper.category.custom_rules")
		);
		category.setDescription(new FormattedText[]{
			Component.translatable("config.inventory_sweeper.category.custom_rules.description")
		});
		category.addEntry(entries.startTextDescription(
			Component.translatable("config.inventory_sweeper.custom_rules.explanation")
		).build());

		category.addEntry(entries.startStrList(
				Component.translatable("config.inventory_sweeper.custom_drop_items"),
				config.customItems.stream().sorted().toList()
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setCellErrorSupplier(SweeperConfigScreen::validateItemId)
			.setErrorSupplier(SweeperConfigScreen::validateItemList)
			.setAddButtonTooltip(Component.translatable("config.inventory_sweeper.item_list.add"))
			.setRemoveButtonTooltip(Component.translatable("config.inventory_sweeper.item_list.remove"))
			.setTooltip(Component.translatable("config.inventory_sweeper.custom_drop_items.tooltip"))
			.setSaveConsumer(values -> config.customItems = canonicalItemIds(values))
			.build());

		category.addEntry(entries.startStrList(
				Component.translatable("config.inventory_sweeper.kept_items"),
				config.keptItems.stream().sorted().toList()
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setCellErrorSupplier(SweeperConfigScreen::validateItemId)
			.setErrorSupplier(SweeperConfigScreen::validateItemList)
			.setAddButtonTooltip(Component.translatable("config.inventory_sweeper.item_list.add"))
			.setRemoveButtonTooltip(Component.translatable("config.inventory_sweeper.item_list.remove"))
			.setTooltip(Component.translatable("config.inventory_sweeper.kept_items.tooltip"))
			.setSaveConsumer(values -> config.keptItems = canonicalItemIds(values))
			.build());
	}

	private static void addSafetyCategory(ConfigBuilder builder, ConfigEntryBuilder entries, SweeperConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.inventory_sweeper.category.safety")
		);
		category.setDescription(new FormattedText[]{
			Component.translatable("config.inventory_sweeper.category.safety.description")
		});
		category.addEntry(entries.startTextDescription(
			Component.translatable("config.inventory_sweeper.safety.explanation")
		).build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.protect_named"),
				config.protectCustomNamedItems
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_sweeper.protect_named.tooltip"))
			.setSaveConsumer(value -> config.protectCustomNamedItems = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.protect_enchanted"),
				config.protectEnchantedItems
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_sweeper.protect_enchanted.tooltip"))
			.setSaveConsumer(value -> config.protectEnchantedItems = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_sweeper.protect_damaged"),
				config.protectDamagedItems
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_sweeper.protect_damaged.tooltip"))
			.setSaveConsumer(value -> config.protectDamagedItems = value)
			.build());
	}

	private static Optional<Component> validateItemId(String rawId) {
		Identifier itemId = Identifier.tryParse(rawId.trim());
		if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId) || "minecraft:air".equals(itemId.toString())) {
			return Optional.of(Component.translatable("config.inventory_sweeper.item_list.invalid", rawId));
		}
		return Optional.empty();
	}

	private static Optional<Component> validateItemList(List<String> values) {
		Set<Identifier> seen = new HashSet<>();
		for (String value : values) {
			Identifier itemId = Identifier.tryParse(value.trim());
			if (itemId != null && !seen.add(itemId)) {
				return Optional.of(Component.translatable(
					"config.inventory_sweeper.item_list.duplicate",
					itemId
				));
			}
		}
		return Optional.empty();
	}

	private static LinkedHashSet<String> canonicalItemIds(List<String> values) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			Identifier itemId = Identifier.tryParse(value.trim());
			if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
				result.add(itemId.toString());
			}
		}
		return result;
	}
}
