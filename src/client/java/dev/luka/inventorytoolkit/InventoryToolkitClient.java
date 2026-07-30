package dev.luka.inventorytoolkit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.inventorysweeper.InventorySweeperClient;
import dev.luka.inventorytoolkit.config.InventoryToolkitScreen;
import dev.luka.smarthotbar.SmartHotbarClient;
import dev.luka.stackrefill.StackRefillClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InventoryToolkitClient implements ClientModInitializer {
	public static final String MOD_ID = "inventory_toolkit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyMapping.Category keyCategory;
	private static KeyMapping configureKey;

	@Override
	public void onInitializeClient() {
		keyCategory = KeyMapping.Category.register(id("controls"));
		configureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.configure",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			keyCategory
		));

		ClientTickEvents.START_CLIENT_TICK.register(client ->
			InventoryActionCoordinator.beginTick()
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (configureKey.consumeClick()) {
				client.gui.setScreen(new InventoryToolkitScreen(client.gui.screen()));
			}
		});

		// User-requested loadouts have first priority, followed by refills and
		// finally automatic sweeping.
		new SmartHotbarClient().onInitializeClient();
		new StackRefillClient().onInitializeClient();
		new InventorySweeperClient().onInitializeClient();

		LOGGER.info("Inventory Toolkit initialized");
	}

	public static KeyMapping.Category keyCategory() {
		return keyCategory;
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
