package dev.luka.inventorytoolkit.quicktrade;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.ScreenMessages;
import dev.luka.inventorytoolkit.mixin.client.MerchantScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Repeats one villager trade the way a player would: select the trade (which
 * moves payment from the inventory into the trade slots), shift-click the
 * result (which trades for as long as those slots can pay), and again, until
 * the trade is out of stock, the payment runs out, or the inventory is full.
 * Any unexpected error stops the run instead of escaping into the game loop.
 */
public final class QuickTrader {
	private static final int PAYMENT1_SLOT = 0;
	private static final int PAYMENT2_SLOT = 1;
	private static final int RESULT_SLOT = 2;
	// Every round trades at least once or stops, so this is only a backstop.
	private static final int MAX_ROUNDS = 1000;
	// Rounds in a row that may trade nothing, for example while the server
	// catches up, before the run gives up.
	private static final int MAX_IDLE_ROUNDS = 3;

	private enum Step {
		SELECT,
		TAKE
	}

	private final Minecraft client;
	private MerchantScreen screen;
	private int offerIndex;
	// A copy of the trade being repeated, to find it again if the villager's
	// trade list is replaced, as it is when the villager levels up.
	private MerchantOffer trade;
	private Step step;
	private boolean running;
	private long tickCounter;
	private long nextActionTick;
	private int rounds;
	private int idleRounds;
	private int trades;

	public QuickTrader(Minecraft client) {
		this.client = client;
	}

	public boolean isRunning() {
		return running;
	}

	public void start(MerchantScreen screen, int offerIndex, QuickTradeConfig config) {
		try {
			tryStart(screen, offerIndex, config);
		} catch (RuntimeException exception) {
			fail(exception);
		}
	}

	private void tryStart(MerchantScreen screen, int offerIndex, QuickTradeConfig config) {
		LocalPlayer player = client.player;
		MerchantMenu menu = screen.getMenu();
		if (running
			|| !config.enabled
			|| player == null
			|| client.gameMode == null
			|| player.containerMenu != menu) {
			return;
		}
		if (!menu.getCarried().isEmpty()) {
			explain(Component.translatable("message.inventory_toolkit.cursor_not_empty"));
			return;
		}
		MerchantOffers offers = menu.getOffers();
		if (offerIndex < 0 || offerIndex >= offers.size()) {
			return;
		}
		if (offers.get(offerIndex).isOutOfStock()) {
			explain(Component.translatable("message.inventory_toolkit.quick_trade.out_of_stock_already"));
			return;
		}

		this.screen = screen;
		this.offerIndex = offerIndex;
		trade = offers.get(offerIndex).copy();
		step = Step.SELECT;
		running = true;
		rounds = 0;
		idleRounds = 0;
		trades = 0;
		nextActionTick = tickCounter;
	}

	/**
	 * Cancels a run the player asked to stop, and says so.
	 */
	public void cancelByPlayer() {
		if (running) {
			int done = trades;
			cancel();
			explain(Component.translatable("message.inventory_toolkit.quick_trade.cancelled", done));
		}
	}

	public void cancel() {
		running = false;
		screen = null;
		trade = null;
	}

	public void claimTick() {
		if (running) {
			InventoryActionCoordinator.claimManual(InventoryActionCoordinator.QUICK_TRADE);
		}
	}

	public void tick(QuickTradeConfig config) {
		tickCounter++;
		if (!running) {
			return;
		}

		LocalPlayer player = client.player;
		if (player == null
			|| client.gameMode == null
			|| !config.enabled
			|| client.gui.screen() != screen
			|| player.containerMenu != screen.getMenu()) {
			cancel();
			return;
		}
		claimTick();
		if (tickCounter < nextActionTick) {
			return;
		}
		try {
			act(player, config);
		} catch (RuntimeException exception) {
			fail(exception);
		}
	}

