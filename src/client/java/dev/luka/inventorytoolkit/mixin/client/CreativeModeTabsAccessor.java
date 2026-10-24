package dev.luka.inventorytoolkit.mixin.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
	@Accessor("CACHED_PARAMETERS")
	static void inventoryToolkit$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
		throw new AssertionError();
	}
}
