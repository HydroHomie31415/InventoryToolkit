package dev.luka.inventorytoolkit.quickstack;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.containersearch.ContainerHighlights;
import dev.luka.inventorytoolkit.containersearch.ContainerMemory;
import dev.luka.inventorytoolkit.containersearch.ContainerSearchScreen;
import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class QuickStackClient {
	private static final int BUTTON_WIDTH = 14;
	private static final int BUTTON_HEIGHT = 12;
	private static final int BUTTON_INSET_X = 7;
	private static final int BUTTON_INSET_Y = 4;
	private static final int BUTTON_GAP = 2;

	private static QuickStackConfig config;
	private static KeyMapping quickStackKey;
	private static KeyMapping searchKey;
	private static NearbyQuickStack nearby;

	private QuickStackClient() {
	}

	public static void initialize() {
		config = QuickStackConfig.load();
		quickStackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.quick_stack",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			InventoryToolkitClient.keyCategory()
		));
		searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.container_search",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			InventoryToolkitClient.keyCategory()
		));

		nearby = new NearbyQuickStack(Minecraft.getInstance());
		ContainerMemory.initialize();
		ContainerHighlights.initialize();

		ScreenEvents.AFTER_INIT.register(QuickStackClient::attach);
		// Registered after the coordinator resets each tick, so an active run
		// owns the whole tick before any other module can act.
		ClientTickEvents.START_CLIENT_TICK.register(client -> nearby.claimTick());
		ClientTickEvents.END_CLIENT_TICK.register(QuickStackClient::endTick);
	}

	public static QuickStackConfig config() {
		return config;
	}

	public static NearbyQuickStack nearby() {
		return nearby;
	}

	private static void endTick(Minecraft client) {
		while (quickStackKey.consumeClick()) {
			if (nearby.isRunning()) {
				nearby.cancel();
			} else if (client.gui.screen() == null) {
				nearby.start(config);
			}
		}
		while (searchKey.consumeClick()) {
			if (config.memoryEnabled && client.gui.screen() == null) {
				client.gui.setScreen(new ContainerSearchScreen(false, ""));
			}
		}
		nearby.tick(config);
	}

	public static KeyMapping quickStackKey() {
		return quickStackKey;
	}

	public static KeyMapping searchKey() {
		return searchKey;
	}

	private static void attach(Minecraft client, Screen screen, int width, int height) {
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			attachAltClickHighlight(client, containerScreen);
		}
		if (screen instanceof InventoryScreen inventoryScreen) {
			attachToInventory(client, inventoryScreen);
		} else if (screen instanceof AbstractContainerScreen<?> containerScreen
			&& QuickStackDepositor.supports(containerScreen.getMenu())) {
			attachToContainer(client, containerScreen);
		}
	}

	/**
	 * Alt+left-click an item in any inventory screen to outline the remembered
	 * containers that hold it. The screen stays open.
	 */
	private static void attachAltClickHighlight(Minecraft client, AbstractContainerScreen<?> screen) {
		ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> {
			if (!config.memoryEnabled
				|| !config.altClickHighlight
				|| event.button() != InputConstants.MOUSE_BUTTON_LEFT
				|| !event.hasAltDown()) {
				return true;
			}
			Slot slot = ((AbstractContainerScreenAccessor) screen).inventoryToolkit$hoveredSlot();
			if (slot == null || !slot.hasItem()) {
				return true;
			}

			ItemStack stack = slot.getItem();
			int containers = ContainerHighlights.showItem(stack.getItem(), config.highlightSeconds);
			client.gui.hud.setOverlayMessage(Component.translatable(
				containers > 0
					? "message.inventory_toolkit.container_search.highlighting"
					: "message.inventory_toolkit.container_search.not_found",
				containers,
				stack.getItem().getName(stack)
			), false);
			return false;
		});
	}

	private static void attachToContainer(Minecraft client, AbstractContainerScreen<?> screen) {
		ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, event) -> {
			if (!config.enabled || !quickStackKey.matches(event)) {
				return true;
			}
			QuickStackDepositor.deposit(client, screen.getMenu(), config);
			return false;
		});

		if (config.enabled && config.showButton) {
			addCornerButton(
				screen,
				0,
				"button.inventory_toolkit.quick_stack",
				"button.inventory_toolkit.quick_stack.tooltip",
				() -> QuickStackDepositor.deposit(client, screen.getMenu(), config)
			);
		}
	}

	private static void attachToInventory(Minecraft client, InventoryScreen screen) {
		boolean nearbyAvailable = config.enabled && config.nearbyEnabled;
		ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, event) -> {
			if (nearbyAvailable && quickStackKey.matches(event)) {
				startNearbyFromInventory(screen);
				return false;
			}
			if (config.memoryEnabled && searchKey.matches(event)) {
				client.gui.setScreen(new ContainerSearchScreen(true, ""));
				return false;
			}
			return true;
		});

		int buttonIndex = 0;
		Button quickStackButton = null;
		if (nearbyAvailable && config.showInventoryButton) {
			quickStackButton = addCornerButton(
				screen,
				buttonIndex++,
				"button.inventory_toolkit.quick_stack",
				"button.inventory_toolkit.quick_stack.nearby.tooltip",
				() -> startNearbyFromInventory(screen)
			);
		}

		// Inventory clicks would be ignored while a container menu is active
		// anyway; swallow them so nothing appears to happen and then revert.
		// The quick stack button stays clickable so it can cancel the run.
		Button cancelButton = quickStackButton;
		ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) ->
			!nearby.isRunning() || (cancelButton != null && cancelButton.isMouseOver(event.x(), event.y()))
		);
		ScreenMouseEvents.allowMouseScroll(screen).register((ignored, mouseX, mouseY, horizontal, vertical) ->
			!nearby.isRunning()
		);
		if (config.memoryEnabled && config.showSearchButton) {
			addCornerButton(
				screen,
				buttonIndex,
				"button.inventory_toolkit.container_search",
				"button.inventory_toolkit.container_search.tooltip",
				() -> client.gui.setScreen(new ContainerSearchScreen(true, ""))
			);
		}
	}

	private static void startNearbyFromInventory(InventoryScreen screen) {
		if (nearby.isRunning()) {
			nearby.cancel();
		} else if (screen.getMenu().getCarried().isEmpty()) {
			nearby.start(config);
		}
	}

	/**
	 * @param indexFromRight 0 for the corner itself, 1 for the slot to its left, and so on
	 */
	private static Button addCornerButton(
		AbstractContainerScreen<?> screen,
		int indexFromRight,
		String labelKey,
		String tooltipKey,
		Runnable action
	) {
		AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
		Button button = Button.builder(
			Component.translatable(labelKey),
			ignored -> action.run()
		).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
		button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		Screens.getWidgets(screen).add(button);

		Runnable placeButton = () -> button.setPosition(
			bounds.inventoryToolkit$leftPos() + bounds.inventoryToolkit$imageWidth()
				- BUTTON_INSET_X - BUTTON_WIDTH - indexFromRight * (BUTTON_WIDTH + BUTTON_GAP),
			bounds.inventoryToolkit$topPos() + BUTTON_INSET_Y
		);
		placeButton.run();
		// Opening the recipe book moves the inventory sideways without
		// re-initialising the screen, so follow it every frame.
		ScreenEvents.beforeExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
			placeButton.run()
		);
		return button;
	}
}
