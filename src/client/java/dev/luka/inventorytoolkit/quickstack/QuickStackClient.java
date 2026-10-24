package dev.luka.inventorytoolkit.quickstack;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.CornerButtons;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class QuickStackClient {
	private static final CornerButtons.State SORT_CONFIRM = new CornerButtons.State(
		"button.inventory_toolkit.sort.confirm",
		"button.inventory_toolkit.sort.confirm.tooltip"
	);

	private static QuickStackConfig config;
	private static KeyMapping quickStackKey;
	private static KeyMapping searchKey;
	private static KeyMapping sortKey;
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
		sortKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.sort_nearby",
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
				nearby.cancelByPlayer();
			} else if (client.gui.screen() == null) {
				nearby.start(config);
			}
		}
		while (sortKey.consumeClick()) {
			if (nearby.isRunning()) {
				nearby.cancelByPlayer();
			} else if (client.gui.screen() == null) {
				nearby.startSort(config);
			}
		}
		while (searchKey.consumeClick()) {
			if (client.gui.screen() == null) {
				openSearch(client, false);
			}
		}
		nearby.tick(config);
	}

	private static void openSearch(Minecraft client, boolean returnToInventory) {
		if (config.memoryEnabled) {
			client.gui.setScreen(new ContainerSearchScreen(returnToInventory, ""));
		} else {
			explain(client, Component.translatable("message.inventory_toolkit.disabled.scan"));
		}
	}

	private static void explain(Minecraft client, Component message) {
		if (client.player != null) {
			client.gui.hud.setOverlayMessage(message, false);
		}
	}

	public static KeyMapping quickStackKey() {
		return quickStackKey;
	}

	public static KeyMapping searchKey() {
		return searchKey;
	}

	public static KeyMapping sortKey() {
		return sortKey;
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
			Component name = stack.getItem().getName(stack);
			if (containers > 0) {
				explain(client, Component.translatable(
					"message.inventory_toolkit.container_search.highlighting",
					containers,
					name
				));
			} else {
				Component elsewhere = ContainerMemory.describeOtherDimensions(
					BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
				);
				explain(client, elsewhere == null
					? Component.translatable("message.inventory_toolkit.container_search.not_found", containers, name)
					: Component.translatable("message.inventory_toolkit.container_search.not_found_here", name, elsewhere));
			}
			return false;
		});
	}

	private static void attachToContainer(Minecraft client, AbstractContainerScreen<?> screen) {
		ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, event) -> {
			if (!config.enabled || !quickStackKey.matches(event)) {
				return true;
			}
			depositIntoOpenContainer(client, screen);
			return false;
		});

		if (config.enabled && config.showButton) {
			CornerButtons.add(
				screen,
				0,
				"button.inventory_toolkit.quick_stack",
				"button.inventory_toolkit.quick_stack.tooltip",
				() -> depositIntoOpenContainer(client, screen)
			);
		}
	}

	private static void depositIntoOpenContainer(Minecraft client, AbstractContainerScreen<?> screen) {
		if (!screen.getMenu().getCarried().isEmpty()) {
			explain(client, Component.translatable("message.inventory_toolkit.cursor_not_empty"));
			return;
		}
		int moved = QuickStackDepositor.deposit(client, screen.getMenu(), config);
		if (moved > 0) {
			if (config.showMessages) {
				explain(client, Component.translatable("message.inventory_toolkit.quick_stack.finished_here", moved));
			}
		} else {
			explain(client, Component.translatable("message.inventory_toolkit.quick_stack.nothing_matched_here"));
		}
	}

	private static void attachToInventory(Minecraft client, InventoryScreen screen) {
		boolean nearbyAvailable = config.enabled && config.nearbyEnabled;
		ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, event) -> {
			if (quickStackKey.matches(event)) {
				startNearbyFromInventory(NearbyQuickStack.Mode.QUICK_STACK);
				return false;
			}
			if (sortKey.matches(event)) {
				startNearbyFromInventory(NearbyQuickStack.Mode.SORT);
				return false;
			}
			if (searchKey.matches(event)) {
				openSearch(client, true);
				return false;
			}
			return true;
		});

		// While a run is going, any of these buttons cancels it.
		Supplier<CornerButtons.State> cancelWhileRunning = () ->
			nearby.isRunning() ? CornerButtons.State.CANCEL : null;
		int buttonIndex = 0;
		List<Button> cancelButtons = new ArrayList<>();
		if (nearbyAvailable && config.showInventoryButton) {
			cancelButtons.add(CornerButtons.add(
				screen,
				buttonIndex++,
				"button.inventory_toolkit.quick_stack",
				"button.inventory_toolkit.quick_stack.nearby.tooltip",
				() -> startNearbyFromInventory(NearbyQuickStack.Mode.QUICK_STACK),
				cancelWhileRunning
			));
		}
		if (config.memoryEnabled && config.showSearchButton) {
			CornerButtons.add(
				screen,
				buttonIndex++,
				"button.inventory_toolkit.container_search",
				"button.inventory_toolkit.container_search.tooltip",
				() -> openSearch(client, true)
			);
		}
		if (config.sortEnabled && config.showSortButton) {
			cancelButtons.add(CornerButtons.add(
				screen,
				buttonIndex,
				"button.inventory_toolkit.sort",
				"button.inventory_toolkit.sort.tooltip",
				() -> startNearbyFromInventory(NearbyQuickStack.Mode.SORT),
				() -> nearby.isRunning()
					? CornerButtons.State.CANCEL
					: nearby.isSortPreviewPending() ? SORT_CONFIRM : null
			));
		}

		// Inventory clicks would be ignored while a container menu is active
		// anyway; swallow them so nothing appears to happen and then revert.
		// The quick stack and sort buttons stay clickable so they can cancel the run.
		ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) ->
			!nearby.isRunning()
				|| cancelButtons.stream().anyMatch(button -> button.isMouseOver(event.x(), event.y()))
		);
		ScreenMouseEvents.allowMouseScroll(screen).register((ignored, mouseX, mouseY, horizontal, vertical) ->
			!nearby.isRunning()
		);
	}

	private static void startNearbyFromInventory(NearbyQuickStack.Mode mode) {
		if (nearby.isRunning()) {
			nearby.cancelByPlayer();
		} else if (mode == NearbyQuickStack.Mode.SORT) {
			nearby.startSort(config);
		} else {
			nearby.start(config, mode, null);
		}
	}
}
