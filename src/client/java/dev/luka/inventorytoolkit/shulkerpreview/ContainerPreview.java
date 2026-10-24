package dev.luka.inventorytoolkit.shulkerpreview;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The tooltip image of a container item.
 *
 * @param items the stored stacks by slot, with empty stacks for empty slots
 * @param slots the number of slots to draw, a multiple of nine
 * @param tint the background color as 0xRRGGBB, or -1 for the plain grey
 */
public record ContainerPreview(List<ItemStack> items, int slots, int tint) implements TooltipComponent {
}
