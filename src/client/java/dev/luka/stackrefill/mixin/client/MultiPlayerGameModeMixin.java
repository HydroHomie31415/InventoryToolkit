package dev.luka.stackrefill.mixin.client;

import dev.luka.stackrefill.StackRefillClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Unique
	private ItemStack stackRefill$before = ItemStack.EMPTY;
	@Unique
	private int stackRefill$beforeCount;
	@Unique
	private int stackRefill$hotbarSlot = -1;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void stackRefill$capture(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult blockHit,
		CallbackInfoReturnable<InteractionResult> callback
	) {
		if (hand != InteractionHand.MAIN_HAND) {
			stackRefill$clear();
			return;
		}

		ItemStack held = player.getMainHandItem();
		stackRefill$before = held.copyWithCount(1);
		stackRefill$beforeCount = held.getCount();
		stackRefill$hotbarSlot = player.getInventory().getSelectedSlot();
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void stackRefill$detectUse(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult blockHit,
		CallbackInfoReturnable<InteractionResult> callback
	) {
		try {
			if (hand != InteractionHand.MAIN_HAND
				|| stackRefill$before.isEmpty()
				|| stackRefill$hotbarSlot < 0
				|| !callback.getReturnValue().consumesAction()) {
				return;
			}

			ItemStack after = player.getInventory().getItem(stackRefill$hotbarSlot);
			int afterCount;
			if (after.isEmpty()) {
				afterCount = 0;
			} else if (ItemStack.isSameItemSameComponents(stackRefill$before, after)) {
				afterCount = after.getCount();
			} else {
				return;
			}

			if (afterCount < stackRefill$beforeCount) {
				StackRefillClient.onStackUsed(
					stackRefill$before,
					stackRefill$hotbarSlot,
					stackRefill$beforeCount,
					afterCount
				);
			}
		} finally {
			stackRefill$clear();
		}
	}

	@Unique
	private void stackRefill$clear() {
		stackRefill$before = ItemStack.EMPTY;
		stackRefill$beforeCount = 0;
		stackRefill$hotbarSlot = -1;
	}
}
