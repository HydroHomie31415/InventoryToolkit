package dev.hydro.hydrosbundles;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hydro.hydrosbundles.config.HydrosBundlesSettingsScreen;
import dev.hydro.hydrosbundles.screen.BundleOverviewScreen;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HydrosBundlesClient implements ClientModInitializer {
	public static final String MOD_ID = "hydros_bundles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static HydrosBundlesConfig config;
	private static QuickStackManager quickStackManager;
	private static KeyMapping quickStackKey;
	private static KeyMapping openBundlesKey;
	private static KeyMapping configureKey;
	private static int lastBundleIndex = -1;

	@Override
	public void onInitializeClient() {
		config = HydrosBundlesConfig.load();
		quickStackManager = new QuickStackManager(Minecraft.getInstance(), config);
		KeyMapping.Category category = InventoryToolkitClient.keyCategory();

		quickStackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hydros_bundles.quick_stack",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_K,
			category
		));
		openBundlesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hydros_bundles.open",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_B,
			category
		));
		configureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hydros_bundles.configure",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (quickStackKey.consumeClick()) {
				if (quickStackManager.isRunning()) {
					quickStackManager.cancel();
					notifyPlayer(Component.translatable("message.hydros_bundles.cancelled"));
				} else {
					quickStackManager.start();
				}
			}
			while (openBundlesKey.consumeClick()) {
				if (client.player != null) {
					client.gui.setScreen(new BundleOverviewScreen(client.gui.screen()));
				}
			}
			while (configureKey.consumeClick()) {
				client.gui.setScreen(new HydrosBundlesSettingsScreen(client.gui.screen()));
			}
			quickStackManager.tick();
		});

		LOGGER.info("Hydro's Bundles initialized");
	}

	public static HydrosBundlesConfig config() {
		return config;
	}

	public static void reloadManagerConfig() {
		config.normalize();
		quickStackManager.setConfig(config);
	}

	public static int lastBundleIndex() {
		return lastBundleIndex;
	}

	public static void setLastBundleIndex(int inventoryIndex) {
		lastBundleIndex = inventoryIndex;
	}

	public static void notifyPlayer(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (config.notifications && client.player != null) {
			client.player.sendOverlayMessage(message);
		}
	}
}
