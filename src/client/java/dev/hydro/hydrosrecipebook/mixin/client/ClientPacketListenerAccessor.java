package dev.hydro.hydrosrecipebook.mixin.client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
	@Invoker("refreshRecipeBook")
	void hydrosRecipeBook$refreshRecipeBook(ClientRecipeBook book);
}
