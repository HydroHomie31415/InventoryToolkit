package dev.luka.inventorytoolkit.autotool;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

public final class AutoToolSwitchClient {
	private static AutoToolSwitchConfig config;
	private static KeyMapping activationKey;
	private static KeyMapping toggleKey;
	private static int originalSlot = -1;
	private static int automaticallySelectedSlot = -1;
	private static int returnCountdown = -1;

	private AutoToolSwitchClient() {
	}

	public static void initialize() {
		config = AutoToolSwitchConfig.load();
		activationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.auto_tool.activation",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_LALT,
			InventoryToolkitClient.keyCategory()
		));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.auto_tool.toggle",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			InventoryToolkitClient.keyCategory()
		));

		AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (!(player instanceof LocalPlayer localPlayer)
				|| minecraft.player != localPlayer
				|| minecraft.level != level) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(position);
			if (state.getDestroySpeed(level, position) >= 0.0F) {
				onAttackBlock(minecraft, localPlayer, state);
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(AutoToolSwitchClient::endTick);
	}

	public static AutoToolSwitchConfig config() {
		return config;
	}

	public static KeyMapping activationKey() {
		return activationKey;
	}

	public static KeyMapping toggleKey() {
		return toggleKey;
	}

	public static void resetSession() {
		originalSlot = -1;
		automaticallySelectedSlot = -1;
		returnCountdown = -1;
	}

	private static void onAttackBlock(
		Minecraft minecraft,
		LocalPlayer player,
		BlockState state
	) {
		if (!isActive(minecraft, player)
			|| state.isAir()
			|| !AutoToolSelector.isBlockAllowed(state, config)) {
			return;
		}

		Inventory inventory = player.getInventory();
		int currentSlot = inventory.getSelectedSlot();
		if (originalSlot >= 0 && currentSlot != automaticallySelectedSlot) {
			if (config.cancelReturnOnManualSlotChange) {
				resetSession();
			} else {
				originalSlot = currentSlot;
				automaticallySelectedSlot = currentSlot;
			}
		}

		AutoToolSelector.Selection best = AutoToolSelector.findBest(inventory, state, config);
		if (best == null || best.slot() == currentSlot) {
			return;
		}

		if (originalSlot < 0) {
			originalSlot = currentSlot;
		}
		selectSlot(player, best.slot());
		automaticallySelectedSlot = best.slot();
		returnCountdown = -1;

		if (config.showSwitchMessage) {
			minecraft.gui.hud.setOverlayMessage(Component.translatable(
				"message.inventory_toolkit.auto_tool.switched",
				best.stack().getHoverName()
			), false);
		}
	}

	private static boolean isActive(Minecraft minecraft, LocalPlayer player) {
		if (!config.enabled
			|| minecraft.gui.screen() != null
			|| player.isSpectator()
			|| (!config.allowInCreative && player.getAbilities().instabuild)
			|| (!config.allowWhileSneaking && player.isShiftKeyDown())
			|| (config.pauseWhileUsingItem && player.isUsingItem())) {
			return false;
		}

		return switch (config.activationMode) {
			case ALWAYS -> true;
			case HOLD_KEY -> activationKey.isDown();
			case PAUSE_WHILE_KEY_HELD -> !activationKey.isDown();
		};
	}

	private static void endTick(Minecraft minecraft) {
		while (toggleKey.consumeClick()) {
			config.enabled = !config.enabled;
			config.save();
			if (config.showToggleMessage && minecraft.player != null) {
				minecraft.gui.hud.setOverlayMessage(Component.translatable(
					config.enabled
						? "message.inventory_toolkit.auto_tool.enabled"
						: "message.inventory_toolkit.auto_tool.disabled"
				), false);
			}
		}

		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			resetSession();
			return;
		}
		if (originalSlot < 0) {
			return;
		}

		int selectedSlot = player.getInventory().getSelectedSlot();
		if (selectedSlot != automaticallySelectedSlot && config.cancelReturnOnManualSlotChange) {
			resetSession();
			return;
		}
		if (minecraft.options.keyAttack.isDown()
			|| (minecraft.gameMode != null && minecraft.gameMode.isDestroying())) {
			returnCountdown = -1;
			return;
		}
		if (config.returnMode == AutoToolSwitchConfig.ReturnMode.NEVER) {
			resetSession();
			return;
		}

		if (returnCountdown < 0) {
			returnCountdown = config.returnDelayTicks;
		}
		if (returnCountdown-- > 0) {
			return;
		}

		if (Inventory.isHotbarSlot(originalSlot)) {
			selectSlot(player, originalSlot);
		}
		resetSession();
	}

	private static void selectSlot(LocalPlayer player, int slot) {
		player.getInventory().setSelectedSlot(slot);
		player.connection.send(new ServerboundSetCarriedItemPacket(slot));
	}
}
