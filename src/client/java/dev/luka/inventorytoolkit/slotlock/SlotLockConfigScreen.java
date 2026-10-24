package dev.luka.inventorytoolkit.slotlock;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class SlotLockConfigScreen {
	private SlotLockConfigScreen() {
	}

	public static Screen create(Screen parent) {
		SlotLockConfig config = SlotLockClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.inventory_toolkit.slot_lock.title"))
			.setDoesConfirmSave(true);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
			"config.inventory_toolkit.slot_lock.category.general"
		));

		category.addEntry(entries.startTextDescription(Component.translatable(
			"config.inventory_toolkit.slot_lock.help",
			SlotLockClient.lockKey().getTranslatedKeyMessage(),
			config.lockedSlots.size()
		)).build());
		category.addEntry(toggle(entries, "enabled", config.enabled, true,
			value -> config.enabled = value));
		category.addEntry(toggle(entries, "block_quick_move", config.blockQuickMove, true,
			value -> config.blockQuickMove = value));
		category.addEntry(toggle(entries, "show_on_hotbar", config.showOnHotbar, true,
			value -> config.showOnHotbar = value));
		boolean[] unlockAll = {false};
		category.addEntry(toggle(entries, "unlock_all", false, false,
			value -> unlockAll[0] = value));

		builder.setSavingRunnable(() -> {
			if (unlockAll[0]) {
				config.lockedSlots.clear();
			}
			SlotLockClient.onConfigChanged();
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
				Component.translatable("config.inventory_toolkit.slot_lock." + name),
				value
			)
			.setDefaultValue(defaultValue)
			.setTooltip(Component.translatable(
				"config.inventory_toolkit.slot_lock." + name + ".tooltip"
			))
			.setSaveConsumer(saveConsumer)
			.build();
	}
}
