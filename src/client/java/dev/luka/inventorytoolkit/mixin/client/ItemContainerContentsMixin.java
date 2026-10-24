package dev.luka.inventorytoolkit.mixin.client;

import dev.luka.inventorytoolkit.shulkerpreview.ShulkerPreviewClient;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin {
	// The shulker box preview grid shows the stored items instead.
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private void inventoryToolkit$hideItemList(
		Item.TooltipContext context,
		Consumer<Component> consumer,
		TooltipFlag flag,
		DataComponentGetter components,
		CallbackInfo callbackInfo
	) {
		if (ShulkerPreviewClient.hidesItemList()) {
			callbackInfo.cancel();
		}
	}
}
