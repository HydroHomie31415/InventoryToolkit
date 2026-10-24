package dev.luka.inventorytoolkit.mixin.client;

import dev.luka.inventorytoolkit.dropprotection.DropProtectionClient;
import dev.luka.inventorytoolkit.inventorysort.InventorySortClient;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void inventoryToolkit$extractLockIcon(
		GuiGraphicsExtractor graphics,
		Slot slot,
		int mouseX,
		int mouseY,
		CallbackInfo callbackInfo
	) {
		if (SlotLockClient.isLocked(slot)) {
			SlotLockClient.extractIcon(graphics, slot.x, slot.y);
		}
	}

	// Checked where vanilla checks its own hovered-slot keys, after text
	// fields and screens such as the anvil have had the key.
	@Inject(
		method = "keyPressed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;checkHotbarKeyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"
		),
		cancellable = true
	)
	private void inventoryToolkit$slotKeys(KeyEvent event, CallbackInfoReturnable<Boolean> callbackInfo) {
		if (SlotLockClient.onKeyPressed(event, hoveredSlot)
			|| InventorySortClient.onKeyPressed((AbstractContainerScreen<?>) (Object) this, event, hoveredSlot)) {
			callbackInfo.setReturnValue(true);
		}
	}

	@Inject(
		method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void inventoryToolkit$guardSlotClick(
		Slot slot,
		int slotId,
		int button,
		ContainerInput input,
		CallbackInfo callbackInfo
	) {
		if (input == ContainerInput.QUICK_MOVE && SlotLockClient.blocksQuickMove(slot)) {
			callbackInfo.cancel();
			return;
		}
		if (!DropProtectionClient.allowScreenClick(Minecraft.getInstance(), menu, slot, slotId, input)) {
			callbackInfo.cancel();
		}
	}
}
