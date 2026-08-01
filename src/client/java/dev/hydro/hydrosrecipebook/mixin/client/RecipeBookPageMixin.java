package dev.hydro.hydrosrecipebook.mixin.client;

import dev.hydro.hydrosrecipebook.FavoriteStore;
import dev.hydro.hydrosrecipebook.HydrosRecipeBookClient;
import dev.hydro.hydrosrecipebook.InstantCraftManager;
import dev.hydro.hydrosrecipebook.LocalRecipeCatalog;
import dev.hydro.hydrosrecipebook.LocalRecipePlacer;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeBookPage.class)
abstract class RecipeBookPageMixin {
	@Shadow
	@Final
	private List<RecipeButton> buttons;

	@Shadow
	@Final
	private OverlayRecipeComponent overlay;

	@Shadow
	private List<RecipeCollection> recipeCollections;

	@Shadow
	private boolean isFiltering;

	@Shadow
	private RecipeDisplayId lastClickedRecipe;

	@Shadow
	private RecipeCollection lastClickedRecipeCollection;

	@ModifyVariable(method = "updateCollections", at = @At("HEAD"), argsOnly = true)
	private List<RecipeCollection> hydrosRecipeBook$favoritesFirst(List<RecipeCollection> collections) {
		return HydrosRecipeBookClient.isEnabled()
			? FavoriteStore.favoritesFirst(collections)
			: collections;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void hydrosRecipeBook$handleExtraClicks(
		MouseButtonEvent event,
		int x,
		int y,
		int width,
		int height,
		boolean doubleClick,
		CallbackInfoReturnable<Boolean> callback
	) {
		if (!HydrosRecipeBookClient.isEnabled()) {
			return;
		}
		int button = event.button();
		if ((button != 1 && button != 2) || overlay.isVisible()) {
			return;
		}

		for (RecipeButton recipeButton : buttons) {
			if (!recipeButton.visible || !recipeButton.active
				|| !recipeButton.isMouseOver(event.x(), event.y())) {
				continue;
			}

			if (button == 2) {
				lastClickedRecipe = null;
				lastClickedRecipeCollection = null;
				FavoriteStore.toggle(recipeButton.getCollection());
				((RecipeBookPage) (Object) this).updateCollections(
					recipeCollections,
					true,
					isFiltering
				);
			} else {
				lastClickedRecipe = recipeButton.getCurrentRecipe();
				lastClickedRecipeCollection = recipeButton.getCollection();
				if (LocalRecipeCatalog.isLocal(lastClickedRecipe)) {
					LocalRecipePlacer.requestInstant(lastClickedRecipe);
				} else {
					InstantCraftManager.arm(
						net.minecraft.client.Minecraft.getInstance()
							.player.containerMenu.containerId,
						recipeButton.getDisplayStack()
					);
				}
			}
			callback.setReturnValue(true);
			return;
		}
	}
}
