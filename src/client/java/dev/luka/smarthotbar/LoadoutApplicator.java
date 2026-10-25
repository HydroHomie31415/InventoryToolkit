package dev.luka.smarthotbar;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

public final class LoadoutApplicator {
    private LoadoutApplicator() {
    }

    public static void apply(Minecraft client, LoadoutConfig config) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            return;
        }
        if (client.gui.screen() != null && !(client.gui.screen() instanceof InventoryScreen)) {
            return;
        }
        if (!config.isEnabled()) {
            client.gui.hud.setOverlayMessage(Component.translatable("message.smart_hotbar_loadouts.disabled"), false);
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            client.gui.hud.setOverlayMessage(
                    Component.translatable("message.smart_hotbar_loadouts.container_open"), false);
            return;
        }

        Inventory inventory = player.getInventory();
        Set<Integer> lockedSlots = new HashSet<>();
        int moved = 0;

        for (int hotbarSlot = 0; hotbarSlot < Inventory.getSelectionSize(); hotbarSlot++) {
            LoadoutRule rule = config.get(hotbarSlot);
            if (rule == LoadoutRule.NONE || SlotLockClient.isLocked(hotbarSlot)) {
                continue;
            }

            int source = findBest(inventory, rule, lockedSlots, config);
            if (source < 0) {
                continue;
            }

            if (source != hotbarSlot) {
                if (!InventoryActionCoordinator.acquire(InventoryActionCoordinator.LOADOUTS)) {
                    client.gui.hud.setOverlayMessage(
                            Component.translatable("message.inventory_toolkit.busy"), false);
                    return;
                }
                client.gameMode.handleContainerInput(
                        player.inventoryMenu.containerId,
                        inventoryIndexToMenuSlot(source),
                        hotbarSlot,
                        ContainerInput.SWAP,
                        player
                );
                moved++;
            }
            lockedSlots.add(hotbarSlot);
        }

        client.gui.hud.setOverlayMessage(Component.translatable(
                moved == 0 ? "message.smart_hotbar_loadouts.up_to_date" : "message.smart_hotbar_loadouts.applied",
                config.presetName()
        ), false);
    }

    private static int findBest(Inventory inventory, LoadoutRule rule, Set<Integer> lockedSlots,
                                LoadoutConfig config) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        // Equipment/offhand slots are intentionally excluded; only the 36 normal
        // inventory slots are safe to swap into a hotbar button.
        for (int slot = 0; slot < 36; slot++) {
            if (lockedSlots.contains(slot) || SlotLockClient.isLocked(slot)) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            double score = score(stack, rule, config);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestScore == Double.NEGATIVE_INFINITY ? -1 : bestSlot;
    }

    private static double score(ItemStack stack, LoadoutRule rule, LoadoutConfig config) {
        if (stack.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        return switch (rule) {
            case NONE -> Double.NEGATIVE_INFINITY;
            case BEST_PICKAXE -> stack.is(ItemTags.PICKAXES)
                    ? toolScore(stack, Blocks.STONE.defaultBlockState())
                            + pickaxePreferenceBonus(stack, config.pickaxePreference())
                    : Double.NEGATIVE_INFINITY;
            case BEST_AXE -> stack.is(ItemTags.AXES)
                    ? toolScore(stack, Blocks.OAK_LOG.defaultBlockState())
                    : Double.NEGATIVE_INFINITY;
            case BEST_WEAPON -> stack.has(DataComponents.WEAPON)
                    ? weaponScore(stack)
                    : Double.NEGATIVE_INFINITY;
            case BEST_SHOVEL -> stack.is(ItemTags.SHOVELS)
                    ? toolScore(stack, Blocks.DIRT.defaultBlockState())
                    : Double.NEGATIVE_INFINITY;
            case BEST_HOE -> stack.is(ItemTags.HOES)
                    ? toolScore(stack, Blocks.HAY_BLOCK.defaultBlockState())
                    : Double.NEGATIVE_INFINITY;
            case WATER_BUCKET -> stack.is(Items.WATER_BUCKET) ? 1 : Double.NEGATIVE_INFINITY;
            case BUILDING_BLOCKS -> buildingBlockScore(stack);
            case BEST_FOOD -> foodScore(stack);
        };
    }

    private static double toolScore(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        // Mining speed is primary. Remaining durability only breaks ties, so a
        // slower pristine tool never beats a faster worn tool.
        return stack.getDestroySpeed(state) * 1_000_000.0 + remainingDurability(stack);
    }

    private static double pickaxePreferenceBonus(ItemStack stack, PickaxePreference preference) {
        if (preference == PickaxePreference.NEUTRAL) {
            return 0;
        }
        boolean preferred = stack.getEnchantments().entrySet().stream().anyMatch(entry ->
                entry.getKey().is(preference == PickaxePreference.SILK_TOUCH
                        ? Enchantments.SILK_TOUCH
                        : Enchantments.FORTUNE));
        // Large enough to make the chosen enchantment preference decisive while
        // leaving normal mining-speed scoring intact when none match.
        return preferred ? 1_000_000_000.0 : 0;
    }

    private static double weaponScore(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double damage = modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
        return damage * 1_000_000.0 + remainingDurability(stack);
    }

    private static double buildingBlockScore(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !blockItem.getBlock().defaultBlockState().canOcclude()) {
            return Double.NEGATIVE_INFINITY;
        }
        // Prefer a larger stack, then keep the existing hotbar order stable.
        return stack.getCount();
    }

    private static double foodScore(ItemStack stack) {
        if (stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.is(Items.SUSPICIOUS_STEW)) {
            return Double.NEGATIVE_INFINITY;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return Double.NEGATIVE_INFINITY;
        }
        // Saturation is the requested priority; nutrition and stack count provide
        // deterministic tie-breakers.
        return food.saturation() * 1_000_000.0 + food.nutrition() * 1_000.0 + stack.getCount();
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
    }

    private static int inventoryIndexToMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }
}
