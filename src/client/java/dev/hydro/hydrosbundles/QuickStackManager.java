package dev.hydro.hydrosbundles;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

public final class QuickStackManager {
	private final Minecraft client;
	private final Deque<Integer> pendingSources = new ArrayDeque<>();
	private HydrosBundlesConfig config;
	private BundleRouter router;
	private LocalPlayer trackedPlayer;
	private long tickCounter;
	private long nextActionTick;
	private int successfulTransfers;
	private int skippedSources;
	private int startingItemCount;

	public QuickStackManager(Minecraft client, HydrosBundlesConfig config) {
		this.client = client;
		setConfig(config);
	}

	public void setConfig(HydrosBundlesConfig config) {
		this.config = config;
		this.router = new BundleRouter(config);
		cancel();
	}

	public void start() {
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || !config.enabled) {
			return;
		}
		if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
			notifyPlayer(Component.translatable("message.hydros_bundles.inventory_busy"));
			return;
		}

		pendingSources.clear();
		trackedPlayer = player;
		int firstIndex = config.includeHotbar ? 0 : Inventory.getSelectionSize();
		for (int index = firstIndex; index < BundleInventory.PLAYER_STORAGE_SIZE; index++) {
			ItemStack stack = player.getInventory().getItem(index);
			if (!stack.isEmpty() && !BundleInventory.isBundle(stack) && !SlotLockClient.isLocked(index)) {
				pendingSources.addLast(index);
			}
		}
		successfulTransfers = 0;
		skippedSources = 0;
		startingItemCount = pendingSources.size();
		nextActionTick = tickCounter;
		if (pendingSources.isEmpty()) {
			notifyPlayer(Component.translatable("message.hydros_bundles.nothing_to_stack"));
		} else {
			notifyPlayer(Component.translatable("message.hydros_bundles.started", pendingSources.size()));
		}
	}

	public void tick() {
		tickCounter++;
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			reset(null);
			return;
		}
		if (trackedPlayer != player) {
			reset(player);
			return;
		}
		if (pendingSources.isEmpty() || tickCounter < nextActionTick || client.gui.screen() != null) {
			return;
		}
		if (!config.enabled || player.isSpectator()
			|| player.containerMenu != player.inventoryMenu
			|| !player.inventoryMenu.getCarried().isEmpty()) {
			cancel();
			return;
		}
		if (successfulTransfers >= config.maxTransfersPerRun) {
			finish();
			return;
		}
		if (!InventoryActionCoordinator.isAvailableFor(InventoryActionCoordinator.HYDROS_BUNDLES)) {
			return;
		}

		int sourceIndex = pendingSources.removeFirst();
		ItemStack source = player.getInventory().getItem(sourceIndex);
		if (source.isEmpty() || BundleInventory.isBundle(source)) {
			checkFinished();
			return;
		}

		int bundleIndex = router.findBundle(player.getInventory(), source, sourceIndex);
		if (bundleIndex < 0) {
			skippedSources++;
			checkFinished();
			return;
		}

		int beforeCount = source.getCount();
		if (insert(player, sourceIndex, bundleIndex)) {
			successfulTransfers++;
			ItemStack remaining = player.getInventory().getItem(sourceIndex);
			if (!remaining.isEmpty() && remaining.getCount() < beforeCount
				&& router.findBundle(player.getInventory(), remaining, sourceIndex) >= 0) {
				pendingSources.addLast(sourceIndex);
			}
		} else {
			skippedSources++;
		}
		nextActionTick = tickCounter + config.actionDelayTicks;
		checkFinished();
	}

	public boolean isRunning() {
		return !pendingSources.isEmpty();
	}

	public void cancel() {
		pendingSources.clear();
		successfulTransfers = 0;
		skippedSources = 0;
		startingItemCount = 0;
	}

	private boolean insert(LocalPlayer player, int sourceIndex, int bundleIndex) {
		Inventory inventory = player.getInventory();
		AbstractContainerMenu menu = player.inventoryMenu;
		Slot sourceSlot = BundleInventory.menuSlot(menu, inventory, sourceIndex);
		Slot bundleSlot = BundleInventory.menuSlot(menu, inventory, bundleIndex);
		if (sourceSlot == null || bundleSlot == null || !BundleInventory.canAccept(bundleSlot.getItem(), sourceSlot.getItem())) {
			return false;
		}
		if (!InventoryActionCoordinator.acquire(InventoryActionCoordinator.HYDROS_BUNDLES)) {
			return false;
		}

		int beforeCount = sourceSlot.getItem().getCount();
		client.gameMode.handleContainerInput(menu.containerId, sourceSlot.index, 0, ContainerInput.PICKUP, player);
		client.gameMode.handleContainerInput(menu.containerId, bundleSlot.index, 0, ContainerInput.PICKUP, player);
		if (!menu.getCarried().isEmpty()) {
			client.gameMode.handleContainerInput(menu.containerId, sourceSlot.index, 0, ContainerInput.PICKUP, player);
		}
		return menu.getCarried().isEmpty() && sourceSlot.getItem().getCount() < beforeCount;
	}

	private void checkFinished() {
		if (pendingSources.isEmpty()) {
			finish();
		}
	}

	private void finish() {
		if (startingItemCount > 0) {
			if (config.notifySkippedItems) {
				notifyPlayer(Component.translatable(
					"message.hydros_bundles.finished_with_skips",
					successfulTransfers,
					skippedSources
				));
			} else {
				notifyPlayer(Component.translatable("message.hydros_bundles.finished", successfulTransfers));
			}
		}
		cancel();
	}

	private void notifyPlayer(Component message) {
		if (config.notifications && client.player != null) {
			client.player.sendOverlayMessage(message);
		}
	}

	private void reset(LocalPlayer player) {
		trackedPlayer = player;
		cancel();
		nextActionTick = 0;
	}
}
