package dev.hydro.hydrosrecipebook;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.ArrayList;
import java.util.List;

public final class LocalRecipePlacer {
	private static final int CLICKS_PER_TICK = 8;
	private static final int RESULT_TIMEOUT_TICKS = 40;
	private static RecipeDisplayId instantRequest;
	private static CraftingJob job;
	private static FurnaceJob furnaceJob;

	private LocalRecipePlacer() {
	}

	public static void cancelAll() {
		instantRequest = null;
		job = null;
		furnaceJob = null;
	}

	public static void requestInstant(RecipeDisplayId recipe) {
		instantRequest = recipe;
	}

	public static boolean start(
		Minecraft minecraft,
		LocalRecipe localRecipe,
		boolean useMaxItems
	) {
		boolean instant = localRecipe.displayEntry().id().equals(instantRequest);
		instantRequest = null;

		if (minecraft.player == null || minecraft.gameMode == null) {
			show(minecraft, "hydros_recipe_book.local.wrong_station");
			return false;
		}
		if (job != null || furnaceJob != null) {
			show(minecraft, "hydros_recipe_book.local.busy");
			return false;
		}
		if (!minecraft.player.containerMenu.getCarried().isEmpty()) {
			show(minecraft, "hydros_recipe_book.local.cursor_not_empty");
			return false;
		}

		InstantCraftManager.cancel();
		if (minecraft.player.containerMenu instanceof AbstractCraftingMenu craftingMenu
			&& localRecipe.holder().value() instanceof CraftingRecipe craftingRecipe) {
			job = new CraftingJob(
				craftingMenu.containerId,
				craftingRecipe,
				useMaxItems,
				instant
			);
			return true;
		}
		if (minecraft.player.containerMenu instanceof AbstractFurnaceMenu furnaceMenu
			&& localRecipe.holder().value() instanceof AbstractCookingRecipe cookingRecipe) {
			furnaceJob = new FurnaceJob(
				furnaceMenu.containerId,
				cookingRecipe,
				useMaxItems,
				instant
			);
			return true;
		}

		show(minecraft, "hydros_recipe_book.local.wrong_station");
		return false;
	}

	public static void tick(Minecraft minecraft) {
		if ((job != null || furnaceJob != null)
			&& !InventoryActionCoordinator.acquire(
				InventoryActionCoordinator.RECIPE_BOOK
			)) {
			return;
		}
		if (furnaceJob != null) {
			tickFurnace(minecraft);
			return;
		}
		if (job == null || minecraft.player == null || minecraft.gameMode == null) {
			return;
		}

		AbstractContainerMenu currentMenu = minecraft.player.containerMenu;
		if (currentMenu.containerId != job.containerId
			|| !(currentMenu instanceof AbstractCraftingMenu craftingMenu)) {
			cancel(minecraft, "hydros_recipe_book.local.cancelled");
			return;
		}

		for (int clicks = 0; clicks < CLICKS_PER_TICK && job != null; clicks++) {
			if (!advance(minecraft, craftingMenu)) {
				break;
			}
		}
	}

	private static boolean advance(Minecraft minecraft, AbstractCraftingMenu menu) {
		switch (job.phase) {
			case CLEAR_GRID -> {
				for (Slot slot : menu.getInputGridSlots()) {
					if (slot.hasItem()) {
						ItemStack before = slot.getItem().copy();
						click(minecraft, menu, slot, 0, ContainerInput.QUICK_MOVE);
						if (ItemStack.matches(before, slot.getItem())) {
							cancel(minecraft, "hydros_recipe_book.local.could_not_clear");
							return false;
						}
						return true;
					}
				}
				job.phase = Phase.PLAN;
				return true;
			}
			case PLAN -> {
				if (!menu.getCarried().isEmpty()) {
					cancel(minecraft, "hydros_recipe_book.local.cursor_not_empty");
					return false;
				}
				if (!buildPlan(minecraft, menu)) {
					cancel(minecraft, "hydros_recipe_book.local.missing_ingredients");
					return false;
				}
				job.phase = Phase.PLACE;
				return true;
			}
			case PLACE -> {
				if (job.taskIndex >= job.tasks.size()) {
					if (!menu.getCarried().isEmpty()) {
						return returnCursor(minecraft, menu);
					}
					job.phase = Phase.WAIT_RESULT;
					job.resultWaitTicks = RESULT_TIMEOUT_TICKS;
					return false;
				}
				return advancePlacement(minecraft, menu);
			}
			case WAIT_RESULT -> {
				ItemStack result = menu.getResultSlot().getItem();
				if (!result.isEmpty()) {
					if (job.instant) {
						click(
							minecraft,
							menu,
							menu.getResultSlot(),
							0,
							ContainerInput.QUICK_MOVE
						);
					}
					job = null;
					return false;
				}
				job.resultWaitTicks--;
				if (job.resultWaitTicks <= 0) {
					cancel(minecraft, "hydros_recipe_book.local.server_rejected");
				}
				return false;
			}
		}
		return false;
	}

