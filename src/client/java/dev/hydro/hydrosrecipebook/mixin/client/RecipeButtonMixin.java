package dev.hydro.hydrosrecipebook.mixin.client;

import dev.hydro.hydrosrecipebook.FavoriteStore;
import dev.hydro.hydrosrecipebook.HydrosRecipeBookClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
abstract class RecipeButtonMixin {
	private static final Component HYDROS_RECIPE_BOOK$FAVORITE_BADGE =
		Component.literal("★");

	@Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
	private void hydrosRecipeBook$renderFavoriteBadge(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		float partialTick,
		CallbackInfo callback
	) {
		if (!HydrosRecipeBookClient.isEnabled()) {
			return;
		}
		RecipeButton button = (RecipeButton) (Object) this;
		if (!FavoriteStore.isFavorite(button.getCollection())) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		int x = button.getX() + button.getWidth()
			- font.width(HYDROS_RECIPE_BOOK$FAVORITE_BADGE) - 1;
		graphics.text(
			font,
			HYDROS_RECIPE_BOOK$FAVORITE_BADGE,
			x,
			button.getY() + 1,
			0xFFFFD700,
			true
		);
	}

	@Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
	private void hydrosRecipeBook$addFavoriteTooltip(
		ItemStack stack,
		CallbackInfoReturnable<List<Component>> callback
	) {
		if (!HydrosRecipeBookClient.isEnabled()) {
			return;
		}
		List<Component> tooltip = new ArrayList<>(callback.getReturnValue());
		boolean favorite = FavoriteStore.isFavorite(
			((RecipeButton) (Object) this).getCollection()
		);
		if (favorite) {
			tooltip.add(Component.translatable("hydros_recipe_book.favorite.tooltip")
				.withStyle(ChatFormatting.GOLD));
		}
		tooltip.add(Component.translatable(
			favorite
				? "hydros_recipe_book.favorite.remove_hint"
				: "hydros_recipe_book.favorite.add_hint"
		).withStyle(ChatFormatting.DARK_GRAY));
		callback.setReturnValue(tooltip);
	}
}
