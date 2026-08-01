package dev.hydro.hydrosrecipebook.mixin.client;

import dev.hydro.hydrosrecipebook.LocalRecipeCatalog;
import dev.hydro.hydrosrecipebook.HydrosRecipeBookClient;
import dev.hydro.hydrosrecipebook.RecipeBookEnhancements;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ClientRecipeBook.class)
abstract class ClientRecipeBookMixin {
	@Shadow
	@Final
	private Map<RecipeDisplayId, RecipeDisplayEntry> known;

	@ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true)
	private RecipeDisplayEntry hydrosRecipeBook$removeGroup(RecipeDisplayEntry entry) {
		return RecipeBookEnhancements.processEntry(
			(ClientRecipeBook) (Object) this,
			entry
		);
	}

	@Inject(method = "add", at = @At("HEAD"))
	private void hydrosRecipeBook$preferServerEntry(
		RecipeDisplayEntry entry,
		CallbackInfo callback
	) {
		if (!HydrosRecipeBookClient.isEnabled()) {
			return;
		}
		if (entry.id().index() < 0 || LocalRecipeCatalog.isLocal(entry.id())) {
			return;
		}
		known.entrySet().removeIf(knownEntry ->
			knownEntry.getKey().index() < 0
				&& knownEntry.getValue().display().equals(entry.display())
		);
	}
}
