package dev.hydro.hydrosbundles.screen;

import dev.hydro.hydrosbundles.BundleInventory;
import dev.hydro.hydrosbundles.HydrosBundlesClient;
import dev.hydro.hydrosbundles.config.HydrosBundlesSettingsScreen;
import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;

public final class BundleOverviewScreen extends Screen {
	private static final int SLOT_SIZE = 18;
	private static final int CONTENT_ROWS = 3;

	private final Screen parent;
	private int selectedBundleIndex = -1;
	private int contentPage;

	public BundleOverviewScreen(Screen parent) {
		super(Component.translatable("screen.hydros_bundles.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		refreshSelectedBundle();
		int center = width / 2;
		addRenderableWidget(Button.builder(Component.literal("◀"), button -> cycleBundle(-1))
			.bounds(center - 130, 25, 28, 20).build());
		addRenderableWidget(Button.builder(Component.literal("▶"), button -> cycleBundle(1))
			.bounds(center - 98, 25, 28, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.hydros_bundles.previous_page"),
				button -> changePage(-1))
			.bounds(center - 66, 25, 44, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.hydros_bundles.next_page"),
				button -> changePage(1))
			.bounds(center - 18, 25, 44, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.hydros_bundles.config"),
				button -> minecraft.gui.setScreen(new HydrosBundlesSettingsScreen(this)))
			.bounds(center + 30, 25, 48, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.hydros_bundles.done"),
				button -> onClose())
			.bounds(center + 82, 25, 48, 20).build());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0 || selectedBundle() == null) {
			return false;
		}

		int contentSlot = slotAt(event.x(), event.y(), contentLeft(), contentTop(), contentColumns(), CONTENT_ROWS);
		if (contentSlot >= 0) {
			int contentIndex = contentPage * pageSize() + contentSlot;
			withdraw(contentIndex);
			return true;
		}

		if (HydrosBundlesClient.config().showPlayerInventory) {
			int displaySlot = slotAt(event.x(), event.y(), inventoryLeft(), inventoryTop(), 9, 4);
			if (displaySlot >= 0) {
				insertFromInventory(displayToInventoryIndex(displaySlot));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= contentLeft() && mouseX < contentLeft() + contentColumns() * SLOT_SIZE
			&& mouseY >= contentTop() && mouseY < contentTop() + CONTENT_ROWS * SLOT_SIZE
			&& verticalAmount != 0.0) {
			changePage(verticalAmount > 0.0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

		ItemStack bundle = selectedBundle();
		if (bundle == null) {
			graphics.centeredText(
				font,
				Component.translatable("screen.hydros_bundles.no_bundles"),
				width / 2,
				height / 2 - 5,
				0xFFFF7777
			);
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			return;
		}

		BundleContents contents = BundleInventory.contents(bundle);
		int percent = Math.round(BundleItem.getFullnessDisplay(bundle) * 100.0F);
		graphics.centeredText(
			font,
			Component.translatable(
				"screen.hydros_bundles.bundle_header",
				bundle.getHoverName(),
				contents.size(),
				percent
			),
			width / 2,
			49,
			0xFFFFFFFF
		);

		renderContents(graphics, contents, mouseX, mouseY);
		if (HydrosBundlesClient.config().showPlayerInventory) {
			renderPlayerInventory(graphics, mouseX, mouseY);
		}
		graphics.centeredText(
			font,
			Component.translatable("screen.hydros_bundles.hint"),
			width / 2,
			122,
			0xFFAAAAAA
		);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (HydrosBundlesClient.config().rememberLastBundle) {
			HydrosBundlesClient.setLastBundleIndex(selectedBundleIndex);
		}
		minecraft.gui.setScreen(parent);
	}

	private void renderContents(
		GuiGraphicsExtractor graphics,
		BundleContents contents,
		int mouseX,
		int mouseY
	) {
		List<ItemStackTemplate> items = contents.items();
		int start = contentPage * pageSize();
		for (int displayIndex = 0; displayIndex < pageSize(); displayIndex++) {
			int x = contentLeft() + displayIndex % contentColumns() * SLOT_SIZE;
			int y = contentTop() + displayIndex / contentColumns() * SLOT_SIZE;
			renderSlotBackground(graphics, x, y, mouseX, mouseY);
			int contentIndex = start + displayIndex;
			if (contentIndex >= items.size()) {
				continue;
			}
			ItemStack stack = items.get(contentIndex).create();
			graphics.item(stack, x + 1, y + 1);
			graphics.itemDecorations(font, stack, x + 1, y + 1);
			if (isInside(mouseX, mouseY, x, y)) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
		}
		int pages = Math.max(1, (items.size() + pageSize() - 1) / pageSize());
		graphics.text(
			font,
			Component.translatable("screen.hydros_bundles.page", contentPage + 1, pages),
			contentLeft(),
			contentTop() + CONTENT_ROWS * SLOT_SIZE + 2,
			0xFFAAAAAA
		);
	}

	private void renderPlayerInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (minecraft.player == null) {
			return;
		}
		graphics.text(
			font,
			Component.translatable("screen.hydros_bundles.player_inventory"),
			inventoryLeft(),
			inventoryTop() - 11,
			0xFFFFFFFF
		);
		for (int displayIndex = 0; displayIndex < BundleInventory.PLAYER_STORAGE_SIZE; displayIndex++) {
			int x = inventoryLeft() + displayIndex % 9 * SLOT_SIZE;
			int y = inventoryTop() + displayIndex / 9 * SLOT_SIZE;
			renderSlotBackground(graphics, x, y, mouseX, mouseY);
			ItemStack stack = minecraft.player.getInventory().getItem(displayToInventoryIndex(displayIndex));
			if (!stack.isEmpty()) {
				graphics.item(stack, x + 1, y + 1);
				graphics.itemDecorations(font, stack, x + 1, y + 1);
				if (isInside(mouseX, mouseY, x, y)) {
					graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
				}
			}
		}
	}

	private void renderSlotBackground(
		GuiGraphicsExtractor graphics,
		int x,
		int y,
		int mouseX,
		int mouseY
	) {
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, isInside(mouseX, mouseY, x, y)
			? 0x90606060
			: 0x90404040);
		graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF888888);
	}

	private void withdraw(int contentIndex) {
		if (!inventoryInteractionReady()) {
			return;
		}
		ItemStack bundle = selectedBundle();
		BundleContents contents = BundleInventory.contents(bundle);
		if (contentIndex < 0 || contentIndex >= contents.size()) {
			return;
		}
		ItemStack desired = contents.items().get(contentIndex).create();
		if (availableInventoryCapacity(desired) < desired.getCount()) {
			HydrosBundlesClient.notifyPlayer(Component.translatable("message.hydros_bundles.inventory_full"));
			return;
		}

		Inventory inventory = minecraft.player.getInventory();
		AbstractContainerMenu menu = minecraft.player.inventoryMenu;
		Slot bundleSlot = BundleInventory.menuSlot(menu, inventory, selectedBundleIndex);
		if (bundleSlot == null || minecraft.getConnection() == null) {
			return;
		}
		if (!acquireInventoryAction()) {
			return;
		}

		if (BundleItem.getSelectedItemIndex(bundle) != contentIndex) {
			BundleItem.toggleSelectedItem(bundle, contentIndex);
			minecraft.getConnection().send(new ServerboundSelectBundleItemPacket(bundleSlot.index, contentIndex));
		}
		minecraft.gameMode.handleContainerInput(
			menu.containerId,
			bundleSlot.index,
			1,
			ContainerInput.PICKUP,
			minecraft.player
		);
		placeCarriedIntoInventory(bundleSlot);
	}

	private void insertFromInventory(int sourceInventoryIndex) {
		if (!inventoryInteractionReady() || sourceInventoryIndex == selectedBundleIndex) {
			return;
		}
		Inventory inventory = minecraft.player.getInventory();
		ItemStack source = inventory.getItem(sourceInventoryIndex);
		ItemStack bundle = selectedBundle();
		if (!BundleInventory.canAccept(bundle, source)) {
			return;
		}
		AbstractContainerMenu menu = minecraft.player.inventoryMenu;
		Slot sourceSlot = BundleInventory.menuSlot(menu, inventory, sourceInventoryIndex);
		Slot bundleSlot = BundleInventory.menuSlot(menu, inventory, selectedBundleIndex);
		if (sourceSlot == null || bundleSlot == null) {
			return;
		}
		if (!acquireInventoryAction()) {
			return;
		}

		minecraft.gameMode.handleContainerInput(menu.containerId, sourceSlot.index, 0, ContainerInput.PICKUP, minecraft.player);
		minecraft.gameMode.handleContainerInput(menu.containerId, bundleSlot.index, 0, ContainerInput.PICKUP, minecraft.player);
		if (!menu.getCarried().isEmpty()) {
			minecraft.gameMode.handleContainerInput(menu.containerId, sourceSlot.index, 0, ContainerInput.PICKUP, minecraft.player);
		}
	}

	private void placeCarriedIntoInventory(Slot bundleSlot) {
		AbstractContainerMenu menu = minecraft.player.inventoryMenu;
		Inventory inventory = minecraft.player.getInventory();
		for (int inventoryIndex : inventoryDisplayOrder()) {
			if (inventoryIndex == selectedBundleIndex || menu.getCarried().isEmpty()) {
				continue;
			}
			ItemStack existing = inventory.getItem(inventoryIndex);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, menu.getCarried())
				|| existing.getCount() >= existing.getMaxStackSize()) {
				continue;
			}
			clickInventorySlot(menu, inventory, inventoryIndex);
		}
		for (int inventoryIndex : inventoryDisplayOrder()) {
			if (inventoryIndex == selectedBundleIndex || menu.getCarried().isEmpty()) {
				continue;
			}
			if (inventory.getItem(inventoryIndex).isEmpty()) {
				clickInventorySlot(menu, inventory, inventoryIndex);
			}
		}
		if (!menu.getCarried().isEmpty()) {
			// A server-side rule rejected a target slot. Put the extracted stack
			// back into the bundle instead of leaving a hidden carried stack.
			minecraft.gameMode.handleContainerInput(
				menu.containerId,
				bundleSlot.index,
				0,
				ContainerInput.PICKUP,
				minecraft.player
			);
		}
	}

	private void clickInventorySlot(AbstractContainerMenu menu, Inventory inventory, int inventoryIndex) {
		Slot slot = BundleInventory.menuSlot(menu, inventory, inventoryIndex);
		if (slot != null && slot.mayPlace(menu.getCarried())) {
			minecraft.gameMode.handleContainerInput(
				menu.containerId,
				slot.index,
				0,
				ContainerInput.PICKUP,
				minecraft.player
			);
		}
	}

	private int availableInventoryCapacity(ItemStack desired) {
		if (minecraft.player == null) {
			return 0;
		}
		int capacity = 0;
		Inventory inventory = minecraft.player.getInventory();
		for (int index = 0; index < BundleInventory.PLAYER_STORAGE_SIZE; index++) {
			if (index == selectedBundleIndex) {
				continue;
			}
			ItemStack existing = inventory.getItem(index);
			if (existing.isEmpty()) {
				capacity += desired.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(existing, desired)) {
				capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
			}
			if (capacity >= desired.getCount()) {
				return capacity;
			}
		}
		return capacity;
	}

	private boolean inventoryInteractionReady() {
		if (minecraft.player == null || minecraft.gameMode == null
			|| minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
			return false;
		}
		if (!minecraft.player.inventoryMenu.getCarried().isEmpty()) {
			HydrosBundlesClient.notifyPlayer(Component.translatable("message.hydros_bundles.inventory_busy"));
			return false;
		}
		refreshSelectedBundle();
		return selectedBundle() != null;
	}

	private boolean acquireInventoryAction() {
		if (InventoryActionCoordinator.acquire(InventoryActionCoordinator.HYDROS_BUNDLES)) {
			return true;
		}
		HydrosBundlesClient.notifyPlayer(Component.translatable("message.inventory_toolkit.busy"));
		return false;
	}

	private void cycleBundle(int direction) {
		if (minecraft.player == null) {
			return;
		}
		List<Integer> bundles = BundleInventory.bundleInventoryIndices(minecraft.player.getInventory());
		if (bundles.isEmpty()) {
			selectedBundleIndex = -1;
			contentPage = 0;
			return;
		}
		int current = bundles.indexOf(selectedBundleIndex);
		int next = Math.floorMod((current < 0 ? 0 : current) + direction, bundles.size());
		selectedBundleIndex = bundles.get(next);
		contentPage = 0;
	}

	private void changePage(int direction) {
		ItemStack bundle = selectedBundle();
		if (bundle == null) {
			return;
		}
		int pages = Math.max(1, (BundleInventory.contents(bundle).size() + pageSize() - 1) / pageSize());
		contentPage = Math.floorMod(contentPage + direction, pages);
	}

	private void refreshSelectedBundle() {
		if (minecraft.player == null) {
			selectedBundleIndex = -1;
			return;
		}
		List<Integer> bundles = BundleInventory.bundleInventoryIndices(minecraft.player.getInventory());
		if (bundles.contains(selectedBundleIndex)) {
			return;
		}
		int remembered = HydrosBundlesClient.config().rememberLastBundle
			? HydrosBundlesClient.lastBundleIndex()
			: -1;
		selectedBundleIndex = bundles.contains(remembered)
			? remembered
			: bundles.stream().findFirst().orElse(-1);
		contentPage = 0;
	}

	private ItemStack selectedBundle() {
		if (minecraft.player == null || selectedBundleIndex < 0
			|| selectedBundleIndex >= BundleInventory.PLAYER_STORAGE_SIZE) {
			return null;
		}
		ItemStack stack = minecraft.player.getInventory().getItem(selectedBundleIndex);
		return BundleInventory.isBundle(stack) ? stack : null;
	}

	private int contentColumns() {
		return Math.max(4, Math.min(12, (Math.min(width - 20, 260) - 8) / SLOT_SIZE));
	}

	private int pageSize() {
		return contentColumns() * CONTENT_ROWS;
	}

	private int contentLeft() {
		return (width - contentColumns() * SLOT_SIZE) / 2;
	}

	private int contentTop() {
		return 64;
	}

	private int inventoryLeft() {
		return (width - 9 * SLOT_SIZE) / 2;
	}

	private int inventoryTop() {
		return 143;
	}

	private static int displayToInventoryIndex(int displayIndex) {
		return displayIndex < 27 ? displayIndex + 9 : displayIndex - 27;
	}

	private static List<Integer> inventoryDisplayOrder() {
		List<Integer> result = new ArrayList<>(BundleInventory.PLAYER_STORAGE_SIZE);
		for (int displayIndex = 0; displayIndex < BundleInventory.PLAYER_STORAGE_SIZE; displayIndex++) {
			result.add(displayToInventoryIndex(displayIndex));
		}
		return result;
	}

	private static int slotAt(
		double mouseX,
		double mouseY,
		int left,
		int top,
		int columns,
		int rows
	) {
		if (mouseX < left || mouseY < top
			|| mouseX >= left + columns * SLOT_SIZE
			|| mouseY >= top + rows * SLOT_SIZE) {
			return -1;
		}
		return (int) ((mouseY - top) / SLOT_SIZE) * columns
			+ (int) ((mouseX - left) / SLOT_SIZE);
	}

	private static boolean isInside(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
	}
}
