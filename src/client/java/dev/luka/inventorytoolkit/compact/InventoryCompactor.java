package dev.luka.inventorytoolkit.compact;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Crafts every "nine of one item" stack in the player's inventory into its
 * block in an open crafting table. Each step asks the server to fill the grid
 * as a recipe-book click with Shift held would, waits for the result, and
 * shift-clicks it out. Steps repeat until nothing more can be compacted, so
 * nuggets become ingots and then blocks.
 */
public final class InventoryCompactor {
	private static final int RESPONSE_TIMEOUT_TICKS = 40;
	private static final int MAX_STEPS = 256;
	private static final int ITEMS_PER_BLOCK = 9;

	private enum Phase {
		PLACE,
		WAIT_RESULT,
		WAIT_CRAFT,
		CLEAR_GRID
	}

	private final Minecraft client;
	private final Set<RecipeDisplayId> failed = new HashSet<>();
	private final Map<Item, Integer> crafted = new LinkedHashMap<>();
	private List<CompactRecipes.CompactRecipe> recipes = List.of();
	private CompactRecipes.CompactRecipe current;
	private Phase phase = Phase.PLACE;
	private boolean running;
	private int containerId;
	private int stepsLeft;
	private int resultCountBefore;
	private int stateIdBeforeCraft;
	private long tickCounter;
	private long deadlineTick;

	public InventoryCompactor(Minecraft client) {
		this.client = client;
	}

	public boolean isRunning() {
		return running;
	}

	public void start(CompactConfig config) {
		LocalPlayer player = client.player;
		if (running
			|| !config.enabled
			|| player == null
			|| client.level == null
			|| client.gameMode == null
			|| !(player.containerMenu instanceof CraftingMenu menu)) {
			return;
		}
		if (!menu.getCarried().isEmpty()) {
			notifyPlayer(config, Component.translatable("message.inventory_toolkit.compact.cursor_not_empty"));
			return;
		}

		recipes = CompactRecipes.find(client, config);
		failed.clear();
		crafted.clear();
		current = null;
		containerId = menu.containerId;
		stepsLeft = MAX_STEPS;
		phase = Phase.PLACE;
		running = true;
	}

	public void cancel() {
		running = false;
		current = null;
	}

	public void claimTick() {
		if (running) {
			InventoryActionCoordinator.claimManual(InventoryActionCoordinator.COMPACT);
		}
	}

	public void tick(CompactConfig config) {
		tickCounter++;
		if (!running) {
			return;
		}
		LocalPlayer player = client.player;
		if (!config.enabled
			|| player == null
			|| client.gameMode == null
			|| !(player.containerMenu instanceof CraftingMenu menu)
			|| menu.containerId != containerId) {
			cancel();
			return;
		}
		claimTick();

		switch (phase) {
			case PLACE -> place(menu);
			case WAIT_RESULT -> waitForResult(menu);
			case WAIT_CRAFT -> waitForCraft(menu);
			case CLEAR_GRID -> clearGrid(menu, config);
		}
	}

	private void place(CraftingMenu menu) {
		current = nextRecipe();
		if (current == null || stepsLeft-- <= 0) {
			phase = Phase.CLEAR_GRID;
			return;
		}
		resultCountBefore = countInInventory(current.result());
		// The server moves whatever is in the grid back into the inventory first.
		client.gameMode.handlePlaceRecipe(menu.containerId, current.id(), true);
		phase = Phase.WAIT_RESULT;
		deadlineTick = tickCounter + RESPONSE_TIMEOUT_TICKS;
	}

	private void waitForResult(CraftingMenu menu) {
		Slot resultSlot = menu.getResultSlot();
		if (resultSlot.getItem().is(current.result())) {
			stateIdBeforeCraft = menu.getStateId();
			click(menu, resultSlot);
			phase = Phase.WAIT_CRAFT;
			deadlineTick = tickCounter + RESPONSE_TIMEOUT_TICKS;
		} else if (tickCounter >= deadlineTick) {
			// The server rejected the placement (locked recipe, limited crafting).
			failed.add(current.id());
			phase = Phase.PLACE;
		}
	}

	private void waitForCraft(CraftingMenu menu) {
		// The server answers the craft with slot updates that change the state ID.
		if (menu.getStateId() == stateIdBeforeCraft && tickCounter < deadlineTick) {
			return;
		}
		int made = countInInventory(current.result()) - resultCountBefore;
		if (made > 0) {
			crafted.merge(current.result(), made, Integer::sum);
		} else {
			failed.add(current.id());
		}
		phase = Phase.PLACE;
	}

	/**
	 * Returns leftovers to the inventory, for example when it filled up before
	 * every block could be taken out.
	 */
	private void clearGrid(CraftingMenu menu, CompactConfig config) {
		for (Slot slot : menu.getInputGridSlots()) {
			if (slot.hasItem()) {
				click(menu, slot);
			}
		}
		finish(config);
	}

	/**
	 * The server takes ingredients from any slot, so materials that are also
	 * in a locked slot are never compacted.
	 */
	private CompactRecipes.CompactRecipe nextRecipe() {
		for (CompactRecipes.CompactRecipe recipe : recipes) {
			if (!failed.contains(recipe.id())
				&& countInInventory(recipe.ingredient()) >= ITEMS_PER_BLOCK
				&& !isInLockedSlot(recipe.ingredient())) {
				return recipe;
			}
		}
		return null;
	}

	private boolean isInLockedSlot(Item item) {
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (SlotLockClient.isLocked(slot) && inventory.getItem(slot).is(item)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Counts plain stacks only; the server never uses renamed, enchanted, or
	 * damaged items for recipe-book placement either.
	 */
	private int countInInventory(Item item) {
		Inventory inventory = client.player.getInventory();
		int count = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private void click(CraftingMenu menu, Slot slot) {
		client.gameMode.handleContainerInput(
			menu.containerId,
			slot.index,
			0,
			ContainerInput.QUICK_MOVE,
			client.player
		);
	}

	private void finish(CompactConfig config) {
		running = false;
		current = null;
		if (crafted.isEmpty()) {
			notifyPlayer(config, Component.translatable("message.inventory_toolkit.compact.nothing"));
			return;
		}
		MutableComponent summary = Component.empty();
		crafted.forEach((item, count) -> {
			if (!summary.getSiblings().isEmpty()) {
				summary.append(", ");
			}
			summary.append(Component.translatable(
				"message.inventory_toolkit.compact.entry",
				count,
				new ItemStack(item).getHoverName()
			));
		});
		notifyPlayer(config, Component.translatable("message.inventory_toolkit.compact.finished", summary));
	}

	private void notifyPlayer(CompactConfig config, Component message) {
		if (config.showMessages && client.player != null) {
			client.gui.hud.setOverlayMessage(message, false);
		}
	}
}
