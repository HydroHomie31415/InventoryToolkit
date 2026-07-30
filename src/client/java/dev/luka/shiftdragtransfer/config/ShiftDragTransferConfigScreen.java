package dev.luka.shiftdragtransfer.config;

import dev.luka.shiftdragtransfer.ShiftDragTransferClient;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.DestinationPriority;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.DragMouseButton;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.InputModifier;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.ScrollDirection;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.SlotSearchOrder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.Locale;

public final class ShiftDragTransferConfigScreen {
	private ShiftDragTransferConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ShiftDragTransferConfig config = ShiftDragTransferClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.shift_drag_transfer.title"))
			.setDoesConfirmSave(true)
			.setShouldTabsSmoothScroll(true)
			.setShouldListSmoothScroll(true)
			.alwaysShowTabs();
		ConfigEntryBuilder entries = builder.entryBuilder();

		addGeneralSettings(builder, entries, config);
		addDragSettings(builder, entries, config);
		addScrollSettings(builder, entries, config);
		addDestinationSettings(builder, entries, config);

		builder.setSavingRunnable(config::save);
		return builder.build();
	}

	private static void addGeneralSettings(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ShiftDragTransferConfig config
	) {
		ConfigCategory category = category(
			builder,
			"general",
			"config.shift_drag_transfer.category.general.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.enabled"),
				config.enabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.shift_drag_transfer.enabled.tooltip"))
			.setSaveConsumer(value -> config.enabled = value)
			.build());
	}

	private static void addDragSettings(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ShiftDragTransferConfig config
	) {
		ConfigCategory category = category(
			builder,
			"drag",
			"config.shift_drag_transfer.category.drag.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.drag.enabled"),
				config.dragEnabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.shift_drag_transfer.drag.enabled.tooltip"))
			.setSaveConsumer(value -> config.dragEnabled = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.shift_drag_transfer.drag.modifier"),
				InputModifier.class,
				config.dragModifier
			)
			.setDefaultValue(InputModifier.SHIFT)
			.setEnumNameProvider(value -> enumName("modifier", value))
			.setTooltip(Component.translatable("config.shift_drag_transfer.drag.modifier.tooltip"))
			.setSaveConsumer(value -> config.dragModifier = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.shift_drag_transfer.drag.button"),
				DragMouseButton.class,
				config.dragMouseButton
			)
			.setDefaultValue(DragMouseButton.LEFT)
			.setEnumNameProvider(value -> enumName("mouse_button", value))
			.setTooltip(Component.translatable("config.shift_drag_transfer.drag.button.tooltip"))
			.setSaveConsumer(value -> config.dragMouseButton = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.drag.from_container"),
				config.dragFromContainer
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.drag.from_container.tooltip"
			))
			.setSaveConsumer(value -> config.dragFromContainer = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.drag.from_player"),
				config.dragFromPlayerInventory
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.drag.from_player.tooltip"
			))
			.setSaveConsumer(value -> config.dragFromPlayerInventory = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.drag.start_empty"),
				config.allowDragStartOnEmptySlot
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.drag.start_empty.tooltip"
			))
			.setSaveConsumer(value -> config.allowDragStartOnEmptySlot = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.drag.revisit"),
				config.allowDragSlotRevisit
			)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("config.shift_drag_transfer.drag.revisit.tooltip"))
			.setSaveConsumer(value -> config.allowDragSlotRevisit = value)
			.build());
	}

	private static void addScrollSettings(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ShiftDragTransferConfig config
	) {
		ConfigCategory category = category(
			builder,
			"scroll",
			"config.shift_drag_transfer.category.scroll.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.scroll.enabled"),
				config.scrollEnabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.shift_drag_transfer.scroll.enabled.tooltip"))
			.setSaveConsumer(value -> config.scrollEnabled = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.shift_drag_transfer.scroll.modifier"),
				InputModifier.class,
				config.scrollModifier
			)
			.setDefaultValue(InputModifier.NONE)
			.setEnumNameProvider(value -> enumName("modifier", value))
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.scroll.modifier.tooltip"
			))
			.setSaveConsumer(value -> config.scrollModifier = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.shift_drag_transfer.scroll.direction"),
				ScrollDirection.class,
				config.scrollDirection
			)
			.setDefaultValue(ScrollDirection.STANDARD)
			.setEnumNameProvider(value -> enumName("scroll_direction", value))
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.scroll.direction.tooltip"
			))
			.setSaveConsumer(value -> config.scrollDirection = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.scroll.to_player"),
				config.scrollContainerToPlayer
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.scroll.to_player.tooltip"
			))
			.setSaveConsumer(value -> config.scrollContainerToPlayer = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.scroll.to_container"),
				config.scrollPlayerToContainer
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.scroll.to_container.tooltip"
			))
			.setSaveConsumer(value -> config.scrollPlayerToContainer = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.shift_drag_transfer.scroll.amount"),
				config.itemsPerScroll,
				1,
				16
			)
			.setDefaultValue(1)
			.setTextGetter(value -> Component.translatable(
				"config.shift_drag_transfer.scroll.amount.value",
				value
			))
			.setTooltip(Component.translatable("config.shift_drag_transfer.scroll.amount.tooltip"))
			.setSaveConsumer(value -> config.itemsPerScroll = value)
			.build());

		category.addEntry(entries.startIntSlider(
				Component.translatable("config.shift_drag_transfer.scroll.cooldown"),
				config.scrollCooldownMilliseconds,
				0,
				500
			)
			.setDefaultValue(0)
			.setTextGetter(value -> value == 0
				? Component.translatable("config.shift_drag_transfer.scroll.cooldown.none")
				: Component.translatable(
					"config.shift_drag_transfer.scroll.cooldown.value",
					value
				))
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.scroll.cooldown.tooltip"
			))
			.setSaveConsumer(value -> config.scrollCooldownMilliseconds = value)
			.build());
	}

	private static void addDestinationSettings(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			ShiftDragTransferConfig config
	) {
		ConfigCategory category = category(
			builder,
			"destinations",
			"config.shift_drag_transfer.category.destinations.description"
		);

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.inventory.main"),
				config.useMainInventory
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.inventory.main.tooltip"
			))
			.setSaveConsumer(value -> config.useMainInventory = value)
			.build());

		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.shift_drag_transfer.inventory.hotbar"),
				config.useHotbar
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.inventory.hotbar.tooltip"
			))
			.setSaveConsumer(value -> config.useHotbar = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.shift_drag_transfer.destination.priority"),
				DestinationPriority.class,
				config.destinationPriority
			)
			.setDefaultValue(DestinationPriority.MATCHING_STACKS)
			.setEnumNameProvider(value -> enumName("destination_priority", value))
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.destination.priority.tooltip"
			))
			.setSaveConsumer(value -> config.destinationPriority = value)
			.build());

		category.addEntry(entries.startEnumSelector(
				Component.translatable("config.shift_drag_transfer.destination.order"),
				SlotSearchOrder.class,
				config.slotSearchOrder
			)
			.setDefaultValue(SlotSearchOrder.FIRST_TO_LAST)
			.setEnumNameProvider(value -> enumName("slot_order", value))
			.setTooltip(Component.translatable(
				"config.shift_drag_transfer.destination.order.tooltip"
			))
			.setSaveConsumer(value -> config.slotSearchOrder = value)
			.build());
	}

	private static ConfigCategory category(
			ConfigBuilder builder,
			String name,
			String descriptionKey
	) {
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.shift_drag_transfer.category." + name)
		);
		category.setDescription(new FormattedText[]{Component.translatable(descriptionKey)});
		return category;
	}

	private static Component enumName(String field, Enum<?> value) {
		return Component.translatable(
			"config.shift_drag_transfer."
				+ field
				+ "."
				+ value.name().toLowerCase(Locale.ROOT)
		);
	}
}
