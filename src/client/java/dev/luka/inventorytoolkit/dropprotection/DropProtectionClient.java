package dev.luka.inventorytoolkit.dropprotection;

import dev.luka.inventorytoolkit.ScreenMessages;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashSet;
import java.util.Set;

/**
 * Stops the player from dropping valuable items by accident: with Q in the
 * world, with Q over a slot, or by clicking outside a container screen while
 * holding an item. Depending on the mode, a protected drop either goes
 * through when it is repeated within a few seconds or is refused outright.
 * Inventory Sweeper never drops protected items either.
 */
public final class DropProtectionClient {
	private static final long CONFIRM_MILLIS = 3000;
	private static final int OUTSIDE_SLOT_ID = -999;

	private static DropProtectionConfig config;
	private static Set<String> protectedItems = Set.of();
	private static ItemStack confirmedStack = ItemStack.EMPTY;
	private static long confirmedUntil;

	private DropProtectionClient() {
	}

	public static void initialize() {
		config = DropProtectionConfig.load();
		onConfigChanged();
	}

	public static DropProtectionConfig config() {
		return config;
	}

	public static void onConfigChanged() {
		config.save();
		Set<String> ids = new HashSet<>();
		for (String id : config.protectedItems) {
			ids.add(id.trim());
		}
		protectedItems = Set.copyOf(ids);
		confirmedStack = ItemStack.EMPTY;
	}

	/**
	 * Whether the stack itself is protected, regardless of the slot it is in.
	 */
	public static boolean isProtected(ItemStack stack) {
		return reason(stack, false) != null;
	}

	/**
	 * Called before a drop with the drop key in the world.
	 *
	 * @return whether the drop may go ahead
	 */
	public static boolean allowWorldDrop(Minecraft client) {
		if (client.player == null) {
			return true;
		}
		int selected = client.player.getInventory().getSelectedSlot();
		return allowDrop(client, client.player.getInventory().getSelectedItem(), SlotLockClient.isLocked(selected));
	}

	/**
	 * Called before a container screen sends a click.
	 *
	 * @return whether the click may go ahead
	 */
	public static boolean allowScreenClick(
		Minecraft client,
		AbstractContainerMenu menu,
		Slot slot,
		int slotId,
		ContainerInput input
	) {
		if (input == ContainerInput.THROW && slot != null && slot.hasItem()) {
			return allowDrop(client, slot.getItem(), SlotLockClient.isLocked(slot));
		}
		// Releasing a carried stack outside the screen drops it.
		if (slot == null
			&& slotId == OUTSIDE_SLOT_ID
			&& (input == ContainerInput.PICKUP || input == ContainerInput.THROW)) {
			return allowDrop(client, menu.getCarried(), false);
		}
		return true;
	}

	private static boolean allowDrop(Minecraft client, ItemStack stack, boolean fromLockedSlot) {
		if (stack.isEmpty()) {
			return true;
		}
		Component reason = reason(stack, fromLockedSlot);
		if (reason == null) {
			return true;
		}

		long now = Util.getMillis();
		Component name = stack.getHoverName().copy().withStyle(stack.getRarity().color());
		if (config.mode == DropProtectionConfig.Mode.BLOCK) {
			ScreenMessages.show(client, Component.translatable("message.inventory_toolkit.drop_protection.blocked", name, reason));
			return false;
		}
		// Every drop of the confirmed item keeps the confirmation alive, so a
		// held or repeated drop key keeps working once confirmed.
		if (now < confirmedUntil && ItemStack.isSameItemSameComponents(confirmedStack, stack)) {
			confirmedUntil = now + CONFIRM_MILLIS;
			return true;
		}
		confirmedStack = stack.copyWithCount(1);
		confirmedUntil = now + CONFIRM_MILLIS;
		ScreenMessages.show(client, Component.translatable("message.inventory_toolkit.drop_protection.confirm", name, reason));
		return false;
	}

	/**
	 * @return why the stack is protected, or null when it is not
	 */
	private static Component reason(ItemStack stack, boolean fromLockedSlot) {
		if (config == null || !config.enabled || stack.isEmpty()) {
			return null;
		}
		if (fromLockedSlot && config.protectLockedSlots) {
			return reasonText("locked_slot");
		}
		if (protectedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
			return reasonText("listed");
		}
		if (config.protectNamed && stack.has(DataComponents.CUSTOM_NAME)) {
			return reasonText("named");
		}
		if (config.protectEnchanted
			&& (stack.isEnchanted()
				|| !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty())) {
			return reasonText("enchanted");
		}
		if (config.protectFilledContainers && holdsItems(stack)) {
			return reasonText("filled");
		}
		if (config.rarity != DropProtectionConfig.RarityThreshold.OFF
			&& stack.getRarity().ordinal() >= config.rarity.ordinal()) {
			return reasonText("rarity");
		}
		return null;
	}

	private static boolean holdsItems(ItemStack stack) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null && contents.nonEmptyItems().iterator().hasNext()) {
			return true;
		}
		return !stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty();
	}

	private static Component reasonText(String key) {
		return Component.translatable("message.inventory_toolkit.drop_protection.reason." + key)
			.withStyle(ChatFormatting.GRAY);
	}
}
