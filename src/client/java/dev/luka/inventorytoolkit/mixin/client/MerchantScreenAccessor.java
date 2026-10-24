package dev.luka.inventorytoolkit.mixin.client;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
	@Accessor("shopItem")
	void inventoryToolkit$setShopItem(int shopItem);

	@Accessor("scrollOff")
	int inventoryToolkit$scrollOff();

	/**
	 * Selects the trade in {@code shopItem} exactly as clicking it does:
	 * payment items move into the trade slots and the server is told.
	 */
	@Invoker("postButtonClick")
	void inventoryToolkit$postButtonClick();
}
