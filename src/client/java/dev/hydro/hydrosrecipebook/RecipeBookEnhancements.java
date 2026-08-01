package dev.hydro.hydrosrecipebook;

import dev.hydro.hydrosrecipebook.mixin.client.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;

public final class RecipeBookEnhancements {
	private static final Map<ClientRecipeBook, Map<RecipeDisplayId, RecipeDisplayEntry>>
		VANILLA_ENTRIES = new WeakHashMap<>();

	private RecipeBookEnhancements() {
	}

	public static RecipeDisplayEntry processEntry(
		ClientRecipeBook book,
		RecipeDisplayEntry entry
	) {
		if (entry.id().index() < 0) {
			return entry;
		}
		VANILLA_ENTRIES.computeIfAbsent(book, ignored -> new HashMap<>())
			.put(entry.id(), entry);
		return HydrosRecipeBookClient.isEnabled() ? withoutGroup(entry) : entry;
	}

	public static boolean applyEnabled(ClientRecipeBook book, boolean enabled) {
		Map<RecipeDisplayId, RecipeDisplayEntry> originals = VANILLA_ENTRIES.get(book);
		if (originals == null || originals.isEmpty()) {
			return false;
		}

		Map<RecipeDisplayId, RecipeDisplayEntry> known =
			((ClientRecipeBookAccessor) book).hydrosRecipeBook$getKnown();
		boolean changed = false;
		for (Map.Entry<RecipeDisplayId, RecipeDisplayEntry> original : originals.entrySet()) {
			RecipeDisplayEntry current = known.get(original.getKey());
			if (current == null) {
				continue;
			}
			RecipeDisplayEntry replacement = enabled
				? withoutGroup(original.getValue())
				: original.getValue();
			if (!current.equals(replacement)) {
				known.put(original.getKey(), replacement);
				changed = true;
			}
		}
		return changed;
	}

	private static RecipeDisplayEntry withoutGroup(RecipeDisplayEntry entry) {
		if (entry.group().isEmpty()) {
			return entry;
		}
		return new RecipeDisplayEntry(
			entry.id(),
			entry.display(),
			OptionalInt.empty(),
			entry.category(),
			entry.craftingRequirements()
		);
	}
}
