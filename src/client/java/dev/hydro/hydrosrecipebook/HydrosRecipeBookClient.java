package dev.hydro.hydrosrecipebook;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class HydrosRecipeBookClient implements ClientModInitializer {
	public static final String MOD_ID = "hydros_recipe_book";
	private static RecipeBookConfig config;
	private static boolean wasEnabled;

	@Override
	public void onInitializeClient() {
		config = RecipeBookConfig.load();
		FavoriteStore.load();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!isEnabled()) {
				if (wasEnabled) {
					LocalRecipeCatalog.disable(minecraft);
					LocalRecipePlacer.cancelAll();
					InstantCraftManager.cancel();
				}
				wasEnabled = false;
				return;
			}
			if (!wasEnabled) {
				LocalRecipeCatalog.enable();
			}
			wasEnabled = true;
			LocalRecipeCatalog.tick(minecraft);
			LocalRecipePlacer.tick(minecraft);
			InstantCraftManager.tick(minecraft);
		});
	}

	public static RecipeBookConfig config() {
		return config;
	}

	public static boolean isEnabled() {
		return config == null || config.enabled;
	}
}
