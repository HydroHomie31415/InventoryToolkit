package dev.luka.inventorytoolkit.quicktrade;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.CornerButtons;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.ScreenMessages;
import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import dev.luka.inventorytoolkit.mixin.client.MerchantScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;

/**
 * Quick Trade: Shift+click a trade in a villager's list, or pick a trade and
 * press the corner button or key, to repeat it as often as possible.
 */
public final class QuickTradeClient {
	// Layout of the trade list in MerchantScreen.
	private static final int TRADE_BUTTON_X = 5;
	private static final int TRADE_BUTTON_Y = 18;
	private static final int TRADE_BUTTON_WIDTH = 88;
	private static final int TRADE_BUTTON_HEIGHT = 20;
	private static final int TRADE_BUTTONS = 7;

	private static QuickTradeConfig config;
	private static QuickTrader trader;
	private static KeyMapping quickTradeKey;
	// The trade the player last clicked in the list; the button and key repeat
	// it. Kept outside the screen's init so resizing the window keeps it.
	private static MerchantScreen pickedScreen;
	private static int pickedTrade = -1;

	private QuickTradeClient() {
	}

	public static void initialize() {
		config = QuickTradeConfig.load();
		trader = new QuickTrader(Minecraft.getInstance());
		quickTradeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.quick_trade",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			InventoryToolkitClient.keyCategory()
		));

		ScreenEvents.AFTER_INIT.register(QuickTradeClient::attach);
		// Registered after the coordinator resets each tick, so an active run
		// owns the whole tick before any other module can act.
		ClientTickEvents.START_CLIENT_TICK.register(client -> trader.claimTick());
		ClientTickEvents.END_CLIENT_TICK.register(client -> trader.tick(config));
	}

	public static QuickTradeConfig config() {
		return config;
	}

	public static QuickTrader trader() {
		return trader;
	}

	public static KeyMapping quickTradeKey() {
		return quickTradeKey;
	}

	private static void attach(Minecraft client, Screen screen, int width, int height) {
		if (!config.enabled || !(screen instanceof MerchantScreen merchantScreen)) {
			return;
		}
		if (pickedScreen != merchantScreen) {
			pickedScreen = merchantScreen;
			pickedTrade = -1;
		}
		Runnable repeatPicked = () -> {
			if (trader.isRunning()) {
				trader.cancelByPlayer();
			} else if (pickedTrade < 0) {
				ScreenMessages.show(client, Component.translatable("message.inventory_toolkit.quick_trade.pick_first"));
			} else {
				trader.start(merchantScreen, pickedTrade, config);
			}
		};

		Button button = config.showButton
			? CornerButtons.add(
				merchantScreen,
				0,
				"button.inventory_toolkit.quick_trade",
				"button.inventory_toolkit.quick_trade.tooltip",
				repeatPicked,
				() -> trader.isRunning() ? CornerButtons.State.CANCEL : null
			)
			: null;

		ScreenMouseEvents.allowMouseClick(merchantScreen).register((ignored, event) -> {
			if (trader.isRunning()) {
				// Clicks during a run would race the trader's own clicks, so
				// any click other than the cancel button stops the run.
				if (button != null && button.isMouseOver(event.x(), event.y())) {
					return true;
				}
				trader.cancelByPlayer();
				return false;
			}
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return true;
			}
			int offerIndex = offerIndexAt(merchantScreen, event.x(), event.y());
			if (offerIndex < 0) {
				return true;
			}
			pickedTrade = offerIndex;
			if (config.shiftClickTrades && event.hasShiftDown()) {
				trader.start(merchantScreen, offerIndex, config);
				return false;
			}
			return true;
		});
		ScreenKeyboardEvents.allowKeyPress(merchantScreen).register((ignored, event) -> {
			if (!quickTradeKey.matches(event)) {
				return true;
			}
			repeatPicked.run();
			return false;
		});
	}

	/**
	 * @return the index of the offer whose button is under the mouse, or -1
	 */
	private static int offerIndexAt(MerchantScreen screen, double mouseX, double mouseY) {
		AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
		int left = bounds.inventoryToolkit$leftPos() + TRADE_BUTTON_X;
		int top = bounds.inventoryToolkit$topPos() + TRADE_BUTTON_Y;
		if (mouseX < left || mouseX >= left + TRADE_BUTTON_WIDTH || mouseY < top) {
			return -1;
		}
		int row = (int) ((mouseY - top) / TRADE_BUTTON_HEIGHT);
		if (row >= TRADE_BUTTONS) {
			return -1;
		}
		int index = row + ((MerchantScreenAccessor) screen).inventoryToolkit$scrollOff();
		return index < screen.getMenu().getOffers().size() ? index : -1;
	}
}
