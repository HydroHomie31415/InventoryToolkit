package dev.hydro.hydrosrecipebook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FavoriteStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("hydros-recipe-book.json");
	private static final Set<String> FAVORITE_RECIPES = new HashSet<>();
	private static final Set<String> FAVORITE_ITEMS = new HashSet<>();

	private FavoriteStore() {
	}

	public static void load() {
		FAVORITE_RECIPES.clear();
		FAVORITE_ITEMS.clear();
		if (!Files.isRegularFile(PATH)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			Config config = GSON.fromJson(reader, Config.class);
			if (config != null) {
				loadIdentifiers(config.favoriteRecipes, FAVORITE_RECIPES);
				loadIdentifiers(config.favoriteItems, FAVORITE_ITEMS);
			}
		} catch (IOException | JsonParseException exception) {
			HydrosRecipeBookLog.warn("Could not read favorites from " + PATH, exception);
		}
	}

	public static boolean isFavorite(ItemStack stack) {
		return !stack.isEmpty() && FAVORITE_ITEMS.contains(itemKey(stack));
	}

	public static boolean isFavorite(RecipeCollection collection) {
		String recipeKey = recipeKey(collection);
		if (recipeKey != null && FAVORITE_RECIPES.contains(recipeKey)) {
			return true;
		}
		ItemStack stack = displayStack(collection);
		return !stack.isEmpty() && isFavorite(stack);
	}

	public static void toggle(RecipeCollection collection) {
		ItemStack stack = displayStack(collection);
		String recipeKey = recipeKey(collection);
		if (recipeKey == null && stack.isEmpty()) {
			return;
		}

		boolean added;
		if (recipeKey != null && FAVORITE_RECIPES.remove(recipeKey)) {
			added = false;
		} else if (!stack.isEmpty() && FAVORITE_ITEMS.remove(itemKey(stack))) {
			added = false;
		} else {
			if (recipeKey != null) {
				FAVORITE_RECIPES.add(recipeKey);
			} else {
				FAVORITE_ITEMS.add(itemKey(stack));
			}
			added = true;
		}
		save();

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			Component itemName = stack.getHoverName();
			minecraft.player.sendOverlayMessage(Component.translatable(
				added ? "hydros_recipe_book.favorite.added" : "hydros_recipe_book.favorite.removed",
				itemName
			));
		}
	}

	public static List<RecipeCollection> favoritesFirst(List<RecipeCollection> collections) {
		List<RecipeCollection> sorted = new ArrayList<>(collections);
		sorted.sort(Comparator.comparing(
			(RecipeCollection collection) -> isFavorite(collection)
		).reversed());
		return sorted;
	}

	public static ItemStack displayStack(RecipeCollection collection) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return ItemStack.EMPTY;
		}

		ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
		for (RecipeDisplayEntry entry : collection.getRecipes()) {
			List<ItemStack> results = entry.resultItems(context);
			if (!results.isEmpty() && !results.getFirst().isEmpty()) {
				return results.getFirst();
			}
		}
		return ItemStack.EMPTY;
	}

	private static String itemKey(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String recipeKey(RecipeCollection collection) {
		for (RecipeDisplayEntry entry : collection.getRecipes()) {
			Identifier key = LocalRecipeCatalog.keyForDisplay(entry.display());
			if (key != null) {
				return key.toString();
			}
		}
		return null;
	}

	private static void loadIdentifiers(List<String> identifiers, Set<String> destination) {
		if (identifiers == null) {
			return;
		}
		identifiers.stream()
			.map(Identifier::tryParse)
			.filter(java.util.Objects::nonNull)
			.map(Identifier::toString)
			.forEach(destination::add);
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(new Config(
					FAVORITE_RECIPES.stream().sorted().toList(),
					FAVORITE_ITEMS.stream().sorted().toList()
				), writer);
			}
		} catch (IOException exception) {
			HydrosRecipeBookLog.warn("Could not save favorites to " + PATH, exception);
		}
	}

	private record Config(List<String> favoriteRecipes, List<String> favoriteItems) {
	}
}
