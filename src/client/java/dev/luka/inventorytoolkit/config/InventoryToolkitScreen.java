package dev.luka.inventorytoolkit.config;

import dev.inventorysweeper.config.SweeperConfigScreen;
import dev.luka.smarthotbar.LoadoutScreen;
import dev.luka.smarthotbar.SmartHotbarClient;
import dev.luka.stackrefill.config.StackRefillConfigScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class InventoryToolkitScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 24;
	private static final int GAP = 16;

	private final Screen parent;

	public InventoryToolkitScreen(Screen parent) {
		super(Component.translatable("screen.inventory_toolkit.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int firstY = Math.max(52, height / 2 - 60);

		addModuleButton(
			firstY,
			"screen.inventory_toolkit.loadouts",
			"screen.inventory_toolkit.loadouts.description",
			button -> minecraft.gui.setScreen(new LoadoutScreen(this, SmartHotbarClient.CONFIG))
		);
		addModuleButton(
			firstY + BUTTON_HEIGHT + GAP,
			"screen.inventory_toolkit.refill",
			"screen.inventory_toolkit.refill.description",
			button -> minecraft.gui.setScreen(StackRefillConfigScreen.create(this))
		);
		addModuleButton(
			firstY + (BUTTON_HEIGHT + GAP) * 2,
			"screen.inventory_toolkit.sweeper",
			"screen.inventory_toolkit.sweeper.description",
			button -> minecraft.gui.setScreen(SweeperConfigScreen.create(this))
		);

		addRenderableWidget(Button.builder(
			Component.translatable("screen.inventory_toolkit.done"),
			button -> onClose()
		).bounds(width / 2 - 70, firstY + (BUTTON_HEIGHT + GAP) * 3 + 8, 140, 20).build());
	}

	private void addModuleButton(
		int y,
		String labelKey,
		String descriptionKey,
		Button.OnPress action
	) {
		Button button = Button.builder(Component.translatable(labelKey), action)
			.bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
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
