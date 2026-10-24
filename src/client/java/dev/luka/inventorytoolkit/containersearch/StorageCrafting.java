package dev.luka.inventorytoolkit.containersearch;

import dev.hydro.hydrosrecipebook.LocalRecipeCatalog;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Works out how many of an item could be crafted, in one crafting step, from
 * what the player's inventory and remembered containers hold. Recipes come
 * from the client's own recipe data, so a server with data packs may differ.
 */
public final class StorageCrafting {
	private static ClientPacketListener recipesConnection;
	private static List<CraftRecipe> recipes = List.of();

	/**
	 * @param choices the items one ingredient slot accepts
	 * @param perCraft how many slots of the recipe take this ingredient
	 */
	public record Ingredient(List<Item> choices, int perCraft) {
	}

	public record CraftRecipe(
		Identifier id,
		Item result,
		int resultCount,
		String searchName,
		String searchId,
		List<Ingredient> ingredients
	) {
	}

	/**
	 * @param used how many of each item the crafts take
	 * @param limiting the ingredient that runs out first
	 */
	public record Plan(CraftRecipe recipe, int crafts, Map<Item, Integer> used, Ingredient limiting) {
		public int made() {
			return crafts * recipe.resultCount();
		}
	}

	/**
	 * A container to take ingredients from, and how many of each to take.
	 */
	public record Source(ContainerMemory.StoredContainer container, Map<Item, Integer> taken, double distance) {
	}

	/**
	 * Item counts the player can craft with, split by where they are.
	 */
	public record Stock(Map<Item, Integer> inventory, Map<Item, Integer> total) {
	}

	private StorageCrafting() {
	}

	/**
	 * The best plan for every item matching the query that can be crafted at
	 * least once, by name.
	 */
	public static List<Plan> craftable(Minecraft client, String query, Stock stock) {
		Map<Item, Plan> best = new LinkedHashMap<>();
		for (CraftRecipe recipe : recipes(client)) {
			if (!recipe.searchName().contains(query) && !recipe.searchId().contains(query)) {
				continue;
			}
			Plan plan = plan(recipe, stock.total());
			Plan previous = best.get(recipe.result());
			if (plan != null && (previous == null || plan.made() > previous.made())) {
				best.put(recipe.result(), plan);
			}
		}
		List<Plan> plans = new ArrayList<>(best.values());
		plans.sort(Comparator.comparing(plan -> plan.recipe().searchName()));
		return plans;
	}

	/**
	 * Counts the items in the player's inventory and in every remembered
	 * container of this dimension, including those inside stored shulker
	 * boxes and bundles.
	 */
	public static Stock stock(Minecraft client, List<ContainerMemory.StoredContainer> containers) {
		Map<Item, Integer> inventory = new HashMap<>();
		if (client.player != null) {
			for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty()) {
					inventory.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		Map<Item, Integer> total = new HashMap<>(inventory);
		Map<String, Item> itemsById = new HashMap<>();
		for (ContainerMemory.StoredContainer container : containers) {
			for (ContainerMemory.StoredStack stack : container.items()) {
				Item item = itemsById.computeIfAbsent(stack.item(), StorageCrafting::item);
				if (item != Items.AIR) {
					total.merge(item, stack.count(), Integer::sum);
				}
			}
		}
		return new Stock(inventory, total);
	}

	/**
	 * Picks the containers to take the plan's ingredients from: whatever the
	 * inventory already holds is used first, then the nearest containers.
	 *
	 * @param containers nearest first
	 */
	public static List<Source> sources(
		Plan plan,
		Stock stock,
		List<ContainerMemory.StoredContainer> containers,
		List<Double> distances
	) {
		Map<Item, Integer> needed = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> use : plan.used().entrySet()) {
			int fromStorage = use.getValue() - stock.inventory().getOrDefault(use.getKey(), 0);
			if (fromStorage > 0) {
				needed.put(use.getKey(), fromStorage);
			}
		}

		List<Source> sources = new ArrayList<>();
		Map<String, Item> itemsById = new HashMap<>();
		for (int index = 0; index < containers.size() && !needed.isEmpty(); index++) {
			ContainerMemory.StoredContainer container = containers.get(index);
			Map<Item, Integer> taken = new LinkedHashMap<>();
			for (ContainerMemory.StoredStack stack : container.items()) {
				Item item = itemsById.computeIfAbsent(stack.item(), StorageCrafting::item);
				Integer stillNeeded = needed.get(item);
				if (stillNeeded == null) {
					continue;
				}
				int take = Math.min(stillNeeded, stack.count());
				taken.merge(item, take, Integer::sum);
				if (take == stillNeeded) {
					needed.remove(item);
				} else {
					needed.put(item, stillNeeded - take);
				}
			}
			if (!taken.isEmpty()) {
				sources.add(new Source(container, taken, distances.get(index)));
			}
		}
		return sources;
	}

