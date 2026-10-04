package dev.hydro.hydrosrecipebook;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.hydro.hydrosrecipebook.mixin.client.ClientPacketListenerAccessor;
import dev.hydro.hydrosrecipebook.mixin.client.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.core.registries.Registries;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

public final class LocalRecipeCatalog {
	private static final FileToIdConverter RECIPE_FILES = FileToIdConverter.json("recipe");
	private static final Map<RecipeDisplayId, LocalRecipe> BY_DISPLAY_ID = new HashMap<>();
	private static final Map<RecipeDisplay, Identifier> KEYS_BY_DISPLAY = new HashMap<>();
	private static ClientPacketListener loadedConnection;
	private static ClientRecipeBook installedBook;
	private static boolean loadFailed;

	private LocalRecipeCatalog() {
	}

	public static void enable() {
		loadedConnection = null;
		installedBook = null;
		loadFailed = false;
	}

	public static void disable(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		ClientRecipeBook book = minecraft.player == null
			? null
			: minecraft.player.getRecipeBook();
		if (connection != null && book != null) {
			Map<RecipeDisplayId, RecipeDisplayEntry> known =
				((ClientRecipeBookAccessor) book).hydrosRecipeBook$getKnown();
			boolean changed = known.keySet().removeIf(BY_DISPLAY_ID::containsKey);
			changed |= RecipeBookEnhancements.applyEnabled(book, false);
			if (changed) {
				((ClientPacketListenerAccessor) connection)
					.hydrosRecipeBook$refreshRecipeBook(book);
			}
		}

		loadedConnection = null;
		installedBook = null;
		BY_DISPLAY_ID.clear();
		KEYS_BY_DISPLAY.clear();
		loadFailed = false;
	}

	public static void tick(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || minecraft.player == null || minecraft.level == null) {
			loadedConnection = null;
			installedBook = null;
			BY_DISPLAY_ID.clear();
			KEYS_BY_DISPLAY.clear();
			loadFailed = false;
			return;
		}

		if (connection != loadedConnection) {
			loadedConnection = connection;
			installedBook = null;
			loadFailed = false;
			load(minecraft, connection);
		}

		ClientRecipeBook book = minecraft.player.getRecipeBook();
		if (!loadFailed && (book != installedBook || !isInstalled(book))) {
			install(connection, book);
			installedBook = book;
		}
	}

	public static LocalRecipe get(RecipeDisplayId id) {
		return BY_DISPLAY_ID.get(id);
	}

	public static boolean isLocal(RecipeDisplayId id) {
		return id != null && id.index() < 0 && BY_DISPLAY_ID.containsKey(id);
	}

	public static Identifier keyForDisplay(RecipeDisplay display) {
		return KEYS_BY_DISPLAY.get(display);
	}

	private static void load(Minecraft minecraft, ClientPacketListener connection) {
		BY_DISPLAY_ID.clear();
		KEYS_BY_DISPLAY.clear();

		try {
			List<PackResources> packs = new ArrayList<>(
				minecraft.getResourceManager().listPacks().toList()
			);
			PackResources vanilla = minecraft.getVanillaPackResources().fullResources();
			if (packs.stream().noneMatch(pack -> pack == vanilla)) {
				packs.addFirst(vanilla);
			}

			MultiPackResourceManager resources =
				new MultiPackResourceManager(PackType.SERVER_DATA, packs);
			Map<Identifier, Recipe<?>> decoded = new TreeMap<>();
			RegistryOps<JsonElement> ops =
				connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			for (Map.Entry<Identifier, Resource> file : RECIPE_FILES.listMatchingResources(resources).entrySet()) {
				Identifier id = RECIPE_FILES.fileToId(file.getKey());
				try (BufferedReader reader = file.getValue().openAsReader()) {
					Recipe.CODEC.parse(ops, StrictJsonParser.parse(reader))
						.ifSuccess(recipe -> decoded.put(id, recipe.value()));
				} catch (Exception exception) {
					// Skip unreadable or malformed recipe files, like vanilla does.
				}
			}

			int nextSyntheticId = -1;
			for (Map.Entry<Identifier, Recipe<?>> decodedRecipe : decoded.entrySet()) {
				Recipe<?> recipe = decodedRecipe.getValue();
				if (recipe.placementInfo().isImpossibleToPlace()) {
					continue;
				}

				ResourceKey<Recipe<?>> key = ResourceKey.create(
					Registries.RECIPE,
					decodedRecipe.getKey()
				);
				RecipeHolder<?> holder = new RecipeHolder<>(key, recipe);
				for (RecipeDisplay display : recipe.display()) {
					RecipeDisplayId displayId = new RecipeDisplayId(nextSyntheticId--);
					RecipeDisplayEntry entry = new RecipeDisplayEntry(
						displayId,
						display,
						OptionalInt.empty(),
						recipe.recipeBookCategory(),
						Optional.of(recipe.placementInfo().ingredients())
					);
					LocalRecipe localRecipe = new LocalRecipe(
						decodedRecipe.getKey(),
						holder,
						entry
					);
					BY_DISPLAY_ID.put(displayId, localRecipe);
					KEYS_BY_DISPLAY.putIfAbsent(display, decodedRecipe.getKey());
				}
			}
			HydrosRecipeBookLog.info(
				"Loaded {} local recipe displays from {} recipes",
				BY_DISPLAY_ID.size(),
				decoded.size()
			);
		} catch (RuntimeException exception) {
			loadFailed = true;
			HydrosRecipeBookLog.warn("Could not build the local recipe catalog", exception);
		}
	}

	private static void install(ClientPacketListener connection, ClientRecipeBook book) {
		RecipeBookEnhancements.applyEnabled(book, true);
		Map<RecipeDisplayId, RecipeDisplayEntry> known =
			((ClientRecipeBookAccessor) book).hydrosRecipeBook$getKnown();
		Set<RecipeDisplay> serverDisplays = new HashSet<>();
		for (RecipeDisplayEntry entry : known.values()) {
			if (entry.id().index() >= 0) {
				serverDisplays.add(entry.display());
			}
		}
		for (LocalRecipe localRecipe : BY_DISPLAY_ID.values()) {
			if (!serverDisplays.contains(localRecipe.displayEntry().display())) {
				book.add(localRecipe.displayEntry());
			}
		}
		((ClientPacketListenerAccessor) connection)
			.hydrosRecipeBook$refreshRecipeBook(book);
	}

	private static boolean isInstalled(ClientRecipeBook book) {
		if (BY_DISPLAY_ID.isEmpty()) {
			return true;
		}
		Map<RecipeDisplayId, RecipeDisplayEntry> known =
			((ClientRecipeBookAccessor) book).hydrosRecipeBook$getKnown();
		Set<RecipeDisplay> knownDisplays = new HashSet<>();
		for (RecipeDisplayEntry entry : known.values()) {
			knownDisplays.add(entry.display());
		}
		return BY_DISPLAY_ID.values().stream().allMatch(
			localRecipe -> knownDisplays.contains(localRecipe.displayEntry().display())
		);
	}
}
