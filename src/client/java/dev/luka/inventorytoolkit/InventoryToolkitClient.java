package dev.luka.inventorytoolkit;

import be.luka.quickoffhand.QuickOffhandClient;
import com.mojang.blaze3d.platform.InputConstants;
import dev.hydro.hydrosbundles.HydrosBundlesClient;
import dev.hydro.hydrosrecipebook.HydrosRecipeBookClient;
import dev.inventorysweeper.InventorySweeperClient;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchClient;
import dev.luka.inventorytoolkit.compact.CompactClient;
import dev.luka.inventorytoolkit.config.InventoryToolkitScreen;
import dev.luka.inventorytoolkit.dropprotection.DropProtectionClient;
import dev.luka.inventorytoolkit.inventorysort.InventorySortClient;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import dev.luka.inventorytoolkit.quicktrade.QuickTradeClient;
import dev.luka.inventorytoolkit.shulkerpreview.ShulkerPreviewClient;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import dev.luka.shiftdragtransfer.ShiftDragTransferClient;
import dev.luka.smarthotbar.SmartHotbarClient;
import dev.luka.stackrefill.StackRefillClient;
import net.fabricmc.api.ClientModInitializer;
import dev.luka.inventorytoolkit.config.InventoryToolkitConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InventoryToolkitClient implements ClientModInitializer {
	public static final String MOD_ID = "inventory_toolkit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyMapping.Category keyCategory;
	private static KeyMapping configureKey;
	private static InventoryToolkitConfig config;
	private static boolean welcomePending;
	private static boolean openSettingsPending;

	@Override
	public void onInitializeClient() {
		keyCategory = KeyMapping.Category.register(id("controls"));
		configureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.configure",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			keyCategory
		));

		ClientTickEvents.START_CLIENT_TICK.register(client ->
			InventoryActionCoordinator.beginTick()
		);

		config = InventoryToolkitConfig.load();
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) ->
			welcomePending = !config.welcomeShown
		);
		// The command runs while the chat screen is still open, and closing the
		// chat would close the settings again, so they open on the next tick.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal("inventorytoolkit").executes(context -> {
				openSettingsPending = true;
				return 1;
			}))
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (configureKey.consumeClick()) {
				client.gui.setScreen(new InventoryToolkitScreen(client.gui.screen()));
			}
			if (openSettingsPending) {
				openSettingsPending = false;
				client.gui.setScreen(new InventoryToolkitScreen(null));
			}
			if (welcomePending && client.player != null) {
				welcomePending = false;
				showWelcome(client.player);
			}
		});

		ScreenMessages.initialize();
		SlotLockClient.initialize();
		DropProtectionClient.initialize();
		ShulkerPreviewClient.initialize();
		InventorySortClient.initialize();
		AutoToolSwitchClient.initialize();
		QuickStackClient.initialize();
		CompactClient.initialize();
		QuickTradeClient.initialize();

		// User-requested recipe actions, loadouts, and offhand swaps have first
		// priority, followed by bundle routing, refills, and automatic sweeping.
		new HydrosRecipeBookClient().onInitializeClient();
		new SmartHotbarClient().onInitializeClient();
		new QuickOffhandClient().onInitializeClient();
		new HydrosBundlesClient().onInitializeClient();
		new StackRefillClient().onInitializeClient();
		new InventorySweeperClient().onInitializeClient();
		new ShiftDragTransferClient().onInitializeClient();

		LOGGER.info("Inventory Toolkit initialized");
	}

	/**
	 * Shown once, on the first join after installing, so the toolkit's
	 * settings and unbound keys can be found.
	 */
	private static void showWelcome(LocalPlayer player) {
		config.welcomeShown = true;
		config.save();
		Component link = Component.translatable("message.inventory_toolkit.welcome.link")
			.withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand("/inventorytoolkit"))
				.withHoverEvent(new HoverEvent.ShowText(
					Component.translatable("message.inventory_toolkit.welcome.link.tooltip")
				)));
		player.sendSystemMessage(Component.translatable(
			"message.inventory_toolkit.welcome",
			Component.translatable("screen.inventory_toolkit.title").withStyle(ChatFormatting.GOLD),
			link
		));
	}

	public static KeyMapping.Category keyCategory() {
		return keyCategory;
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
