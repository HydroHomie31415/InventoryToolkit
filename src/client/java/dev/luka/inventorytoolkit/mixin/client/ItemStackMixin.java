package dev.luka.inventorytoolkit.mixin.client;

import dev.luka.inventorytoolkit.shulkerpreview.ShulkerPreviewClient;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void inventoryToolkit$containerPreview(CallbackInfoReturnable<Optional<TooltipComponent>> callbackInfo) {
		TooltipComponent preview = ShulkerPreviewClient.previewFor((ItemStack) (Object) this);
		if (preview != null) {
			callbackInfo.setReturnValue(Optional.of(preview));
		}
	}
}
