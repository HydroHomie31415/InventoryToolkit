package dev.inventorysweeper;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class SweeperCommands {
	private SweeperCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = literal("sweeper")
				.executes(context -> status(context.getSource()))
				.then(literal("on").executes(context -> setEnabled(context.getSource(), true)))
				.then(literal("off").executes(context -> setEnabled(context.getSource(), false)))
				.then(literal("toggle").executes(context ->
					setEnabled(context.getSource(), !InventorySweeperClient.config().enabled)))
				.then(literal("status").executes(context -> status(context.getSource())))
				.then(literal("purge").executes(context -> purge(context.getSource())))
				.then(literal("reload").executes(context -> reload(context.getSource())))
				.then(itemCommand("add", SweeperCommands::addItem))
				.then(itemCommand("remove", SweeperCommands::removeItem))
				.then(itemCommand("keep", SweeperCommands::keepItem))
				.then(itemCommand("unkeep", SweeperCommands::unkeepItem));

			LiteralArgumentBuilder<FabricClientCommandSource> presets = literal("preset")
				.executes(context -> listPresets(context.getSource()))
				.then(literal("list").executes(context -> listPresets(context.getSource())));

			for (String presetId : PresetRegistry.ids()) {
				presets.then(literal(presetId)
					.executes(context -> showPreset(context.getSource(), presetId))
					.then(literal("on").executes(context -> setPreset(context.getSource(), presetId, true)))
					.then(literal("off").executes(context -> setPreset(context.getSource(), presetId, false)))
					.then(literal("toggle").executes(context -> setPreset(
						context.getSource(),
						presetId,
						!InventorySweeperClient.config().isPresetEnabled(presetId)
					))));
			}

			root.then(presets);
			dispatcher.register(root);
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> itemCommand(String name, ItemAction action) {
		return literal(name).then(argument("item", StringArgumentType.word())
			.suggests((context, builder) -> SharedSuggestionProvider.suggest(
				BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString),
				builder
			))
			.executes(context -> action.run(
				context.getSource(),
				StringArgumentType.getString(context, "item")
			)));
	}

	private static int status(FabricClientCommandSource source) {
		SweeperConfig config = InventorySweeperClient.config();
		List<String> enabledPresets = PresetRegistry.ids().stream()
			.filter(config::isPresetEnabled)
			.sorted()
			.toList();
		String presetText = enabledPresets.isEmpty() ? "none" : String.join(", ", enabledPresets);

		source.sendFeedback(Component.translatable(
			"command.inventory_sweeper.status",
			state(config.enabled),
			presetText,
			config.customItems.size(),
			config.keptItems.size()
		));
		return 1;
	}

	private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
		InventorySweeperClient.setEnabled(enabled);
		source.sendFeedback(Component.translatable(enabled
			? "command.inventory_sweeper.enabled"
			: "command.inventory_sweeper.disabled"));
		return 1;
	}

	private static int purge(FabricClientCommandSource source) {
		int count = InventorySweeperClient.cleaner().queuePurge();
		source.sendFeedback(Component.translatable("command.inventory_sweeper.purge", count));
		return count > 0 ? 1 : 0;
	}

	private static int reload(FabricClientCommandSource source) {
		InventorySweeperClient.reloadConfig();
		source.sendFeedback(Component.translatable("command.inventory_sweeper.reloaded"));
		return 1;
	}

	private static int listPresets(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable("command.inventory_sweeper.preset.header")
			.withStyle(ChatFormatting.GOLD));
		PresetRegistry.ids().stream().sorted().forEach(presetId -> {
			boolean enabled = InventorySweeperClient.config().isPresetEnabled(presetId);
			source.sendFeedback(Component.literal(" • " + presetId + ": ")
				.append(state(enabled))
				.append(Component.literal(" — "))
				.append(Component.translatable("preset.inventory_sweeper." + presetId)));
		});
		return 1;
	}

	private static int showPreset(FabricClientCommandSource source, String presetId) {
		boolean enabled = InventorySweeperClient.config().isPresetEnabled(presetId);
		source.sendFeedback(Component.literal(presetId + ": ")
			.append(state(enabled))
			.append(Component.literal(" — "))
			.append(Component.translatable("preset.inventory_sweeper." + presetId)));
		return 1;
	}

	private static int setPreset(FabricClientCommandSource source, String presetId, boolean enabled) {
		InventorySweeperClient.config().setPresetEnabled(presetId, enabled);
		InventorySweeperClient.cleaner().resnapshot();
		source.sendFeedback(Component.translatable(
			"command.inventory_sweeper.preset.changed",
			presetId,
			state(enabled)
		));
		return 1;
	}

	private static int addItem(FabricClientCommandSource source, String rawItemId) {
		Identifier itemId = validItem(source, rawItemId);
		if (itemId == null) {
			return 0;
		}
		SweeperConfig config = InventorySweeperClient.config();
		config.keptItems.remove(itemId.toString());
		boolean changed = config.customItems.add(itemId.toString());
		config.save();
		InventorySweeperClient.cleaner().resnapshot();
		source.sendFeedback(Component.translatable(
			changed ? "command.inventory_sweeper.item.added" : "command.inventory_sweeper.item.already_added",
			itemId
		));
		return 1;
	}

	private static int removeItem(FabricClientCommandSource source, String rawItemId) {
		Identifier itemId = validItem(source, rawItemId);
		if (itemId == null) {
			return 0;
		}
		SweeperConfig config = InventorySweeperClient.config();
		boolean changed = config.customItems.remove(itemId.toString());
		config.save();
		InventorySweeperClient.cleaner().resnapshot();
		source.sendFeedback(Component.translatable(
			changed ? "command.inventory_sweeper.item.removed" : "command.inventory_sweeper.item.not_custom",
			itemId
		));
		return changed ? 1 : 0;
	}

	private static int keepItem(FabricClientCommandSource source, String rawItemId) {
		Identifier itemId = validItem(source, rawItemId);
		if (itemId == null) {
			return 0;
		}
		SweeperConfig config = InventorySweeperClient.config();
		boolean changed = config.keptItems.add(itemId.toString());
		config.save();
		InventorySweeperClient.cleaner().resnapshot();
		source.sendFeedback(Component.translatable(
			changed ? "command.inventory_sweeper.item.kept" : "command.inventory_sweeper.item.already_kept",
			itemId
		));
		return 1;
	}

	private static int unkeepItem(FabricClientCommandSource source, String rawItemId) {
		Identifier itemId = validItem(source, rawItemId);
		if (itemId == null) {
			return 0;
		}
		SweeperConfig config = InventorySweeperClient.config();
		boolean changed = config.keptItems.remove(itemId.toString());
		config.save();
		InventorySweeperClient.cleaner().resnapshot();
		source.sendFeedback(Component.translatable(
			changed ? "command.inventory_sweeper.item.unkept" : "command.inventory_sweeper.item.not_kept",
			itemId
		));
		return changed ? 1 : 0;
	}

	private static Identifier validItem(FabricClientCommandSource source, String rawItemId) {
		Identifier itemId = Identifier.tryParse(rawItemId);
		if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
			source.sendError(Component.translatable("command.inventory_sweeper.item.unknown", rawItemId));
			return null;
		}
		return itemId;
	}

	private static Component state(boolean enabled) {
		return Component.translatable(enabled
			? "command.inventory_sweeper.state.on"
			: "command.inventory_sweeper.state.off")
			.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	@FunctionalInterface
	private interface ItemAction {
		int run(FabricClientCommandSource source, String itemId);
	}
}
