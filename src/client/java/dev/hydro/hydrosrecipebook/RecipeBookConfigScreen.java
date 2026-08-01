package dev.hydro.hydrosrecipebook;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RecipeBookConfigScreen {
	private RecipeBookConfigScreen() {
	}

	public static Screen create(Screen parent) {
		RecipeBookConfig config = HydrosRecipeBookClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("config.hydros_recipe_book.title"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(
			Component.translatable("config.hydros_recipe_book.category.general")
		);

		category.addEntry(entries.startTextDescription(
			Component.translatable("config.hydros_recipe_book.category.general.description")
		).build());
		category.addEntry(entries.startBooleanToggle(
				Component.translatable("config.hydros_recipe_book.enabled"),
				config.enabled
			)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("config.hydros_recipe_book.enabled.tooltip"))
			.setSaveConsumer(value -> config.enabled = value)
			.build());

		builder.setSavingRunnable(config::save);
		return builder.build();
	}
}
