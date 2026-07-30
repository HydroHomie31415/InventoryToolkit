package dev.luka.stackrefill.config;

import dev.luka.stackrefill.ListMode;
import dev.luka.stackrefill.MatchMode;
import dev.luka.stackrefill.RefillMode;
import dev.luka.stackrefill.SourceStrategy;
import dev.luka.stackrefill.StackRefillClient;
import dev.luka.stackrefill.StackRefillConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class StackRefillConfigScreen {
	private StackRefillConfigScreen() {
	}

	public static Screen create(Screen parent) {
		StackRefillConfig config = StackRefillClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.stack_refill.title"))
			.setDoesConfirmSave(true)
			.setShouldTabsSmoothScroll(true)
			.setShouldListSmoothScroll(true)
			.alwaysShowTabs();
		ConfigEntryBuilder entries = builder.entryBuilder();

		addBehavior(builder, entries, config);
		addSources(builder, entries, config);
		addConditions(builder, entries, config);
		addItemRules(builder, entries, config);
		addFeedback(builder, entries, config);

		builder.setSavingRunnable(() -> {
			config.save();
			StackRefillClient.manager().clearPending();
		});
		return builder.build();
	}

	private static void addBehavior(ConfigBuilder builder, ConfigEntryBuilder entries, StackRefillConfig config) {
		ConfigCategory category = category(
			builder,
			"behavior",
			"config.stack_refill.category.behavior.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.enabled"),
				config.enabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.stack_refill.enabled.tooltip"))
			.setSaveConsumer(value -> config.enabled = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.stack_refill.threshold"),
				config.refillAtCount,
				0,
				63
			)
			.setDefaultValue(0)
			.setTextGetter(value -> value == 0
				? Component.translatable("config.stack_refill.threshold.empty")
				: Component.translatable("config.stack_refill.threshold.value", value))
			.setTooltip(Component.translatable("config.stack_refill.threshold.tooltip"))
			.setSaveConsumer(value -> config.refillAtCount = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.stack_refill.refill_mode"),
				RefillMode.class,
				config.refillMode
			)
			.setDefaultValue(RefillMode.REPLACE_STACK)
			.setEnumNameProvider(value -> enumName("refill_mode", value))
			.setTooltip(Component.translatable("config.stack_refill.refill_mode.tooltip"))
			.setSaveConsumer(value -> config.refillMode = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.stack_refill.delay"),
				config.refillDelayTicks,
				0,
				100
			)
			.setDefaultValue(0)
			.setTextGetter(value -> Component.translatable(
				"config.stack_refill.ticks",
				value,
				String.format(Locale.ROOT, "%.2f", value / 20.0)
			))
			.setTooltip(Component.translatable("config.stack_refill.delay.tooltip"))
			.setSaveConsumer(value -> config.refillDelayTicks = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.stack_refill.cooldown"),
				config.cooldownTicks,
				0,
				40
			)
			.setDefaultValue(1)
			.setTextGetter(value -> Component.translatable(
				"config.stack_refill.ticks",
				value,
				String.format(Locale.ROOT, "%.2f", value / 20.0)
			))
			.setTooltip(Component.translatable("config.stack_refill.cooldown.tooltip"))
			.setSaveConsumer(value -> config.cooldownTicks = value)
			.build());
	}

	private static void addSources(ConfigBuilder builder, ConfigEntryBuilder entries, StackRefillConfig config) {
		ConfigCategory category = category(
			builder,
			"sources",
			"config.stack_refill.category.sources.description"
		);

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.stack_refill.match_mode"),
				MatchMode.class,
				config.matchMode
			)
			.setDefaultValue(MatchMode.EXACT_COMPONENTS)
			.setEnumNameProvider(value -> enumName("match_mode", value))
			.setTooltip(Component.translatable("config.stack_refill.match_mode.tooltip"))
			.setSaveConsumer(value -> config.matchMode = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.stack_refill.source_strategy"),
				SourceStrategy.class,
				config.sourceStrategy
			)
			.setDefaultValue(SourceStrategy.LARGEST_STACK)
			.setEnumNameProvider(value -> enumName("source_strategy", value))
			.setTooltip(Component.translatable("config.stack_refill.source_strategy.tooltip"))
			.setSaveConsumer(value -> config.sourceStrategy = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.search_inventory"),
				config.searchMainInventory
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.stack_refill.search_inventory.tooltip"))
			.setSaveConsumer(value -> config.searchMainInventory = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.search_hotbar"),
				config.searchOtherHotbarSlots
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.stack_refill.search_hotbar.tooltip"))
			.setSaveConsumer(value -> config.searchOtherHotbarSlots = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.stack_refill.minimum_source"),
				config.minimumSourceCount,
				1,
				64
			)
			.setDefaultValue(1)
			.setTextGetter(value -> Component.translatable("config.stack_refill.items", value))
			.setTooltip(Component.translatable("config.stack_refill.minimum_source.tooltip"))
			.setSaveConsumer(value -> config.minimumSourceCount = value)
			.build());
	}

	private static void addConditions(ConfigBuilder builder, ConfigEntryBuilder entries, StackRefillConfig config) {
		ConfigCategory category = category(
			builder,
			"conditions",
			"config.stack_refill.category.conditions.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.blocks_only"),
				config.onlyBlockItems
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.stack_refill.blocks_only.tooltip"))
			.setSaveConsumer(value -> config.onlyBlockItems = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.pause_screens"),
				config.pauseWhileScreenOpen
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.stack_refill.pause_screens.tooltip"))
			.setSaveConsumer(value -> config.pauseWhileScreenOpen = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.require_selected"),
				config.requireOriginalSlotSelected
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.stack_refill.require_selected.tooltip"))
			.setSaveConsumer(value -> config.requireOriginalSlotSelected = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.allow_sneaking"),
				config.allowWhileSneaking
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.stack_refill.allow_sneaking.tooltip"))
			.setSaveConsumer(value -> config.allowWhileSneaking = value)
			.build());

		category.addEntry(entries.startTextDescription(
			Component.translatable("config.stack_refill.hotbar_slots.description")
		).build());
		for (int slot = 0; slot < 9; slot++) {
			int hotbarSlot = slot;
			category.addEntry(entries.startBooleanToggle(
					Component.translatable("config.stack_refill.hotbar_slot", slot + 1),
					config.enabledHotbarSlots[slot]
				)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.enabledHotbarSlots[hotbarSlot] = value)
				.build());
		}
	}

	private static void addItemRules(ConfigBuilder builder, ConfigEntryBuilder entries, StackRefillConfig config) {
		ConfigCategory category = category(
			builder,
			"item_rules",
			"config.stack_refill.category.item_rules.description"
		);

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.stack_refill.list_mode"),
				ListMode.class,
				config.listMode
			)
			.setDefaultValue(ListMode.BLACKLIST)
			.setEnumNameProvider(value -> enumName("list_mode", value))
			.setTooltip(Component.translatable("config.stack_refill.list_mode.tooltip"))
			.setSaveConsumer(value -> config.listMode = value)
			.build());

		category.addEntry(entries.startTextDescription(
			Component.translatable("config.stack_refill.item_rules.description")
		).build());
		category.addEntry(entries.startStrList(
				Component.translatable("config.stack_refill.item_rules"),
				config.itemRules.stream().sorted().toList()
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setCellErrorSupplier(StackRefillConfigScreen::validateItemId)
			.setErrorSupplier(StackRefillConfigScreen::validateItemList)
			.setAddButtonTooltip(Component.translatable("config.stack_refill.item_rules.add"))
			.setRemoveButtonTooltip(Component.translatable("config.stack_refill.item_rules.remove"))
			.setSaveConsumer(values -> config.itemRules = canonicalItemIds(values))
			.build());
	}

	private static void addFeedback(ConfigBuilder builder, ConfigEntryBuilder entries, StackRefillConfig config) {
		ConfigCategory category = category(
			builder,
			"feedback",
			"config.stack_refill.category.feedback.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.notifications"),
				config.notifications
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.stack_refill.notifications.tooltip"))
			.setSaveConsumer(value -> config.notifications = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.stack_refill.notify_no_match"),
				config.notifyWhenNoMatch
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.stack_refill.notify_no_match.tooltip"))
			.setSaveConsumer(value -> config.notifyWhenNoMatch = value)
			.build());
	}

	private static ConfigCategory category(ConfigBuilder builder, String name, String descriptionKey) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.stack_refill.category." + name)
		);
		category.setDescription(new FormattedText[]{Component.translatable(descriptionKey)});
		return category;
	}

	private static Component enumName(String field, Enum<?> value) {
		return Component.translatable(
			"config.stack_refill." + field + "." + value.name().toLowerCase(Locale.ROOT)
		);
	}

	private static Optional<Component> validateItemId(String rawId) {
		Identifier itemId = Identifier.tryParse(rawId.trim());
		if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId) || "minecraft:air".equals(itemId.toString())) {
			return Optional.of(Component.translatable("config.stack_refill.item_rules.invalid", rawId));
		}
		return Optional.empty();
	}

	private static Optional<Component> validateItemList(List<String> values) {
		Set<Identifier> seen = new HashSet<>();
		for (String value : values) {
			Identifier itemId = Identifier.tryParse(value.trim());
			if (itemId != null && !seen.add(itemId)) {
				return Optional.of(Component.translatable("config.stack_refill.item_rules.duplicate", itemId));
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
