package dev.luka.stackrefill;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class StackRefillManager {
	private final Minecraft client;
	private final Map<Integer, PendingRefill> pendingByHotbarSlot = new HashMap<>();
	private StackRefillConfig config;
	private LocalPlayer trackedPlayer;
	private long tickCounter;
	private long nextActionTick;

	public StackRefillManager(Minecraft client, StackRefillConfig config) {
		this.client = client;
		this.config = config;
	}

	public void onStackUsed(ItemStack before, int hotbarSlot, int beforeCount, int afterCount) {
		LocalPlayer player = client.player;
		if (player == null
			|| !config.enabled
			|| before.isEmpty()
			|| beforeCount <= afterCount
			|| afterCount > config.refillAtCount
			|| !config.isHotbarSlotEnabled(hotbarSlot)
			|| config.onlyBlockItems && !(before.getItem() instanceof BlockItem)
			|| !config.allowWhileSneaking && player.isShiftKeyDown()
			|| !passesItemRules(before)) {
			return;
		}

		PendingRefill refill = new PendingRefill(
			before.copyWithCount(1),
			hotbarSlot,
			tickCounter + config.refillDelayTicks
		);
		pendingByHotbarSlot.merge(hotbarSlot, refill, (oldValue, newValue) ->
			oldValue.matches(newValue.sample()) && oldValue.readyAtTick() <= newValue.readyAtTick()
				? oldValue
				: newValue
		);
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
		if (!config.enabled || player.isSpectator()) {
			pendingByHotbarSlot.clear();
			return;
		}
		if (tickCounter < nextActionTick || config.pauseWhileScreenOpen && client.gui.screen() != null) {
			return;
		}

		PendingRefill next = pendingByHotbarSlot.values().stream()
			.filter(refill -> refill.readyAtTick() <= tickCounter)
			.min((left, right) -> Long.compare(left.readyAtTick(), right.readyAtTick()))
			.orElse(null);
		if (next == null) {
			return;
		}
		if (!InventoryActionCoordinator.isAvailableFor(InventoryActionCoordinator.STACK_REFILL)) {
			return;
		}

		pendingByHotbarSlot.remove(next.hotbarSlot());
		perform(player, next);
	}

	public void setConfig(StackRefillConfig config) {
		this.config = config;
		clearPending();
	}

	public void clearPending() {
		pendingByHotbarSlot.clear();
	}

	private void perform(LocalPlayer player, PendingRefill refill) {
		Inventory inventory = player.getInventory();
		if (config.requireOriginalSlotSelected && inventory.getSelectedSlot() != refill.hotbarSlot()) {
			return;
		}
		if (!config.isHotbarSlotEnabled(refill.hotbarSlot())) {
			return;
		}
		if (player.containerMenu != player.inventoryMenu || player.inventoryMenu.getCarried().isEmpty() == false) {
			return;
		}

		ItemStack target = inventory.getItem(refill.hotbarSlot());
		if (!target.isEmpty() && !matchesForReplacement(refill.sample(), target)) {
			return;
		}
		if (target.getCount() > config.refillAtCount) {
			return;
		}

		int sourceIndex = findSource(inventory, refill);
		if (sourceIndex < 0) {
			if (config.notifications && config.notifyWhenNoMatch) {
				player.sendOverlayMessage(Component.translatable(
					"message.stack_refill.no_match",
					refill.sample().getHoverName()
				));
			}
			return;
		}

		AbstractContainerMenu menu = player.inventoryMenu;
		Slot sourceSlot = findMenuSlot(menu, inventory, sourceIndex);
		Slot targetSlot = findMenuSlot(menu, inventory, refill.hotbarSlot());
		if (sourceSlot == null || targetSlot == null) {
			return;
		}

		boolean changed = config.refillMode == RefillMode.TOP_UP
			? topUp(player, menu, sourceSlot, targetSlot)
			: replace(player, menu, sourceSlot, refill.hotbarSlot());
		if (!changed) {
			return;
		}

		nextActionTick = tickCounter + config.cooldownTicks;
		ItemStack refilled = inventory.getItem(refill.hotbarSlot());
		if (config.notifications) {
			player.sendOverlayMessage(Component.translatable(
				"message.stack_refill.refilled",
				refilled.getHoverName(),
				refilled.getCount()
			));
		}
	}

	private boolean replace(LocalPlayer player, AbstractContainerMenu menu, Slot sourceSlot, int hotbarSlot) {
		if (!InventoryActionCoordinator.acquire(InventoryActionCoordinator.STACK_REFILL)) {
			return false;
		}
		client.gameMode.handleContainerInput(
			menu.containerId,
			sourceSlot.index,
			hotbarSlot,
			ContainerInput.SWAP,
			player
		);
		return true;
	}

	private boolean topUp(LocalPlayer player, AbstractContainerMenu menu, Slot sourceSlot, Slot targetSlot) {
		ItemStack source = sourceSlot.getItem();
		ItemStack target = targetSlot.getItem();
		if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(source, target)) {
			return false;
		}
		if (!InventoryActionCoordinator.acquire(InventoryActionCoordinator.STACK_REFILL)) {
			return false;
		}

		int beforeCount = target.getCount();
		client.gameMode.handleContainerInput(menu.containerId, sourceSlot.index, 0, ContainerInput.PICKUP, player);
		client.gameMode.handleContainerInput(menu.containerId, targetSlot.index, 0, ContainerInput.PICKUP, player);
		if (!menu.getCarried().isEmpty()) {
			client.gameMode.handleContainerInput(menu.containerId, sourceSlot.index, 0, ContainerInput.PICKUP, player);
		}
		return targetSlot.getItem().getCount() > beforeCount;
	}

	private int findSource(Inventory inventory, PendingRefill refill) {
		int bestIndex = -1;
		int bestCount = config.sourceStrategy == SourceStrategy.SMALLEST_STACK ? Integer.MAX_VALUE : Integer.MIN_VALUE;

		for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
			if (index == refill.hotbarSlot()) {
				continue;
			}
			boolean hotbar = Inventory.isHotbarSlot(index);
			if (hotbar && !config.searchOtherHotbarSlots || !hotbar && !config.searchMainInventory) {
				continue;
			}

			ItemStack candidate = inventory.getItem(index);
			if (candidate.isEmpty()
				|| candidate.getCount() < config.minimumSourceCount
				|| !matchesForReplacement(refill.sample(), candidate)) {
				continue;
			}

			switch (config.sourceStrategy) {
				case FIRST_SLOT -> {
					return index;
				}
				case LAST_SLOT -> bestIndex = index;
				case LARGEST_STACK -> {
					if (candidate.getCount() > bestCount) {
						bestCount = candidate.getCount();
						bestIndex = index;
					}
				}
				case SMALLEST_STACK -> {
					if (candidate.getCount() < bestCount) {
						bestCount = candidate.getCount();
						bestIndex = index;
					}
				}
			}
		}
		return bestIndex;
	}

	private boolean matchesForReplacement(ItemStack expected, ItemStack candidate) {
		if (config.refillMode == RefillMode.TOP_UP || config.matchMode == MatchMode.EXACT_COMPONENTS) {
			return ItemStack.isSameItemSameComponents(expected, candidate);
		}
		return expected.is(candidate.getItem());
	}

	private boolean passesItemRules(ItemStack stack) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		boolean listed = config.itemRules.contains(itemId.toString());
		return config.listMode == ListMode.WHITELIST ? listed : !listed;
	}

	private static Slot findMenuSlot(AbstractContainerMenu menu, Inventory inventory, int inventoryIndex) {
		for (Slot slot : menu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() == inventoryIndex) {
				return slot;
			}
		}
		return null;
	}

	private void reset(LocalPlayer player) {
		trackedPlayer = player;
		pendingByHotbarSlot.clear();
		nextActionTick = 0;
	}

	private record PendingRefill(ItemStack sample, int hotbarSlot, long readyAtTick) {
		private boolean matches(ItemStack stack) {
			return ItemStack.isSameItemSameComponents(sample, stack);
		}
	}
}
