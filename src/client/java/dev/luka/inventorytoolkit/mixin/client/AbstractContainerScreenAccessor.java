package dev.luka.inventorytoolkit.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int inventoryToolkit$leftPos();

	@Accessor("topPos")
	int inventoryToolkit$topPos();

	@Accessor("imageWidth")
	int inventoryToolkit$imageWidth();

	@Accessor("imageHeight")
	int inventoryToolkit$imageHeight();

	@Accessor("hoveredSlot")
	Slot inventoryToolkit$hoveredSlot();
}
