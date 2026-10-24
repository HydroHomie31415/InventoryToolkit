package dev.luka.inventorytoolkit.slotlock;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Arrays;

/**
 * Locked player inventory slots are left alone by every toolkit module that
 * moves items on its own: quick stack, sorting, refills, loadouts, bundle
 * routing, compacting, sweeping, recipe placement, and drag or scroll
 * transfers. Shift-clicking a locked slot can be blocked too. Locks belong to
 * slot positions, not items, so they apply in every world.
 */
public final class SlotLockClient {
	private static final int SHACKLE = 0xFFB4B4B4;
	private static final int BODY = 0xFFE8B030;
	private static final int OUTLINE = 0xFF3C2800;

	private static SlotLockConfig config;
	private static KeyMapping lockKey;
	private static final boolean[] locked = new boolean[Inventory.INVENTORY_SIZE];

	private SlotLockClient() {
	}

	public static void initialize() {
		config = SlotLockConfig.load();
		reloadLocks();
		lockKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.inventory_toolkit.slot_lock",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_N,
			InventoryToolkitClient.keyCategory()
		));

		// Keyboard presses are handled in ContainerScreenMixin, where vanilla
		// handles its own slot keys, so typing in a text field never toggles a
		// lock. The key may also be bound to a mouse button.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				ScreenMouseEvents.allowMouseClick(containerScreen).register((ignored, event) ->
					!lockKey.matchesMouse(event)
						|| !toggle(((AbstractContainerScreenAccessor) containerScreen).inventoryToolkit$hoveredSlot())
				);
			}
		});
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.HOTBAR,
			Identifier.fromNamespaceAndPath(InventoryToolkitClient.MOD_ID, "slot_locks"),
			SlotLockClient::extractHotbarLocks
		);
	}

	public static SlotLockConfig config() {
		return config;
	}

	public static KeyMapping lockKey() {
		return lockKey;
	}

	public static void onConfigChanged() {
		config.save();
		reloadLocks();
	}

	/**
	 * @param inventorySlot a player inventory index
	 */
	public static boolean isLocked(int inventorySlot) {
		return config.enabled
			&& inventorySlot >= 0
			&& inventorySlot < locked.length
			&& locked[inventorySlot];
	}

	/**
	 * @return whether the slot shows a player inventory slot that is locked
	 */
	public static boolean isLocked(Slot slot) {
		return slot != null && slot.container instanceof Inventory && isLocked(slot.getContainerSlot());
	}

	/**
	 * Whether a shift-click on this slot should be ignored.
	 */
	public static boolean blocksQuickMove(Slot slot) {
		return config.blockQuickMove && isLocked(slot);
	}

	/**
	 * Called from a container screen's key handling.
	 *
	 * @return whether the key toggled the hovered slot's lock
	 */
	public static boolean onKeyPressed(KeyEvent event, Slot hoveredSlot) {
		return lockKey.matches(event) && toggle(hoveredSlot);
	}

	private static boolean toggle(Slot slot) {
		if (!config.enabled
			|| slot == null
			|| !(slot.container instanceof Inventory)
			|| slot.getContainerSlot() < 0
			|| slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) {
			return false;
		}
		int inventorySlot = slot.getContainerSlot();
		if (locked[inventorySlot]) {
			config.lockedSlots.remove(Integer.valueOf(inventorySlot));
		} else {
			config.lockedSlots.add(inventorySlot);
		}
		onConfigChanged();
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
			SoundEvents.UI_BUTTON_CLICK,
			locked[inventorySlot] ? 1.2F : 0.8F
		));
		return true;
	}

	private static void reloadLocks() {
		Arrays.fill(locked, false);
		for (int inventorySlot : config.lockedSlots) {
			locked[inventorySlot] = true;
		}
	}

	private static void extractHotbarLocks(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!config.enabled || !config.showOnHotbar || client.player == null || client.player.isSpectator()) {
			return;
		}
		int screenCenter = graphics.guiWidth() / 2;
		for (int hotbarSlot = 0; hotbarSlot < Inventory.SELECTION_SIZE; hotbarSlot++) {
			if (locked[hotbarSlot]) {
				// Matches the item positions in Hud#extractItemHotbar.
				extractIcon(graphics, screenCenter - 90 + hotbarSlot * 20 + 2, graphics.guiHeight() - 16 - 3);
			}
		}
	}

	/**
	 * Draws a small padlock in the top-left corner of the 16×16 item area at
	 * the given position.
	 */
	public static void extractIcon(GuiGraphicsExtractor graphics, int slotX, int slotY) {
		int x = slotX - 1;
		int y = slotY - 1;
		// Shackle
		graphics.fill(x + 1, y, x + 6, y + 1, OUTLINE);
		graphics.fill(x + 1, y + 1, x + 2, y + 4, OUTLINE);
		graphics.fill(x + 5, y + 1, x + 6, y + 4, OUTLINE);
		graphics.fill(x + 2, y + 1, x + 5, y + 2, SHACKLE);
		graphics.fill(x + 2, y + 2, x + 3, y + 4, SHACKLE);
		graphics.fill(x + 4, y + 2, x + 5, y + 4, SHACKLE);
		// Body with a keyhole
		graphics.fill(x, y + 4, x + 7, y + 9, OUTLINE);
		graphics.fill(x + 1, y + 5, x + 6, y + 8, BODY);
		graphics.fill(x + 3, y + 6, x + 4, y + 7, OUTLINE);
	}
}
