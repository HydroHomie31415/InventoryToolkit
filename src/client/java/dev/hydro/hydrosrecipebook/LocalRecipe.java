package dev.hydro.hydrosrecipebook;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

public record LocalRecipe(
	Identifier key,
	RecipeHolder<?> holder,
	RecipeDisplayEntry displayEntry
) {
}
