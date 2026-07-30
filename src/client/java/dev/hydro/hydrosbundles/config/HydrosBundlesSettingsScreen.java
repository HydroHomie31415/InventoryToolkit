package dev.hydro.hydrosbundles.config;

import dev.hydro.hydrosbundles.screen.BundleOverviewScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HydrosBundlesSettingsScreen extends Screen {
	private final Screen parent;

	public HydrosBundlesSettingsScreen(Screen parent) {
		super(Component.translatable("config.hydros_bundles.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int buttonWidth = Math.min(250, width - 30);
		int left = (width - buttonWidth) / 2;
		int top = height / 2 - 55;

		addRenderableWidget(Button.builder(
			Component.translatable("config.hydros_bundles.visual_routing"),
			button -> minecraft.gui.setScreen(new VisualRoutingEditorScreen(this))
		).bounds(left, top, buttonWidth, 24).build());
		addRenderableWidget(Button.builder(
			Component.translatable("config.hydros_bundles.general_settings"),
			button -> minecraft.gui.setScreen(HydrosBundlesConfigScreen.createAdvanced(this))
		).bounds(left, top + 30, buttonWidth, 20).build());
		if (minecraft.player != null) {
			addRenderableWidget(Button.builder(
				Component.translatable("config.hydros_bundles.open_browser"),
				button -> minecraft.gui.setScreen(new BundleOverviewScreen(this))
			).bounds(left, top + 56, buttonWidth, 20).build());
		}
		addRenderableWidget(Button.builder(
			Component.translatable("screen.hydros_bundles.done"),
			button -> onClose()
		).bounds(left, top + 86, buttonWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.centeredText(font, title, width / 2, height / 2 - 88, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable("config.hydros_bundles.settings_hint"),
			width / 2,
			height / 2 - 72,
			0xFFAAAAAA
		);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}

