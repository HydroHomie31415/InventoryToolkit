package dev.luka.inventorytoolkit.autotool;

import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.ActivationMode;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.CorrectToolPolicy;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.DurabilityPreference;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.ListMode;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.LootPreference;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.LootPreferenceStrength;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig.ReturnMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
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

public final class AutoToolSwitchConfigScreen {
	private AutoToolSwitchConfigScreen() {
	}

	public static Screen create(Screen parent) {
		AutoToolSwitchConfig config = AutoToolSwitchClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.auto_tool.title"))
			.setDoesConfirmSave(true)
			.setShouldTabsSmoothScroll(true)
			.setShouldListSmoothScroll(true)
			.alwaysShowTabs();
		ConfigEntryBuilder entries = builder.entryBuilder();

		addActivation(builder, entries, config);
		addSelection(builder, entries, config);
		addTools(builder, entries, config);
		addRules(builder, entries, config);
		addReturnAndFeedback(builder, entries, config);

		builder.setSavingRunnable(() -> {
			config.save();
			Minecraft.getInstance().options.save();
			AutoToolSwitchClient.resetSession();
		});
		return builder.build();
	}

	private static void addActivation(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		AutoToolSwitchConfig config
	) {
		ConfigCategory category = category(builder, "activation");
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.inventory_toolkit.auto_tool.enabled"),
				config.enabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.inventory_toolkit.auto_tool.enabled.tooltip"))
			.setSaveConsumer(value -> config.enabled = value)
			.build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.activation_mode"),
				ActivationMode.class,
				config.activationMode
			)
			.setDefaultValue(ActivationMode.PAUSE_WHILE_KEY_HELD)
			.setEnumNameProvider(value -> enumName("activation_mode", value))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.activation_mode.tooltip"
			))
			.setSaveConsumer(value -> config.activationMode = value)
			.build());
		category.addEntry(entries.fillKeybindingField(
				Component.translatable("config.inventory_toolkit.auto_tool.activation_key"),
				AutoToolSwitchClient.activationKey()
			)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.activation_key.tooltip"
			))
			.build());
		category.addEntry(entries.fillKeybindingField(
				Component.translatable("config.inventory_toolkit.auto_tool.toggle_key"),
				AutoToolSwitchClient.toggleKey()
			)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.toggle_key.tooltip"
			))
			.build());
		category.addEntry(toggle(
			entries,
			"allow_sneaking",
			config.allowWhileSneaking,
			false,
			value -> config.allowWhileSneaking = value
		));
		category.addEntry(toggle(
			entries,
			"allow_creative",
			config.allowInCreative,
			false,
			value -> config.allowInCreative = value
		));
		category.addEntry(toggle(
			entries,
			"pause_using",
			config.pauseWhileUsingItem,
			true,
			value -> config.pauseWhileUsingItem = value
		));
	}

	private static void addSelection(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		AutoToolSwitchConfig config
	) {
		ConfigCategory category = category(builder, "selection");
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.correct_tool"),
				CorrectToolPolicy.class,
				config.correctToolPolicy
			)
			.setDefaultValue(CorrectToolPolicy.PREFER)
			.setEnumNameProvider(value -> enumName("correct_tool", value))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.correct_tool.tooltip"
			))
			.setSaveConsumer(value -> config.correctToolPolicy = value)
			.build());
		category.addEntry(entries.startDoubleField(
				Component.translatable("config.inventory_toolkit.auto_tool.minimum_speed"),
				config.minimumMiningSpeed
			)
			.setDefaultValue(1.01)
			.setMin(1.0)
			.setMax(100.0)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.minimum_speed.tooltip"
			))
			.setSaveConsumer(value -> config.minimumMiningSpeed = value)
			.build());
		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.auto_tool.minimum_improvement"),
				config.minimumSpeedImprovementPercent,
				0,
				500
			)
			.setDefaultValue(0)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_toolkit.auto_tool.percent",
				value
			))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.minimum_improvement.tooltip"
			))
			.setSaveConsumer(value -> config.minimumSpeedImprovementPercent = value)
			.build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.loot_preference"),
				LootPreference.class,
				config.lootPreference
			)
			.setDefaultValue(LootPreference.NONE)
			.setEnumNameProvider(value -> enumName("loot_preference", value))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.loot_preference.tooltip"
			))
			.setSaveConsumer(value -> config.lootPreference = value)
			.build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.loot_strength"),
				LootPreferenceStrength.class,
				config.lootPreferenceStrength
			)
			.setDefaultValue(LootPreferenceStrength.TIEBREAKER)
			.setEnumNameProvider(value -> enumName("loot_strength", value))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.loot_strength.tooltip"
			))
			.setSaveConsumer(value -> config.lootPreferenceStrength = value)
			.build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.durability_preference"),
				DurabilityPreference.class,
				config.durabilityPreference
			)
			.setDefaultValue(DurabilityPreference.MOST_REMAINING)
			.setEnumNameProvider(value -> enumName("durability_preference", value))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.durability_preference.tooltip"
			))
			.setSaveConsumer(value -> config.durabilityPreference = value)
			.build());
	}

	private static void addTools(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		AutoToolSwitchConfig config
	) {
		ConfigCategory category = category(builder, "tools");
		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.auto_tool.remaining_durability"),
				config.minimumRemainingDurability,
				0,
				1000
			)
			.setDefaultValue(2)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.remaining_durability.tooltip"
			))
			.setSaveConsumer(value -> config.minimumRemainingDurability = value)
			.build());
		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.auto_tool.remaining_percent"),
				config.minimumRemainingDurabilityPercent,
				0,
				100
			)
			.setDefaultValue(0)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_toolkit.auto_tool.percent",
				value
			))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.remaining_percent.tooltip"
			))
			.setSaveConsumer(value -> config.minimumRemainingDurabilityPercent = value)
			.build());

		category.addEntry(entries.startTextDescription(Component.translatable(
			"config.inventory_toolkit.auto_tool.tool_types.description"
		)).build());
		category.addEntry(toolToggle(entries, "pickaxes", config.allowPickaxes,
			value -> config.allowPickaxes = value));
		category.addEntry(toolToggle(entries, "axes", config.allowAxes,
			value -> config.allowAxes = value));
		category.addEntry(toolToggle(entries, "shovels", config.allowShovels,
			value -> config.allowShovels = value));
		category.addEntry(toolToggle(entries, "hoes", config.allowHoes,
			value -> config.allowHoes = value));
		category.addEntry(toolToggle(entries, "shears", config.allowShears,
			value -> config.allowShears = value));
		category.addEntry(toolToggle(entries, "swords", config.allowSwords,
			value -> config.allowSwords = value));
		category.addEntry(toolToggle(entries, "other", config.allowOtherEffectiveItems,
			value -> config.allowOtherEffectiveItems = value));

		category.addEntry(entries.startTextDescription(Component.translatable(
			"config.inventory_toolkit.auto_tool.hotbar.description"
		)).build());
		for (int slot = 0; slot < InventorySlotCount.VALUE; slot++) {
			int hotbarSlot = slot;
			category.addEntry(entries.startBooleanToggle(
					Component.translatable(
						"config.inventory_toolkit.auto_tool.hotbar_slot",
						slot + 1
					),
					config.enabledHotbarSlots[slot]
				)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.enabledHotbarSlots[hotbarSlot] = value)
				.build());
		}
	}

	private static void addRules(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		AutoToolSwitchConfig config
	) {
		ConfigCategory category = category(builder, "rules");
		category.addEntry(entries.startTextDescription(Component.translatable(
			"config.inventory_toolkit.auto_tool.rules.description"
		)).build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.block_list_mode"),
				ListMode.class,
				config.blockListMode
			)
			.setDefaultValue(ListMode.BLACKLIST)
			.setEnumNameProvider(value -> enumName("list_mode", value))
			.setSaveConsumer(value -> config.blockListMode = value)
			.build());
		category.addEntry(entries.startStrList(
				Component.translatable("config.inventory_toolkit.auto_tool.block_rules"),
				config.blockRules.stream().sorted().toList()
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setCellErrorSupplier(value -> validateRule(value, true))
			.setErrorSupplier(AutoToolSwitchConfigScreen::validateRuleList)
			.setSaveConsumer(values -> config.blockRules = canonicalRules(values))
			.build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.item_list_mode"),
				ListMode.class,
				config.itemListMode
			)
			.setDefaultValue(ListMode.BLACKLIST)
			.setEnumNameProvider(value -> enumName("list_mode", value))
			.setSaveConsumer(value -> config.itemListMode = value)
			.build());
		category.addEntry(entries.startStrList(
				Component.translatable("config.inventory_toolkit.auto_tool.item_rules"),
				config.itemRules.stream().sorted().toList()
			)
			.setDefaultValue(List.of())
			.setExpanded(true)
			.setInsertInFront(false)
			.setCellErrorSupplier(value -> validateRule(value, false))
			.setErrorSupplier(AutoToolSwitchConfigScreen::validateRuleList)
			.setSaveConsumer(values -> config.itemRules = canonicalRules(values))
			.build());
	}

	private static void addReturnAndFeedback(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		AutoToolSwitchConfig config
	) {
		ConfigCategory category = category(builder, "return_feedback");
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.inventory_toolkit.auto_tool.return_mode"),
				ReturnMode.class,
				config.returnMode
			)
			.setDefaultValue(ReturnMode.AFTER_MINING)
			.setEnumNameProvider(value -> enumName("return_mode", value))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.return_mode.tooltip"
			))
			.setSaveConsumer(value -> config.returnMode = value)
			.build());
		category.addEntry(entries.startIntSlider(
				Component.translatable("config.inventory_toolkit.auto_tool.return_delay"),
				config.returnDelayTicks,
				0,
				40
			)
			.setDefaultValue(2)
			.setTextGetter(value -> Component.translatable(
				"config.inventory_toolkit.auto_tool.ticks",
				value
			))
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool.return_delay.tooltip"
			))
			.setSaveConsumer(value -> config.returnDelayTicks = value)
			.build());
		category.addEntry(toggle(
			entries,
			"cancel_manual",
			config.cancelReturnOnManualSlotChange,
			true,
			value -> config.cancelReturnOnManualSlotChange = value
		));
		category.addEntry(toggle(
			entries,
			"show_switch",
			config.showSwitchMessage,
			false,
			value -> config.showSwitchMessage = value
		));
		category.addEntry(toggle(
			entries,
			"show_toggle",
			config.showToggleMessage,
			true,
			value -> config.showToggleMessage = value
		));
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> toggle(
		ConfigEntryBuilder entries,
		String name,
		boolean value,
		boolean defaultValue,
		java.util.function.Consumer<Boolean> saveConsumer
	) {
		return entries.startBooleanToggle(
				Component.translatable("config.inventory_toolkit.auto_tool." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.auto_tool." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> toolToggle(
		ConfigEntryBuilder entries,
		String name,
		boolean value,
		java.util.function.Consumer<Boolean> saveConsumer
	) {
		return entries.startBooleanToggle(
				Component.translatable("config.inventory_toolkit.auto_tool.tool." + name),
				value
			)
			.setDefaultValue(true)
			.setSaveConsumer(saveConsumer)
			.build();
	}

	private static ConfigCategory category(ConfigBuilder builder, String name) {
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.auto_tool.category." + name
		));
		category.setDescription(new FormattedText[]{Component.translatable(
			"config.inventory_toolkit.auto_tool.category." + name + ".description"
		)});
		return category;
	}

	private static Component enumName(String field, Enum<?> value) {
		return Component.translatable(
			"config.inventory_toolkit.auto_tool." + field + "."
				+ value.name().toLowerCase(Locale.ROOT)
		);
	}

	private static Optional<Component> validateRule(String rawRule, boolean blockRule) {
		String value = rawRule.trim();
		boolean tag = value.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
		if (id == null) {
			return Optional.of(Component.translatable(
				"config.inventory_toolkit.auto_tool.rule.invalid",
				rawRule
			));
		}
		if (!tag) {
			boolean exists = blockRule
				? BuiltInRegistries.BLOCK.containsKey(id)
				: BuiltInRegistries.ITEM.containsKey(id);
			if (!exists) {
				return Optional.of(Component.translatable(
					"config.inventory_toolkit.auto_tool.rule.unknown",
					rawRule
				));
			}
		}
		return Optional.empty();
	}

	private static Optional<Component> validateRuleList(List<String> values) {
		Set<String> seen = new HashSet<>();
		for (String value : values) {
			String normalized = value.trim().toLowerCase(Locale.ROOT);
			if (!seen.add(normalized)) {
				return Optional.of(Component.translatable(
					"config.inventory_toolkit.auto_tool.rule.duplicate",
					value
				));
			}
		}
		return Optional.empty();
	}

	private static LinkedHashSet<String> canonicalRules(List<String> values) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			if (!value.isBlank()) {
				result.add(value.trim().toLowerCase(Locale.ROOT));
			}
		}
		return result;
	}

	private static final class InventorySlotCount {
		private static final int VALUE = 9;

		private InventorySlotCount() {
		}
	}
}
