package dev.luka.inventorytoolkit.shulkerpreview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a container item's contents as a grid of inventory slots.
 */
final class ClientContainerPreview implements ClientTooltipComponent {
	private static final int COLUMNS = 9;
	private static final int SLOT_SIZE = 18;
	private static final int BORDER = 1;
	private static final int MARGIN_Y = 2;
	private static final int PANEL = 0xC6C6C6;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0xFF8B8B8B;

	private final ContainerPreview preview;

	ClientContainerPreview(ContainerPreview preview) {
		this.preview = preview;
	}

	private int rows() {
		return preview.slots() / COLUMNS;
	}

	@Override
	public int getHeight(Font font) {
		return rows() * SLOT_SIZE + BORDER * 2 + MARGIN_Y * 2;
	}

	@Override
	public int getWidth(Font font) {
		return COLUMNS * SLOT_SIZE + BORDER * 2;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
		int top = y + MARGIN_Y;
		int panel = preview.tint() < 0 ? PANEL : blend(preview.tint(), PANEL);
		graphics.fill(x, top, x + getWidth(font), top + rows() * SLOT_SIZE + BORDER * 2, 0xFF000000 | panel);

		for (int index = 0; index < preview.slots(); index++) {
			int slotX = x + BORDER + (index % COLUMNS) * SLOT_SIZE;
			int slotY = top + BORDER + (index / COLUMNS) * SLOT_SIZE;
			graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_LIGHT);
			graphics.fill(slotX, slotY, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, SLOT_SHADOW);
			graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, SLOT_FILL);

			if (index < preview.items().size()) {
				ItemStack stack = preview.items().get(index);
				if (!stack.isEmpty()) {
					graphics.item(stack, slotX + 1, slotY + 1, index);
					graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
				}
			}
		}
	}

	/**
	 * Mixes the dye color halfway into the vanilla panel grey so dark dyes
	 * stay readable.
	 */
	private static int blend(int color, int base) {
		int red = (((color >> 16) & 0xFF) + ((base >> 16) & 0xFF)) / 2;
		int green = (((color >> 8) & 0xFF) + ((base >> 8) & 0xFF)) / 2;
		int blue = ((color & 0xFF) + (base & 0xFF)) / 2;
		return red << 16 | green << 8 | blue;
	}
}
