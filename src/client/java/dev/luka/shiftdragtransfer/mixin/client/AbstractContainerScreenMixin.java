package dev.luka.shiftdragtransfer.mixin.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import dev.luka.shiftdragtransfer.ShiftDragTransferClient;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.DestinationPriority;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig.SlotSearchOrder;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Shadow
	protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

	@Invoker("getHoveredSlot")
	protected abstract Slot shiftDragTransfer$getHoveredSlot(double mouseX, double mouseY);

	@Unique
	private final Set<Slot> shiftDragTransfer$visitedSlots =
		Collections.newSetFromMap(new IdentityHashMap<>());

	@Unique
	private boolean shiftDragTransfer$active;

	@Unique
	private Slot shiftDragTransfer$lastDragSlot;

	@Unique
	private long shiftDragTransfer$lastScrollTransferNanos;

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void shiftDragTransfer$startDrag(
			MouseButtonEvent event,
			boolean doubleClick,
			CallbackInfoReturnable<Boolean> callbackInfo
	) {
		shiftDragTransfer$reset();
		ShiftDragTransferConfig config = ShiftDragTransferClient.config();

		if (!config.enabled
				|| !config.dragEnabled
				|| event.button() != config.dragMouseButton.button()
				|| !config.dragModifier.matches(event)
				|| !menu.getCarried().isEmpty()) {
			return;
		}

		Slot slot = shiftDragTransfer$getHoveredSlot(event.x(), event.y());
		if (slot == null
				|| !shiftDragTransfer$isAllowedDragSource(slot, config)
				|| (!config.allowDragStartOnEmptySlot && !slot.hasItem())) {
			return;
		}

		InventoryActionCoordinator.claimManual(
			InventoryActionCoordinator.SHIFT_DRAG_TRANSFER
		);
		shiftDragTransfer$active = true;
		shiftDragTransfer$lastDragSlot = slot;
		shiftDragTransfer$visitedSlots.add(slot);

		if (slot.hasItem()) {
			slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
		}

		callbackInfo.setReturnValue(true);
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void shiftDragTransfer$quickMoveHoveredSlot(
			MouseButtonEvent event,
			double dragX,
			double dragY,
			CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if (!shiftDragTransfer$active) {
			return;
		}

		ShiftDragTransferConfig config = ShiftDragTransferClient.config();
		if (!config.enabled
				|| !config.dragEnabled
				|| event.button() != config.dragMouseButton.button()
				|| !config.dragModifier.isPressed(net.minecraft.client.Minecraft.getInstance())
				|| !menu.getCarried().isEmpty()) {
			shiftDragTransfer$reset();
			return;
		}

		Slot slot = shiftDragTransfer$getHoveredSlot(event.x(), event.y());
		if (slot == shiftDragTransfer$lastDragSlot) {
			callbackInfo.setReturnValue(true);
			return;
		}
		shiftDragTransfer$lastDragSlot = slot;

		if (slot != null
				&& slot.hasItem()
				&& shiftDragTransfer$isAllowedDragSource(slot, config)
				&& (config.allowDragSlotRevisit
					|| shiftDragTransfer$visitedSlots.add(slot))) {
			InventoryActionCoordinator.claimManual(
				InventoryActionCoordinator.SHIFT_DRAG_TRANSFER
			);
			slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
		}

		callbackInfo.setReturnValue(true);
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void shiftDragTransfer$stopDrag(
			MouseButtonEvent event,
			CallbackInfoReturnable<Boolean> callbackInfo
	) {
		boolean wasActive = shiftDragTransfer$active;
		shiftDragTransfer$reset();
		if (wasActive) {
			callbackInfo.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("RETURN"), cancellable = true)
	private void shiftDragTransfer$scrollItems(
			double mouseX,
			double mouseY,
			double horizontalAmount,
			double verticalAmount,
			CallbackInfoReturnable<Boolean> callbackInfo
	) {
		ShiftDragTransferConfig config = ShiftDragTransferClient.config();
		if (Boolean.TRUE.equals(callbackInfo.getReturnValue())
				|| !config.enabled
				|| !config.scrollEnabled
				|| verticalAmount == 0.0
				|| !config.scrollModifier.isPressed(net.minecraft.client.Minecraft.getInstance())
				|| !menu.getCarried().isEmpty()
				|| shiftDragTransfer$isScrollCoolingDown(config)) {
			return;
		}

		Slot source = shiftDragTransfer$getHoveredSlot(mouseX, mouseY);
		if (source == null || !source.hasItem()) {
			return;
		}

		boolean movesTowardPlayer = config.scrollDirection.movesTowardPlayer(verticalAmount);
		boolean playerDestination;

		if (movesTowardPlayer
				&& config.scrollContainerToPlayer
				&& shiftDragTransfer$isContainerStorageSlot(source)) {
			playerDestination = true;
		} else if (!movesTowardPlayer
				&& config.scrollPlayerToContainer
				&& shiftDragTransfer$isConfiguredPlayerInventorySlot(source, config)) {
			playerDestination = false;
		} else {
			return;
		}

		int transferred = 0;
		for (int item = 0; item < config.itemsPerScroll && source.hasItem(); item++) {
			ItemStack sourceStack = source.getItem().copy();
			Slot destination =
				shiftDragTransfer$findDestination(sourceStack, playerDestination, config);
			if (destination == null) {
				break;
			}

			InventoryActionCoordinator.claimManual(
				InventoryActionCoordinator.SHIFT_DRAG_TRANSFER
			);
			if (!shiftDragTransfer$transferOne(source, destination, sourceStack)) {
				break;
			}
			transferred++;
		}

		if (transferred > 0) {
			shiftDragTransfer$lastScrollTransferNanos = System.nanoTime();
			callbackInfo.setReturnValue(true);
		}
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void shiftDragTransfer$onScreenRemoved(CallbackInfo callbackInfo) {
		shiftDragTransfer$reset();
	}

	@Unique
	private boolean shiftDragTransfer$isAllowedDragSource(
			Slot slot,
			ShiftDragTransferConfig config
	) {
		if (!slot.isActive() || slot.isFake()) {
			return false;
		}

		if (slot.container instanceof Inventory) {
			return config.dragFromPlayerInventory
				&& shiftDragTransfer$isConfiguredPlayerInventorySlot(slot, config);
		}

		return config.dragFromContainer;
	}

	@Unique
	private boolean shiftDragTransfer$isContainerStorageSlot(Slot slot) {
		if (slot == null
				|| slot.container instanceof Inventory
				|| !slot.isActive()
				|| slot.isFake()
				|| !slot.hasItem()) {
			return false;
		}

		return slot.mayPlace(slot.getItem());
	}

	@Unique
	private boolean shiftDragTransfer$isPlayerInventorySlot(Slot slot) {
		return slot != null
			&& slot.container instanceof Inventory
			&& slot.getContainerSlot() >= 0
			&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE
			&& slot.isActive()
			&& !slot.isFake();
	}

	@Unique
	private boolean shiftDragTransfer$isConfiguredPlayerInventorySlot(
			Slot slot,
			ShiftDragTransferConfig config
	) {
		if (!shiftDragTransfer$isPlayerInventorySlot(slot) || SlotLockClient.isLocked(slot)) {
			return false;
		}

		int inventorySlot = slot.getContainerSlot();
		return inventorySlot < Inventory.SELECTION_SIZE
			? config.useHotbar
			: config.useMainInventory;
	}

	@Unique
	private Slot shiftDragTransfer$findDestination(
			ItemStack stack,
			boolean playerDestination,
			ShiftDragTransferConfig config
	) {
		Slot matchingSlot = null;
		Slot emptySlot = null;
		int slotCount = menu.slots.size();

		for (int offset = 0; offset < slotCount; offset++) {
			int index = config.slotSearchOrder == SlotSearchOrder.FIRST_TO_LAST
				? offset
				: slotCount - 1 - offset;
			Slot slot = menu.slots.get(index);

			boolean validDestination = playerDestination
				? shiftDragTransfer$isConfiguredPlayerInventorySlot(slot, config)
				: !(slot.container instanceof Inventory) && slot.isActive() && !slot.isFake();
			if (!validDestination || !slot.mayPlace(stack)) {
				continue;
			}

			ItemStack destinationStack = slot.getItem();
			if (destinationStack.isEmpty()) {
				if (emptySlot == null) {
					emptySlot = slot;
				}
				continue;
			}

			int maximum = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
			if (ItemStack.isSameItemSameComponents(stack, destinationStack)
					&& destinationStack.getCount() < maximum
					&& matchingSlot == null) {
				matchingSlot = slot;
			}
		}

		return config.destinationPriority == DestinationPriority.MATCHING_STACKS
			? matchingSlot != null ? matchingSlot : emptySlot
			: emptySlot != null ? emptySlot : matchingSlot;
	}

	@Unique
	private boolean shiftDragTransfer$transferOne(
			Slot source,
			Slot destination,
			ItemStack expectedStack
	) {
		slotClicked(source, source.index, 0, ContainerInput.PICKUP);

		ItemStack carried = menu.getCarried();
		if (carried.isEmpty() || !ItemStack.isSameItemSameComponents(expectedStack, carried)) {
			return false;
		}

		int countBeforeDeposit = carried.getCount();
		slotClicked(destination, destination.index, 1, ContainerInput.PICKUP);
		int countAfterDeposit = menu.getCarried().getCount();
		boolean transferred = countAfterDeposit == countBeforeDeposit - 1;

		if (!menu.getCarried().isEmpty()) {
			slotClicked(source, source.index, 0, ContainerInput.PICKUP);
		}

		return transferred && menu.getCarried().isEmpty();
	}

	@Unique
	private boolean shiftDragTransfer$isScrollCoolingDown(ShiftDragTransferConfig config) {
		if (config.scrollCooldownMilliseconds == 0
				|| shiftDragTransfer$lastScrollTransferNanos == 0L) {
			return false;
		}

		long elapsedNanos = System.nanoTime() - shiftDragTransfer$lastScrollTransferNanos;
		return elapsedNanos < config.scrollCooldownMilliseconds * 1_000_000L;
	}

	@Unique
	private void shiftDragTransfer$reset() {
		shiftDragTransfer$active = false;
		shiftDragTransfer$lastDragSlot = null;
		shiftDragTransfer$visitedSlots.clear();
	}
}
