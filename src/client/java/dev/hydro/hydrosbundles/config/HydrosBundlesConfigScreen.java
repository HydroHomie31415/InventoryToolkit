package dev.hydro.hydrosbundles.config;

import dev.hydro.hydrosbundles.BundleNameMatch;
import dev.hydro.hydrosbundles.HydrosBundlesClient;
import dev.hydro.hydrosbundles.HydrosBundlesConfig;
import dev.hydro.hydrosbundles.RoutingRule;
import dev.hydro.hydrosbundles.UnmatchedBehavior;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class HydrosBundlesConfigScreen {
	private HydrosBundlesConfigScreen() {
	}

	public static Screen create(Screen parent) {
		return new HydrosBundlesSettingsScreen(parent);
	}

	public static Screen createAdvanced(Screen parent) {
		HydrosBundlesConfig config = HydrosBundlesClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.hydros_bundles.title"))
			.setDoesConfirmSave(true)
			.setShouldTabsSmoothScroll(true)
			.setShouldListSmoothScroll(true)
			.alwaysShowTabs();
		ConfigEntryBuilder entries = builder.entryBuilder();

		addQuickStack(builder, entries, config);
		addRouting(builder, entries, config);
		addInterface(builder, entries, config);

		builder.setSavingRunnable(() -> {
			config.save();
			HydrosBundlesClient.reloadManagerConfig();
		});
		return builder.build();
	}

	private static void addQuickStack(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		HydrosBundlesConfig config
	) {
		ConfigCategory category = category(builder, "quick_stack");
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.enabled"),
				config.enabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.hydros_bundles.enabled.tooltip"))
			.setSaveConsumer(value -> config.enabled = value)
			.build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.include_hotbar"),
				config.includeHotbar
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.hydros_bundles.include_hotbar.tooltip"))
			.setSaveConsumer(value -> config.includeHotbar = value)
			.build());
		category.addEntry(entries.startIntSlider(
				Component.translatable("config.hydros_bundles.action_delay"),
				config.actionDelayTicks,
				0,
				20
			)
			.setDefaultValue(2)
			.setTextGetter(value -> Component.translatable("config.hydros_bundles.ticks", value))
			.setTooltip(Component.translatable("config.hydros_bundles.action_delay.tooltip"))
			.setSaveConsumer(value -> config.actionDelayTicks = value)
			.build());
		category.addEntry(entries.startIntField(
				Component.translatable("config.hydros_bundles.max_transfers"),
				config.maxTransfersPerRun
			)
			.setDefaultValue(128)
			.setMin(1)
			.setMax(512)
			.setTooltip(Component.translatable("config.hydros_bundles.max_transfers.tooltip"))
			.setSaveConsumer(value -> config.maxTransfersPerRun = value)
			.build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.prefer_filled"),
				config.preferMostFilledBundle
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.hydros_bundles.prefer_filled.tooltip"))
			.setSaveConsumer(value -> config.preferMostFilledBundle = value)
			.build());
	}

	private static void addRouting(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		HydrosBundlesConfig config
	) {
		ConfigCategory category = category(builder, "routing");
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.hydros_bundles.name_match"),
				BundleNameMatch.class,
				config.bundleNameMatch
			)
			.setDefaultValue(BundleNameMatch.EXACT)
			.setEnumNameProvider(HydrosBundlesConfigScreen::enumName)
			.setTooltip(Component.translatable("config.hydros_bundles.name_match.tooltip"))
			.setSaveConsumer(value -> config.bundleNameMatch = value)
			.build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.case_sensitive"),
				config.caseSensitiveBundleNames
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.hydros_bundles.case_sensitive.tooltip"))
			.setSaveConsumer(value -> config.caseSensitiveBundleNames = value)
			.build());
		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.hydros_bundles.unmatched"),
				UnmatchedBehavior.class,
				config.unmatchedBehavior
			)
			.setDefaultValue(UnmatchedBehavior.ANY_UNNAMED_BUNDLE)
			.setEnumNameProvider(HydrosBundlesConfigScreen::enumName)
			.setTooltip(Component.translatable("config.hydros_bundles.unmatched.tooltip"))
			.setSaveConsumer(value -> config.unmatchedBehavior = value)
			.build());
		category.addEntry(entries.startTextDescription(
			Component.translatable("config.hydros_bundles.rules.help")
		).build());
		category.addEntry(entries.startStrList(
				Component.translatable("config.hydros_bundles.rules"),
				config.routingRules
			)
			.setDefaultValue(List.of("redstone = $redstone", "blocks = $blocks"))
			.setExpanded(true)
			.setInsertInFront(false)
			.setCellErrorSupplier(HydrosBundlesConfigScreen::validateRule)
			.setErrorSupplier(HydrosBundlesConfigScreen::validateRuleList)
			.setAddButtonTooltip(Component.translatable("config.hydros_bundles.rules.add"))
			.setRemoveButtonTooltip(Component.translatable("config.hydros_bundles.rules.remove"))
			.setSaveConsumer(values -> config.routingRules = values.stream().map(String::trim).toList())
			.build());
	}

	private static void addInterface(
		ConfigBuilder builder,
		ConfigEntryBuilder entries,
		HydrosBundlesConfig config
	) {
		ConfigCategory category = category(builder, "interface");
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.notifications"),
				config.notifications
			)
			.setDefaultValue(true)
			.setSaveConsumer(value -> config.notifications = value)
			.build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.notify_skipped"),
				config.notifySkippedItems
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.hydros_bundles.notify_skipped.tooltip"))
			.setSaveConsumer(value -> config.notifySkippedItems = value)
			.build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.remember_bundle"),
				config.rememberLastBundle
			)
			.setDefaultValue(true)
			.setSaveConsumer(value -> config.rememberLastBundle = value)
			.build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_bundles.show_inventory"),
				config.showPlayerInventory
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.hydros_bundles.show_inventory.tooltip"))
			.setSaveConsumer(value -> config.showPlayerInventory = value)
			.build());
	}

	private static ConfigCategory category(ConfigBuilder builder, String name) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.hydros_bundles.category." + name)
		);
		category.setDescription(new FormattedText[]{
			Component.translatable("config.hydros_bundles.category." + name + ".description")
		});
		return category;
	}

	private static Component enumName(Enum<?> value) {
		return Component.translatable(
			"config.hydros_bundles.enum." + value.name().toLowerCase(Locale.ROOT)
		);
	}

	private static Optional<Component> validateRule(String raw) {
		try {
			RoutingRule.parse(raw);
			return Optional.empty();
		} catch (IllegalArgumentException exception) {
			return Optional.of(Component.literal(exception.getMessage()));
		}
	}

	private static Optional<Component> validateRuleList(List<String> values) {
		Set<String> seenBundleNames = new HashSet<>();
		for (String value : values) {
			try {
				String bundleName = RoutingRule.parse(value).bundleName().toLowerCase(Locale.ROOT);
				if (!seenBundleNames.add(bundleName)) {
					return Optional.of(Component.translatable(
						"config.hydros_bundles.rules.duplicate",
						bundleName
					));
				}
			} catch (IllegalArgumentException ignored) {
				// The individual cell supplies the more specific error.
			}
		}
		return Optional.empty();
	}
}