	private void act(LocalPlayer player, QuickTradeConfig config) {
		MerchantMenu menu = screen.getMenu();
		MerchantOffers offers = menu.getOffers();
		offerIndex = locate(offers);
		if (offerIndex < 0) {
			finish(config, Component.translatable("message.inventory_toolkit.quick_trade.trade_gone", trades));
			return;
		}
		MerchantOffer offer = offers.get(offerIndex);

		if (step == Step.SELECT) {
			if (offer.isOutOfStock()) {
				finish(config, Component.translatable("message.inventory_toolkit.quick_trade.out_of_stock", trades));
				return;
			}
			if (++rounds > MAX_ROUNDS) {
				finish(config, Component.translatable("message.inventory_toolkit.quick_trade.finished", trades));
				return;
			}
			MerchantScreenAccessor accessor = (MerchantScreenAccessor) screen;
			accessor.inventoryToolkit$setShopItem(offerIndex);
			accessor.inventoryToolkit$postButtonClick();
			step = Step.TAKE;
			nextActionTick = tickCounter + config.delayTicks;
			return;
		}

		Slot result = menu.getSlot(RESULT_SLOT);
		if (!result.hasItem()) {
			Component missing = missingPayment(menu, offer).getHoverName();
			finish(config, trades == 0
				? Component.translatable("message.inventory_toolkit.quick_trade.cannot_pay", missing)
				: Component.translatable("message.inventory_toolkit.quick_trade.out_of_payment", trades, missing));
			return;
		}
		// Payment from an earlier trade that did not fit back into the
		// inventory stays in the trade slots and may offer another trade.
		if (!ItemStack.isSameItemSameComponents(result.getItem(), offer.getResult())
			|| !offer.satisfiedBy(menu.getSlot(PAYMENT1_SLOT).getItem(), menu.getSlot(PAYMENT2_SLOT).getItem())) {
			finish(config, Component.translatable("message.inventory_toolkit.quick_trade.slots_blocked", trades));
			return;
		}

		int usesBefore = offer.getUses();
		client.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
		int traded = Math.max(0, offer.getUses() - usesBefore);
		trades += traded;
		if (result.hasItem() && traded == 0) {
			// The result could not go anywhere.
			finish(config, Component.translatable("message.inventory_toolkit.quick_trade.inventory_full", trades));
			return;
		}
		idleRounds = traded > 0 ? 0 : idleRounds + 1;
		if (idleRounds >= MAX_IDLE_ROUNDS) {
			finish(config, Component.translatable("message.inventory_toolkit.quick_trade.finished", trades));
			return;
		}
		step = Step.SELECT;
		nextActionTick = tickCounter + config.delayTicks;
	}

	/**
	 * @return the index of the trade being repeated, or -1 if the villager no longer offers it
	 */
	private int locate(MerchantOffers offers) {
		if (offerIndex < offers.size() && sameTrade(trade, offers.get(offerIndex))) {
			return offerIndex;
		}
		for (int index = 0; index < offers.size(); index++) {
			if (sameTrade(trade, offers.get(index))) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * Prices change with demand and discounts, so only the items are compared.
	 */
	private static boolean sameTrade(MerchantOffer a, MerchantOffer b) {
		return ItemStack.isSameItemSameComponents(a.getResult(), b.getResult())
			&& ItemStack.isSameItemSameComponents(a.getBaseCostA(), b.getBaseCostA())
			&& ItemStack.isSameItemSameComponents(a.getCostB(), b.getCostB());
	}

	/**
	 * The payment the player has too little of: the first cost unless it is
	 * covered by the trade slots and inventory together.
	 */
	private static ItemStack missingPayment(MerchantMenu menu, MerchantOffer offer) {
		ItemStack costA = offer.getCostA();
		if (count(menu, offer.getItemCostA()) < costA.getCount() || offer.getCostB().isEmpty()) {
			return costA;
		}
		return offer.getCostB();
	}

	private static int count(MerchantMenu menu, ItemCost cost) {
		int count = 0;
		for (Slot slot : menu.slots) {
			boolean payment = slot.index == PAYMENT1_SLOT || slot.index == PAYMENT2_SLOT;
			if ((payment || slot.container instanceof Inventory) && cost.test(slot.getItem())) {
				count += slot.getItem().getCount();
			}
		}
		return count;
	}

	private void finish(QuickTradeConfig config, Component message) {
		boolean nothingTraded = trades == 0;
		cancel();
		// Explain a run that traded nothing even when summaries are off.
		if (config.showMessages || nothingTraded) {
			explain(message);
		}
	}

	private void fail(RuntimeException exception) {
		InventoryToolkitClient.LOGGER.error("Quick Trade stopped after an error", exception);
		cancel();
		explain(Component.translatable("message.inventory_toolkit.quick_trade.error"));
	}

	private void explain(Component message) {
		// The action bar is hidden behind the trading screen.
		ScreenMessages.show(client, message);
	}
}
