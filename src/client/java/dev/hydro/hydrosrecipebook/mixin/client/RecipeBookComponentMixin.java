package dev.hydro.hydrosrecipebook.mixin.client;

import dev.hydro.hydrosrecipebook.LocalRecipe;
import dev.hydro.hydrosrecipebook.LocalRecipeCatalog;
import dev.hydro.hydrosrecipebook.LocalRecipePlacer;
import dev.hydro.hydrosrecipebook.HydrosRecipeBookClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
abstract class RecipeBookComponentMixin {
	@Shadow
	protected Minecraft minecraft;

	@Inject(method = "tryPlaceRecipe", at = @At("HEAD"), cancellable = true)
	private void hydrosRecipeBook$placeLocalRecipe(
		RecipeCollection collection,
		RecipeDisplayId recipeId,
		boolean useMaxItems,
		CallbackInfoReturnable<Boolean> callback
	) {
		if (!HydrosRecipeBookClient.isEnabled()) {
			return;
		}
		LocalRecipe localRecipe = LocalRecipeCatalog.get(recipeId);
		if (localRecipe != null) {
			callback.setReturnValue(LocalRecipePlacer.start(
				minecraft,
				localRecipe,
				useMaxItems
			));
		}
	}
}
