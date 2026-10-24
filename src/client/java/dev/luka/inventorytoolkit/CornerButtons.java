package dev.luka.inventorytoolkit;

import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Small buttons in the top-right corner of a container screen, shared by the
 * modules that add one.
 */
public final class CornerButtons {
	private static final int BUTTON_WIDTH = 14;
	private static final int BUTTON_HEIGHT = 12;
	private static final int BUTTON_INSET_X = 7;
	private static final int BUTTON_INSET_Y = 4;
	private static final int BUTTON_GAP = 2;

	/**
	 * Label and tooltip that temporarily replace a button's own, for example
	 * a cancel cross while the button's action runs.
	 */
	public record State(String labelKey, String tooltipKey) {
		public static final State CANCEL = new State(
			"button.inventory_toolkit.cancel",
			"button.inventory_toolkit.cancel.tooltip"
		);
	}

	private CornerButtons() {
	}

	/**
	 * @param indexFromRight 0 for the corner itself, 1 for the slot to its left, and so on
	 */
	public static Button add(
		AbstractContainerScreen<?> screen,
		int indexFromRight,
		String labelKey,
		String tooltipKey,
		Runnable action
	) {
		return add(screen, indexFromRight, labelKey, tooltipKey, action, () -> null);
	}

	/**
	 * @param state checked every frame; a non-null state replaces the label and tooltip
	 */
	public static Button add(
		AbstractContainerScreen<?> screen,
		int indexFromRight,
		String labelKey,
		String tooltipKey,
		Runnable action,
		Supplier<State> state
	) {
		AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
		State normal = new State(labelKey, tooltipKey);
		Button button = Button.builder(
			Component.translatable(labelKey),
			ignored -> action.run()
		).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
		button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		Screens.getWidgets(screen).add(button);

		State[] shown = {normal};
		Runnable placeButton = () -> {
			button.setPosition(
				bounds.inventoryToolkit$leftPos() + bounds.inventoryToolkit$imageWidth()
					- BUTTON_INSET_X - BUTTON_WIDTH - indexFromRight * (BUTTON_WIDTH + BUTTON_GAP),
				bounds.inventoryToolkit$topPos() + BUTTON_INSET_Y
			);
			State current = state.get();
			State wanted = current == null ? normal : current;
			if (!wanted.equals(shown[0])) {
				shown[0] = wanted;
				button.setMessage(Component.translatable(wanted.labelKey()));
				button.setTooltip(Tooltip.create(Component.translatable(wanted.tooltipKey())));
			}
		};
		placeButton.run();
		// Opening the recipe book moves the inventory sideways without
		// re-initialising the screen, so follow it every frame.
		ScreenEvents.beforeExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
			placeButton.run()
		);
		return button;
	}
}