	private static void tickFurnace(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.gameMode == null) {
			return;
		}
		AbstractContainerMenu currentMenu = minecraft.player.containerMenu;
		if (currentMenu.containerId != furnaceJob.containerId
			|| !(currentMenu instanceof AbstractFurnaceMenu furnaceMenu)) {
			cancelFurnace(minecraft, "hydros_recipe_book.local.cancelled");
			return;
		}

		for (int clicks = 0; clicks < CLICKS_PER_TICK && furnaceJob != null; clicks++) {
			if (!advanceFurnace(minecraft, furnaceMenu)) {
				break;
			}
		}
	}

	private static boolean advanceFurnace(Minecraft minecraft, AbstractFurnaceMenu menu) {
		Slot input = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT);
		switch (furnaceJob.phase) {
			case CLEAR_INPUT -> {
				if (input.hasItem()) {
					ItemStack before = input.getItem().copy();
					click(minecraft, menu, input, 0, ContainerInput.QUICK_MOVE);
					if (ItemStack.matches(before, input.getItem())) {
						cancelFurnace(
							minecraft,
							"hydros_recipe_book.local.could_not_clear"
						);
						return false;
					}
					return true;
				}
				furnaceJob.phase = FurnacePhase.PLAN;
				return true;
			}
			case PLAN -> {
				StackedItemContents contents = inventoryContents(minecraft, menu);
				List<Holder<Item>> choices = new ArrayList<>();
				int count = furnaceJob.useMaxItems
					? contents.getBiggestCraftableStack(
						furnaceJob.recipe,
						input.getMaxStackSize(),
						choices::add
					)
					: (contents.canCraft(furnaceJob.recipe, 1, choices::add) ? 1 : 0);
				if (count <= 0 || choices.isEmpty()) {
					cancelFurnace(
						minecraft,
						"hydros_recipe_book.local.missing_ingredients"
					);
					return false;
				}
				furnaceJob.task = new PlacementTask(
					input,
					choices.getFirst().value(),
					count
				);
				furnaceJob.phase = FurnacePhase.PLACE;
				return true;
			}
			case PLACE -> {
				PlacementTask task = furnaceJob.task;
				ItemStack carried = menu.getCarried();
				if (task.remaining == 0) {
					if (!carried.isEmpty()) {
						return returnFurnaceCursor(minecraft, menu);
					}
					boolean notify = furnaceJob.instant;
					furnaceJob = null;
					if (notify) {
						show(minecraft, "hydros_recipe_book.local.cooking_started");
					}
					return false;
				}
				if (carried.isEmpty()) {
					Slot source = findSource(minecraft, menu, task.item);
					if (source == null) {
						cancelFurnace(
							minecraft,
							"hydros_recipe_book.local.inventory_changed"
						);
						return false;
					}
					furnaceJob.activeSource = source;
					click(minecraft, menu, source, 0, ContainerInput.PICKUP);
					return true;
				}
				if (!carried.is(task.item)) {
					cancelFurnace(
						minecraft,
						"hydros_recipe_book.local.inventory_changed"
					);
					return false;
				}
				if (carried.getCount() <= task.remaining) {
					int moved = carried.getCount();
					click(minecraft, menu, input, 0, ContainerInput.PICKUP);
					task.remaining -= moved;
				} else {
					click(minecraft, menu, input, 1, ContainerInput.PICKUP);
					task.remaining--;
				}
				return true;
			}
		}
		return false;
	}

	private static boolean buildPlan(Minecraft minecraft, AbstractCraftingMenu menu) {
		StackedItemContents contents = inventoryContents(minecraft, menu);

		List<Holder<Item>> choices = new ArrayList<>();
		int count;
		if (job.useMaxItems) {
			count = contents.getBiggestCraftableStack(job.recipe, 64, choices::add);
		} else {
			count = contents.canCraft(job.recipe, 1, choices::add) ? 1 : 0;
		}
		if (count <= 0) {
			return false;
		}

		PlacementInfo placement = job.recipe.placementInfo();
		int gridWidth = menu.getGridWidth();
		int gridHeight = menu.getGridHeight();
		int recipeWidth;
		int recipeHeight;
		if (job.recipe instanceof ShapedRecipe shapedRecipe) {
			recipeWidth = shapedRecipe.getWidth();
			recipeHeight = shapedRecipe.getHeight();
		} else {
			recipeWidth = Math.min(placement.slotsToIngredientIndex().size(), gridWidth);
			recipeHeight = (placement.slotsToIngredientIndex().size() + gridWidth - 1)
				/ gridWidth;
		}
		if (recipeWidth > gridWidth || recipeHeight > gridHeight) {
			return false;
		}

		List<Slot> inputSlots = menu.getInputGridSlots();
		for (int recipeSlot = 0;
				recipeSlot < placement.slotsToIngredientIndex().size();
				recipeSlot++) {
			int ingredientIndex = placement.slotsToIngredientIndex().getInt(recipeSlot);
			if (ingredientIndex < 0) {
				continue;
			}
			int recipeX = recipeSlot % recipeWidth;
			int recipeY = recipeSlot / recipeWidth;
			int targetIndex = recipeY * gridWidth + recipeX;
			if (targetIndex >= inputSlots.size() || ingredientIndex >= choices.size()) {
				return false;
			}
			job.tasks.add(new PlacementTask(
				inputSlots.get(targetIndex),
				choices.get(ingredientIndex).value(),
				count
			));
		}
		return !job.tasks.isEmpty();
	}

	private static boolean advancePlacement(Minecraft minecraft, AbstractCraftingMenu menu) {
		PlacementTask task = job.tasks.get(job.taskIndex);
		ItemStack carried = menu.getCarried();

		if (task.remaining == 0) {
			if (!carried.isEmpty()) {
				return returnCursor(minecraft, menu);
			}
			job.taskIndex++;
			job.activeSource = null;
			return true;
		}

		if (carried.isEmpty()) {
			Slot source = findSource(minecraft, menu, task.item);
			if (source == null) {
				cancel(minecraft, "hydros_recipe_book.local.inventory_changed");
				return false;
			}
			job.activeSource = source;
			click(minecraft, menu, source, 0, ContainerInput.PICKUP);
			return true;
		}

		if (!carried.is(task.item)) {
			cancel(minecraft, "hydros_recipe_book.local.inventory_changed");
			return false;
		}

		int targetCapacity = task.target.getMaxStackSize(carried)
			- task.target.getItem().getCount();
		if (carried.getCount() <= task.remaining && carried.getCount() <= targetCapacity) {
			int moved = carried.getCount();
			click(minecraft, menu, task.target, 0, ContainerInput.PICKUP);
			task.remaining -= moved;
		} else {
			click(minecraft, menu, task.target, 1, ContainerInput.PICKUP);
			task.remaining--;
		}
		return true;
	}

	private static boolean returnCursor(Minecraft minecraft, AbstractCraftingMenu menu) {
		if (job.activeSource == null) {
			cancel(minecraft, "hydros_recipe_book.local.inventory_changed");
			return false;
		}
		click(minecraft, menu, job.activeSource, 0, ContainerInput.PICKUP);
		if (!menu.getCarried().isEmpty()) {
			cancel(minecraft, "hydros_recipe_book.local.inventory_changed");
			return false;
		}
		job.activeSource = null;
		return true;
	}

	private static boolean returnFurnaceCursor(
		Minecraft minecraft,
		AbstractFurnaceMenu menu
	) {
		if (furnaceJob.activeSource == null) {
			cancelFurnace(minecraft, "hydros_recipe_book.local.inventory_changed");
			return false;
		}
		click(
			minecraft,
			menu,
			furnaceJob.activeSource,
			0,
			ContainerInput.PICKUP
		);
		if (!menu.getCarried().isEmpty()) {
			cancelFurnace(minecraft, "hydros_recipe_book.local.inventory_changed");
			return false;
		}
		furnaceJob.activeSource = null;
		return true;
	}

	private static StackedItemContents inventoryContents(
		Minecraft minecraft,
		AbstractContainerMenu menu
	) {
		StackedItemContents contents = new StackedItemContents();
		for (Slot slot : menu.slots) {
			if (slot.container == minecraft.player.getInventory()
				&& slot.hasItem()
				&& !SlotLockClient.isLocked(slot)) {
				contents.accountStack(slot.getItem());
			}
		}
		return contents;
	}

	private static Slot findSource(
		Minecraft minecraft,
		AbstractContainerMenu menu,
		Item item
	) {
		for (Slot slot : menu.slots) {
			if (slot.container == minecraft.player.getInventory()
				&& slot.hasItem()
				&& !SlotLockClient.isLocked(slot)
				&& slot.getItem().is(item)) {
				return slot;
			}
		}
		return null;
	}

	private static void click(
		Minecraft minecraft,
		AbstractContainerMenu menu,
		Slot slot,
		int button,
		ContainerInput input
	) {
		int slotId = menu.slots.indexOf(slot);
		if (slotId >= 0) {
			minecraft.gameMode.handleContainerInput(
				menu.containerId,
				slotId,
				button,
				input,
				minecraft.player
			);
		}
	}

	private static void cancel(Minecraft minecraft, String translationKey) {
		job = null;
		show(minecraft, translationKey);
	}

	private static void cancelFurnace(Minecraft minecraft, String translationKey) {
		furnaceJob = null;
		show(minecraft, translationKey);
	}

	private static void show(Minecraft minecraft, String translationKey) {
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(Component.translatable(translationKey));
		}
	}

	private enum Phase {
		CLEAR_GRID,
		PLAN,
		PLACE,
		WAIT_RESULT
	}

	private enum FurnacePhase {
		CLEAR_INPUT,
		PLAN,
		PLACE
	}

	private static final class CraftingJob {
		private final int containerId;
		private final CraftingRecipe recipe;
		private final boolean useMaxItems;
		private final boolean instant;
		private final List<PlacementTask> tasks = new ArrayList<>();
		private Phase phase = Phase.CLEAR_GRID;
		private int taskIndex;
		private Slot activeSource;
		private int resultWaitTicks;

		private CraftingJob(
			int containerId,
			CraftingRecipe recipe,
			boolean useMaxItems,
			boolean instant
		) {
			this.containerId = containerId;
			this.recipe = recipe;
			this.useMaxItems = useMaxItems;
			this.instant = instant;
		}
	}

	private static final class FurnaceJob {
		private final int containerId;
		private final AbstractCookingRecipe recipe;
		private final boolean useMaxItems;
		private final boolean instant;
		private FurnacePhase phase = FurnacePhase.CLEAR_INPUT;
		private PlacementTask task;
		private Slot activeSource;

		private FurnaceJob(
			int containerId,
			AbstractCookingRecipe recipe,
			boolean useMaxItems,
			boolean instant
		) {
			this.containerId = containerId;
			this.recipe = recipe;
			this.useMaxItems = useMaxItems;
			this.instant = instant;
		}
	}

	private static final class PlacementTask {
		private final Slot target;
		private final Item item;
		private int remaining;

		private PlacementTask(Slot target, Item item, int remaining) {
			this.target = target;
			this.item = item;
			this.remaining = remaining;
		}
	}
}
