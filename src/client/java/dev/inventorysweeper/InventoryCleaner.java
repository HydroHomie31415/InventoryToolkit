package dev.inventorysweeper;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.dropprotection.DropProtectionClient;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InventoryCleaner {
	private final Minecraft client;
	private SweeperConfig config;
	private final Map<StackKey, Integer> previousCounts = new HashMap<>();
	private final Map<StackKey, PendingDrop> queuedDrops = new LinkedHashMap<>();
	private LocalPlayer trackedPlayer;
	private long tickCounter;

	public InventoryCleaner(Minecraft client, SweeperConfig config) {
		this.client = client;
		this.config = config;
	}

	public void tick() {
		tickCounter++;
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			reset(null);
			return;
		}

		if (player != trackedPlayer) {
			reset(player);
			snapshot(player);
			return;
		}

		if (player.isSpectator()) {
			queuedDrops.clear();
			snapshot(player);
			return;
		}

		if (config.pauseWhileScreenOpen && client.gui.screen() != null) {
			snapshot(player);
			return;
		}

		if (!config.dropContainerTransfers && player.hasContainerOpen()) {
			snapshot(player);
			return;
		}

		if (!config.enabled) {
			snapshot(player);
			dropReady(player);
			return;
		}

		Map<StackKey, Integer> currentCounts = countInventory(player);
		for (Map.Entry<StackKey, Integer> entry : currentCounts.entrySet()) {
			int gained = entry.getValue() - previousCounts.getOrDefault(entry.getKey(), 0);
			if (gained > 0 && shouldDrop(entry.getKey().sample())) {
				queue(entry.getKey(), gained, tickCounter + config.dropDelayTicks);
			}
		}
		previousCounts.clear();
		previousCounts.putAll(currentCounts);

		dropReady(player);
	}

	public boolean shouldDrop(ItemStack stack) {
		if (DropProtectionClient.isProtected(stack)) {
			return false;
		}
		if (config.protectCustomNamedItems && stack.has(DataComponents.CUSTOM_NAME)) {
			return false;
		}
		if (config.protectEnchantedItems
			&& (stack.isEnchanted()
				|| !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty())) {
			return false;
		}
		if (config.protectDamagedItems && stack.isDamaged()) {
			return false;
		}

		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String id = itemId.toString();
		if (config.keptItems.contains(id)) {
			return false;
		}
		if (config.customItems.contains(id)) {
			return true;
		}
		for (PresetRegistry.Preset preset : PresetRegistry.all()) {
			if (config.isPresetEnabled(preset.id()) && preset.matches(stack)) {
				return true;
			}
		}
		return false;
	}

	public int queuePurge() {
		LocalPlayer player = client.player;
		if (player == null) {
			return 0;
		}

		int total = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && shouldDrop(stack)) {
				queue(new StackKey(stack), stack.getCount(), tickCounter);
				total += stack.getCount();
			}
		}
		return total;
	}

	public void setConfig(SweeperConfig config) {
		this.config = config;
		queuedDrops.clear();
		if (trackedPlayer != null) {
			snapshot(trackedPlayer);
		}
	}

	public void resnapshot() {
		queuedDrops.clear();
		if (client.player != null) {
			trackedPlayer = client.player;
			snapshot(client.player);
		}
	}

	private void dropReady(LocalPlayer player) {
		int dropped = 0;
		ItemStack lastDropped = ItemStack.EMPTY;
		for (int action = 0; action < config.maxActionsPerTick; action++) {
			DropResult result = dropNext(player);
			if (result == null) {
				break;
			}
			dropped += result.count();
			lastDropped = result.stack();
		}

		if (dropped > 0 && config.notifications && config.notifyOnDrop) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
				"message.inventory_sweeper.dropped",
				dropped,
				lastDropped.getHoverName()
			));
		}
	}

	private DropResult dropNext(LocalPlayer player) {
		Iterator<Map.Entry<StackKey, PendingDrop>> iterator = queuedDrops.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<StackKey, PendingDrop> entry = iterator.next();
			PendingDrop pending = entry.getValue();
			if (pending.count() <= 0) {
				iterator.remove();
				continue;
			}
			if (pending.readyAtTick() > tickCounter) {
				continue;
			}

			AbstractContainerMenu menu = player.containerMenu;
			Slot slot = findPlayerInventorySlot(menu, player.getInventory(), entry.getKey());
			if (slot == null) {
				iterator.remove();
				continue;
			}

			int stackCount = slot.getItem().getCount();
			boolean dropWholeStack = pending.count() >= stackCount;
			ItemStack droppedStack = slot.getItem().copyWithCount(dropWholeStack ? stackCount : 1);
			if (!InventoryActionCoordinator.acquire(InventoryActionCoordinator.SWEEPER)) {
				return null;
			}
			client.gameMode.handleContainerInput(
				menu.containerId,
				slot.index,
				dropWholeStack ? 1 : 0,
				ContainerInput.THROW,
				player
			);
			DroppedItemVisuals.expectDrop(droppedStack);

			int removed = dropWholeStack ? stackCount : 1;
			int remaining = pending.count() - removed;
			if (remaining <= 0) {
				iterator.remove();
			} else {
				entry.setValue(new PendingDrop(remaining, pending.readyAtTick()));
			}
			return new DropResult(droppedStack, removed);
		}
		return null;
	}

	private static Slot findPlayerInventorySlot(AbstractContainerMenu menu, Inventory inventory, StackKey key) {
		for (Slot slot : menu.slots) {
			if (slot.container == inventory
				&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE
				&& !SlotLockClient.isLocked(slot.getContainerSlot())
				&& slot.hasItem()
				&& key.matches(slot.getItem())) {
				return slot;
			}
		}
		return null;
	}

	private void reset(LocalPlayer player) {
		trackedPlayer = player;
		previousCounts.clear();
		queuedDrops.clear();
	}

	private void snapshot(LocalPlayer player) {
		previousCounts.clear();
		previousCounts.putAll(countInventory(player));
	}

	private void queue(StackKey key, int count, long readyAtTick) {
		queuedDrops.compute(key, (ignored, pending) -> pending == null
			? new PendingDrop(count, readyAtTick)
			: new PendingDrop(pending.count() + count, Math.min(pending.readyAtTick(), readyAtTick)));
	}

	private static Map<StackKey, Integer> countInventory(LocalPlayer player) {
		Map<StackKey, Integer> counts = new HashMap<>();
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty()) {
				counts.merge(new StackKey(stack), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static final class StackKey {
		private final ItemStack sample;
		private final int hash;

		private StackKey(ItemStack stack) {
			this.sample = stack.copyWithCount(1);
			this.hash = ItemStack.hashItemAndComponents(this.sample);
		}

		private ItemStack sample() {
			return sample;
		}

		private boolean matches(ItemStack stack) {
			return ItemStack.isSameItemSameComponents(sample, stack);
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof StackKey other && matches(other.sample);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	private record PendingDrop(int count, long readyAtTick) {
	}

	private record DropResult(ItemStack stack, int count) {
	}
}
