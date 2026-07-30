package dev.inventorysweeper;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InventorySweeperClient implements ClientModInitializer {
	public static final String MOD_ID = "inventory_sweeper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SweeperConfig config;
	private static InventoryCleaner cleaner;
	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		config = SweeperConfig.load();
		cleaner = new InventoryCleaner(Minecraft.getInstance(), config);
		DroppedItemVisuals.initialize();

		KeyMapping.Category category = InventoryToolkitClient.keyCategory();
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_sweeper.toggle",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			category
		));

		SweeperCommands.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				setEnabled(!config.enabled);
			}
			cleaner.tick();
			DroppedItemVisuals.tick(client);
		});

		LOGGER.info("Inventory Sweeper initialized");
	}

	public static SweeperConfig config() {
		return config;
	}

	public static InventoryCleaner cleaner() {
		return cleaner;
	}

	public static void setEnabled(boolean enabled) {
		config.enabled = enabled;
		config.save();
		cleaner.resnapshot();
		notifyPlayer(Component.translatable(enabled
			? "message.inventory_sweeper.enabled"
			: "message.inventory_sweeper.disabled"));
	}

	public static void reloadConfig() {
		config = SweeperConfig.load();
		cleaner.setConfig(config);
	}

	public static void notifyPlayer(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && config.notifications) {
			client.player.sendOverlayMessage(message);
		}
	}

}
