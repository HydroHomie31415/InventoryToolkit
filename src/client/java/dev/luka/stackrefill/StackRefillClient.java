package dev.luka.stackrefill;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.stackrefill.config.StackRefillConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StackRefillClient implements ClientModInitializer {
	public static final String MOD_ID = "stack_refill";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static StackRefillConfig config;
	private static StackRefillManager manager;
	private static KeyMapping toggleKey;
	private static KeyMapping configureKey;

	@Override
	public void onInitializeClient() {
		config = StackRefillConfig.load();
		manager = new StackRefillManager(Minecraft.getInstance(), config);

		KeyMapping.Category category = InventoryToolkitClient.keyCategory();
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.stack_refill.toggle",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			category
		));
		configureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.stack_refill.configure",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				setEnabled(!config.enabled);
			}
			while (configureKey.consumeClick()) {
				client.gui.setScreen(StackRefillConfigScreen.create(client.gui.screen()));
			}
			manager.tick();
		});

		LOGGER.info("Stack Refill initialized");
	}

	public static StackRefillConfig config() {
		return config;
	}

	public static StackRefillManager manager() {
		return manager;
	}

	public static void onStackUsed(ItemStack before, int hotbarSlot, int beforeCount, int afterCount) {
		if (manager != null) {
			manager.onStackUsed(before, hotbarSlot, beforeCount, afterCount);
		}
	}

	public static void setEnabled(boolean enabled) {
		config.enabled = enabled;
		config.save();
		manager.clearPending();
		notifyPlayer(Component.translatable(enabled
			? "message.stack_refill.enabled"
			: "message.stack_refill.disabled"));
	}

	public static void reloadConfig() {
		config = StackRefillConfig.load();
		manager.setConfig(config);
	}

	public static void notifyPlayer(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && config.notifications) {
			client.player.sendOverlayMessage(message);
		}
	}

}
