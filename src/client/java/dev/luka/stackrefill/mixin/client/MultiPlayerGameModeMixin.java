package dev.luka.stackrefill.mixin.client;

import dev.luka.stackrefill.StackRefillClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
		stackRefill$capture(player, hand);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void stackRefill$detectUse(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult blockHit,
		CallbackInfoReturnable<InteractionResult> callback
	) {
		stackRefill$detectUse(player, hand, callback.getReturnValue());
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void stackRefill$captureItemUse(
		Player player,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> callback
	) {
		stackRefill$capture(player, hand);
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void stackRefill$detectItemUse(
		Player player,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> callback
	) {
		stackRefill$detectUse(player, hand, callback.getReturnValue());
	}

	@Unique
	private void stackRefill$capture(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			stackRefill$clear();
			return;
		}

		ItemStack held = player.getMainHandItem();
		stackRefill$before = held.copyWithCount(1);
		stackRefill$beforeCount = held.getCount();
		stackRefill$hotbarSlot = player.getInventory().getSelectedSlot();
	}

	@Unique
	private void stackRefill$detectUse(Player player, InteractionHand hand, InteractionResult result) {
		try {
			if (hand != InteractionHand.MAIN_HAND
				|| stackRefill$before.isEmpty()
				|| stackRefill$hotbarSlot < 0
				|| !result.consumesAction()) {
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
			} else if (afterCount == stackRefill$beforeCount) {
				StackRefillClient.onServerConsumedUse(stackRefill$before, stackRefill$hotbarSlot, afterCount);
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