	/**
	 * @return the most crafts the available items allow, or null when the
	 *     recipe cannot be crafted even once
	 */
	static Plan plan(CraftRecipe recipe, Map<Item, Integer> available) {
		List<Ingredient> ingredients = recipe.ingredients();
		List<Item> items = new ArrayList<>();
		Map<Item, Integer> itemIndex = new HashMap<>();
		long upper = Long.MAX_VALUE;
		Ingredient limiting = null;
		for (Ingredient ingredient : ingredients) {
			long supply = 0;
			for (Item choice : ingredient.choices()) {
				int count = available.getOrDefault(choice, 0);
				if (count > 0) {
					supply += count;
					if (!itemIndex.containsKey(choice)) {
						itemIndex.put(choice, items.size());
						items.add(choice);
					}
				}
			}
			long crafts = supply / ingredient.perCraft();
			if (crafts < upper) {
				upper = crafts;
				limiting = ingredient;
			}
		}
		if (upper <= 0) {
			return null;
		}

		// Ingredients that accept several items (any planks, any wool) may
		// compete for the same items, so the largest number of crafts is
		// found by binary search over a flow from ingredients to items.
		long low = 0;
		long high = Math.min(upper, Integer.MAX_VALUE);
		long[] bestUse = null;
		while (low < high) {
			long middle = (low + high + 1) >>> 1;
			long[] use = assign(middle, ingredients, items, itemIndex, available);
			if (use != null) {
				low = middle;
				bestUse = use;
			} else {
				high = middle - 1;
			}
		}
		if (bestUse == null) {
			return null;
		}

		Map<Item, Integer> used = new LinkedHashMap<>();
		for (int index = 0; index < items.size(); index++) {
			if (bestUse[index] > 0) {
				used.put(items.get(index), (int) bestUse[index]);
			}
		}
		return new Plan(recipe, (int) low, used, limiting);
	}

	/**
	 * @return how many of each item the crafts take, or null when the items
	 *     cannot cover them
	 */
	private static long[] assign(
		long crafts,
		List<Ingredient> ingredients,
		List<Item> items,
		Map<Item, Integer> itemIndex,
		Map<Item, Integer> available
	) {
		int ingredientCount = ingredients.size();
		int nodes = ingredientCount + items.size() + 2;
		int source = nodes - 2;
		int sink = nodes - 1;
		long[][] capacity = new long[nodes][nodes];
		long demand = 0;
		for (int index = 0; index < ingredientCount; index++) {
			Ingredient ingredient = ingredients.get(index);
			capacity[source][index] = crafts * ingredient.perCraft();
			demand += capacity[source][index];
			for (Item choice : ingredient.choices()) {
				Integer item = itemIndex.get(choice);
				if (item != null) {
					capacity[index][ingredientCount + item] = Long.MAX_VALUE / 4;
				}
			}
		}
		for (int index = 0; index < items.size(); index++) {
			capacity[ingredientCount + index][sink] = available.get(items.get(index));
		}

		if (maxFlow(capacity, source, sink) < demand) {
			return null;
		}
		long[] use = new long[items.size()];
		for (int index = 0; index < items.size(); index++) {
			use[index] = available.get(items.get(index)) - capacity[ingredientCount + index][sink];
		}
		return use;
	}

