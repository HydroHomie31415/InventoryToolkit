package dev.luka.inventorytoolkit.shulkerpreview;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/**
 * Replaces the "5 items and 3 more" text of shulker boxes, and of any other
 * item that stores container contents, with a grid of the stored stacks.
 */
public final class ShulkerPreviewClient {
	private static final int COLUMNS = 9;
	private static final int SHULKER_BOX_SLOTS = 27;

	private static ShulkerPreviewConfig config;

	private ShulkerPreviewClient() {
	}

	public static void initialize() {
		config = ShulkerPreviewConfig.load();
		ClientTooltipComponentCallback.EVENT.register(component ->
			component instanceof ContainerPreview preview ? new ClientContainerPreview(preview) : null
		);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (config.enabled && config.requireShift && !isShown() && holdsItems(stack)) {
				lines.add(Component.translatable("tooltip.inventory_toolkit.shulker_preview.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}

	public static ShulkerPreviewConfig config() {
		return config;
	}

	/**
	 * Called for every tooltip image request.
	 *
	 * @return the contents grid, or null to keep the item's own tooltip image
	 */
	public static TooltipComponent previewFor(ItemStack stack) {
		if (config == null || !isShown() || !holdsItems(stack)) {
			return null;
		}
		List<ItemStack> items = stack.get(DataComponents.CONTAINER).itemCopies().toList();
		DyeColor color = null;
		int slots = items.size();
		if (Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock shulkerBox) {
			color = shulkerBox.getColor();
			slots = Math.max(slots, SHULKER_BOX_SLOTS);
		}
		slots = (slots + COLUMNS - 1) / COLUMNS * COLUMNS;
		int tint = color != null && config.colorBackground ? color.getTextureDiffuseColor() & 0xFFFFFF : -1;
		return new ContainerPreview(items, slots, tint);
	}

	/**
	 * Whether the vanilla list of stored items should be left out, because
	 * the grid shows them.
	 */
	public static boolean hidesItemList() {
		return config != null && config.hideItemList && isShown();
	}

	private static boolean isShown() {
		return config.enabled && (!config.requireShift || Minecraft.getInstance().hasShiftDown());
	}

	private static boolean holdsItems(ItemStack stack) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		return contents != null && contents.nonEmptyItems().iterator().hasNext();
	}
}
