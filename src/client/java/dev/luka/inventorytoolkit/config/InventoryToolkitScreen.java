package dev.luka.inventorytoolkit.config;

import be.luka.quickoffhand.QuickOffhandClient;
import dev.hydro.hydrosbundles.config.HydrosBundlesSettingsScreen;
import dev.hydro.hydrosrecipebook.RecipeBookConfigScreen;
import dev.inventorysweeper.config.SweeperConfigScreen;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfigScreen;
import dev.luka.smarthotbar.LoadoutScreen;
import dev.luka.smarthotbar.SmartHotbarClient;
import dev.luka.shiftdragtransfer.config.ShiftDragTransferConfigScreen;
import dev.luka.stackrefill.config.StackRefillConfigScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class InventoryToolkitScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 3;
	private static final int GRID_BUTTON_WIDTH = 190;
	private static final int GRID_GAP = 8;

	private final Screen parent;
	private int moduleColumns;
	private int moduleButtonWidth;
	private int moduleFirstX;
	private int moduleFirstY;

	public InventoryToolkitScreen(Screen parent) {
		super(Component.translatable("screen.inventory_toolkit.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		moduleColumns = width >= 320 ? 2 : 1;
		moduleButtonWidth = moduleColumns == 2
			? Math.min(GRID_BUTTON_WIDTH, (width - GRID_GAP - 12) / 2)
			: Math.min(BUTTON_WIDTH, width - 12);
		int rowCount = (8 + moduleColumns - 1) / moduleColumns;
		int gridWidth = moduleButtonWidth * moduleColumns + GRID_GAP * (moduleColumns - 1);
		moduleFirstX = width / 2 - gridWidth / 2;
		moduleFirstY = Math.max(45, height / 2 - (rowCount * (BUTTON_HEIGHT + GAP) + 27) / 2);

		addModuleButton(
			0,
			"screen.inventory_toolkit.recipe_book",
			"screen.inventory_toolkit.recipe_book.description",
			button -> minecraft.gui.setScreen(RecipeBookConfigScreen.create(this))
		);
		addModuleButton(
			1,
			"screen.inventory_toolkit.loadouts",
			"screen.inventory_toolkit.loadouts.description",
			button -> minecraft.gui.setScreen(new LoadoutScreen(this, SmartHotbarClient.CONFIG))
		);
		addModuleButton(
			2,
			"screen.inventory_toolkit.offhand_swapper",
			"screen.inventory_toolkit.offhand_swapper.description",
			button -> minecraft.gui.setScreen(QuickOffhandClient.createConfigScreen(this))
		);
		addModuleButton(
			3,
			"screen.inventory_toolkit.auto_tool",
			"screen.inventory_toolkit.auto_tool.description",
			button -> minecraft.gui.setScreen(AutoToolSwitchConfigScreen.create(this))
		);
		addModuleButton(
			4,
			"screen.inventory_toolkit.transfers",
			"screen.inventory_toolkit.transfers.description",
			button -> minecraft.gui.setScreen(ShiftDragTransferConfigScreen.create(this))
		);
		addModuleButton(
			5,
			"screen.inventory_toolkit.refill",
			"screen.inventory_toolkit.refill.description",
			button -> minecraft.gui.setScreen(StackRefillConfigScreen.create(this))
		);
		addModuleButton(
			6,
			"screen.inventory_toolkit.bundles",
			"screen.inventory_toolkit.bundles.description",
			button -> minecraft.gui.setScreen(new HydrosBundlesSettingsScreen(this))
		);
		addModuleButton(
			7,
			"screen.inventory_toolkit.sweeper",
			"screen.inventory_toolkit.sweeper.description",
			button -> minecraft.gui.setScreen(SweeperConfigScreen.create(this))
		);

		addRenderableWidget(Button.builder(
			Component.translatable("screen.inventory_toolkit.done"),
			button -> onClose()
		).bounds(
			width / 2 - 70,
			moduleFirstY + (BUTTON_HEIGHT + GAP) * rowCount + 7,
			140,
			20
		).build());
	}

	private void addModuleButton(
		int index,
		String labelKey,
		String descriptionKey,
		Button.OnPress action
	) {
		int column = index % moduleColumns;
		int row = index / moduleColumns;
		Button button = Button.builder(Component.translatable(labelKey), action)
			.bounds(
				moduleFirstX + column * (moduleButtonWidth + GRID_GAP),
				moduleFirstY + row * (BUTTON_HEIGHT + GAP),
				moduleButtonWidth,
				BUTTON_HEIGHT
			)
			.build();
		button.setTooltip(Tooltip.create(Component.translatable(descriptionKey)));
		addRenderableWidget(button);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xD0101010);
		graphics.centeredText(font, title, width / 2, 22, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable("screen.inventory_toolkit.subtitle"),
			width / 2,
			36,
			0xFFAAAAAA
		);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