	/**
	 * Edmonds-Karp; leaves the residual capacities in the matrix.
	 */
	private static long maxFlow(long[][] capacity, int source, int sink) {
		int nodes = capacity.length;
		int[] previous = new int[nodes];
		long flow = 0;
		while (true) {
			Arrays.fill(previous, -1);
			previous[source] = source;
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(source);
			while (!queue.isEmpty() && previous[sink] == -1) {
				int node = queue.poll();
				for (int next = 0; next < nodes; next++) {
					if (previous[next] == -1 && capacity[node][next] > 0) {
						previous[next] = node;
						queue.add(next);
					}
				}
			}
			if (previous[sink] == -1) {
				return flow;
			}
			long bottleneck = Long.MAX_VALUE;
			for (int node = sink; node != source; node = previous[node]) {
				bottleneck = Math.min(bottleneck, capacity[previous[node]][node]);
			}
			for (int node = sink; node != source; node = previous[node]) {
				capacity[previous[node]][node] -= bottleneck;
				capacity[node][previous[node]] += bottleneck;
			}
			flow += bottleneck;
		}
	}

	private static List<CraftRecipe> recipes(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		if (connection != recipesConnection) {
			recipesConnection = connection;
			recipes = loadRecipes(client, connection);
		}
		return recipes;
	}

	private static List<CraftRecipe> loadRecipes(Minecraft client, ClientPacketListener connection) {
		List<CraftRecipe> loaded = new ArrayList<>();
		if (connection == null || client.level == null) {
			return loaded;
		}
		ContextMap context = SlotDisplayContext.fromLevel(client.level);
		FeatureFlagSet features = client.level.enabledFeatures();
		try {
			for (Map.Entry<Identifier, Recipe<?>> entry : LocalRecipeCatalog.decodeRecipes(client, connection).entrySet()) {
				for (RecipeDisplay display : entry.getValue().display()) {
					CraftRecipe recipe = toCraftRecipe(entry.getKey(), display, context, features);
					if (recipe != null) {
						loaded.add(recipe);
					}
				}
			}
		} catch (RuntimeException exception) {
			InventoryToolkitClient.LOGGER.warn("Could not read recipes for container search", exception);
		}
		return List.copyOf(loaded);
	}

	private static CraftRecipe toCraftRecipe(
		Identifier id,
		RecipeDisplay display,
		ContextMap context,
		FeatureFlagSet features
	) {
		List<SlotDisplay> slots;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			slots = shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			slots = shapeless.ingredients();
		} else {
			return null;
		}
		if (!display.isEnabled(features)) {
			return null;
		}
		ItemStack result = display.result().resolveForFirstStack(context);
		if (result.isEmpty()) {
			return null;
		}

		// Slots that accept the same items are one ingredient used several times.
		Map<Set<Item>, Integer> grouped = new LinkedHashMap<>();
		for (SlotDisplay slot : slots) {
			Set<Item> choices = new LinkedHashSet<>();
			for (ItemStack choice : slot.resolveForStacks(context)) {
				// Crafting an item out of itself (re-dyeing wool) is never
				// what a search for it means.
				if (!choice.isEmpty() && !choice.is(result.getItem())) {
					choices.add(choice.getItem());
				}
			}
			if (choices.isEmpty()) {
				if (slot instanceof SlotDisplay.Empty || slot.resolveForStacks(context).isEmpty()) {
					continue;
				}
				return null;
			}
			grouped.merge(choices, 1, Integer::sum);
		}
		if (grouped.isEmpty()) {
			return null;
		}

		List<Ingredient> ingredients = new ArrayList<>();
		for (Map.Entry<Set<Item>, Integer> group : grouped.entrySet()) {
			ingredients.add(new Ingredient(List.copyOf(group.getKey()), group.getValue()));
		}
		return new CraftRecipe(
			id,
			result.getItem(),
			result.getCount(),
			result.getHoverName().getString().toLowerCase(Locale.ROOT),
			BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
			List.copyOf(ingredients)
		);
	}

	private static Item item(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(parsed);
	}
}
