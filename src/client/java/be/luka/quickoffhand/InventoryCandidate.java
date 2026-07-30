package be.luka.quickoffhand;

import net.minecraft.world.item.ItemStack;

public record InventoryCandidate(int inventorySlot, String itemId, ItemStack displayStack) {
}
