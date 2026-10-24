package dev.luka.inventorytoolkit.compact;

import dev.hydro.hydrosrecipebook.LocalRecipeCatalog;
import dev.hydro.hydrosrecipebook.mixin.client.ClientRecipeBookAccessor;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the "nine of one item make one block" recipes in the player's recipe
 * book. Only recipes the server sent are used, because the server places them
 * itself; recipes the toolkit adds to the book locally are unknown to it.
 */
final class CompactRecipes {
	private static final int GRID_SIZE = 3;
	private static final int UNCRAFT_COUNT = 9;

	private static ClientPacketListener uncraftsConnection;
	private static Map<Item, Set<Item>> uncrafts = Map.of();

	private CompactRecipes() {
	}

	record CompactRecipe(RecipeDisplayId id, Item ingredient, Item result) {
	}

	static List<CompactRecipe> find(Minecraft client, CompactConfig config) {
		ContextMap context = SlotDisplayContext.fromLevel(client.level);
		Map<RecipeDisplayId, RecipeDisplayEntry> known =
			((ClientRecipeBookAccessor) client.player.getRecipeBook()).hydrosRecipeBook$getKnown();
		Set<Item> excluded = excludedItems(config);
		Set<Item> seenIngredients = new HashSet<>();

		List<CompactRecipe> recipes = new ArrayList<>();
		for (RecipeDisplayEntry entry : known.values()) {
			if (entry.id().index() < 0
				|| !(entry.display() instanceof ShapedCraftingRecipeDisplay shaped)
				|| shaped.width() != GRID_SIZE
				|| shaped.height() != GRID_SIZE) {
				continue;
			}
			Item ingredient = singleItem(shaped.ingredients(), context);
			ItemStack result = shaped.result().resolveForFirstStack(context);
			if (ingredient == null
				|| result.isEmpty()
				|| result.getCount() != 1
				|| result.is(ingredient)
				|| excluded.contains(ingredient)
				|| excluded.contains(result.getItem())
				|| (config.onlyReversible && !isReversible(client, result.getItem(), ingredient))
				|| !seenIngredients.add(ingredient)) {
				continue;
			}
			recipes.add(new CompactRecipe(entry.id(), ingredient, result.getItem()));
		}
		recipes.sort(Comparator.comparing(recipe -> BuiltInRegistries.ITEM.getKey(recipe.ingredient())));
		return recipes;
	}

	/**
	 * @return the item every one of the nine slots takes, or null when the
	 *     slots differ or accept several items
	 */
	private static Item singleItem(List<SlotDisplay> ingredients, ContextMap context) {
		if (ingredients.size() != GRID_SIZE * GRID_SIZE) {
			return null;
		}
		Item item = null;
		for (SlotDisplay ingredient : ingredients) {
			List<ItemStack> choices = ingredient.resolveForStacks(context);
			if (choices.size() != 1 || (item != null && !choices.getFirst().is(item))) {
				return null;
			}
			item = choices.getFirst().getItem();
		}
		return item;
	}

	private static Set<Item> excludedItems(CompactConfig config) {
		Set<Item> excluded = new HashSet<>();
		for (String value : config.excludedItems) {
			Item item = CompactConfig.parseItem(value);
			if (item != null) {
				excluded.add(item);
			}
		}
		return excluded;
	}

	/**
	 * A block is reversible when some recipe turns it back into nine of the
	 * item, so compacting never loses anything (unlike melons or packed ice).
	 * The server's recipe book only holds the reverse recipe once the player
	 * has owned the block, so the client's own recipe data is used instead.
	 */
	private static boolean isReversible(Minecraft client, Item block, Item ingredient) {
		ClientPacketListener connection = client.getConnection();
		if (connection != uncraftsConnection) {
			uncraftsConnection = connection;
			uncrafts = loadUncrafts(client, connection);
		}
		return uncrafts.getOrDefault(block, Set.of()).contains(ingredient);
	}

	private static Map<Item, Set<Item>> loadUncrafts(Minecraft client, ClientPacketListener connection) {
		Map<Item, Set<Item>> found = new HashMap<>();
		if (connection == null) {
			return found;
		}
		ContextMap context = SlotDisplayContext.fromLevel(client.level);
		try {
			for (Recipe<?> recipe : LocalRecipeCatalog.decodeRecipes(client, connection).values()) {
				for (RecipeDisplay display : recipe.display()) {
					SlotDisplay input = singleInput(display);
					if (input == null) {
						continue;
					}
					ItemStack result = display.result().resolveForFirstStack(context);
					if (result.getCount() != UNCRAFT_COUNT) {
						continue;
					}
					for (ItemStack choice : input.resolveForStacks(context)) {
						found.computeIfAbsent(choice.getItem(), ignored -> new HashSet<>()).add(result.getItem());
					}
				}
			}
		} catch (RuntimeException exception) {
			InventoryToolkitClient.LOGGER.warn("Could not read recipes for compacting", exception);
		}
		return found;
	}

	/**
	 * @return the only filled ingredient of a crafting recipe, or null
	 */
	private static SlotDisplay singleInput(RecipeDisplay display) {
		List<SlotDisplay> ingredients;
		if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			ingredients = shapeless.ingredients();
		} else if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			ingredients = shaped.ingredients();
		} else {
			return null;
		}
		SlotDisplay input = null;
		for (SlotDisplay ingredient : ingredients) {
			if (ingredient instanceof SlotDisplay.Empty) {
				continue;
			}
			if (input != null) {
				return null;
			}
			input = ingredient;
		}
		return input;
	}
}
