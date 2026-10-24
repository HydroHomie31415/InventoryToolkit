package dev.hydro.hydrosbundles.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hydro.hydrosbundles.BundleInventory;
import dev.hydro.hydrosbundles.HydrosBundlesClient;
import dev.hydro.hydrosbundles.config.HydrosBundlesSettingsScreen;
import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.resources.Identifier;
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
	private static final Identifier CONTAINER_BACKGROUND =
		Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
	private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
	private static final int SLOT_SIZE = 18;
	private static final int CONTENT_COLUMNS = 9;
	private static final int CONTENT_ROWS = 3;
	private static final int IMAGE_WIDTH = 176;
	// Same slices of generic_54.png that the vanilla chest screen uses.
	private static final int CONTENT_HEIGHT = 17 + CONTENT_ROWS * SLOT_SIZE;
	private static final int INVENTORY_HEIGHT = 96;
	private static final int INVENTORY_V = 126;
	private static final int BOTTOM_BORDER_HEIGHT = 7;
	private static final int BOTTOM_BORDER_V = 215;
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int SELECTED_BUNDLE_COLOR = 0x70FFC23A;

	private final Screen parent;
	private int selectedBundleIndex = -1;
	private int contentPage;
	private Button previousBundleButton;
	private Button nextBundleButton;
	private Button previousPageButton;
	private Button nextPageButton;

	public BundleOverviewScreen(Screen parent) {
		super(Component.translatable("screen.hydros_bundles.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		refreshSelectedBundle();
		int left = leftPos();
		int buttonTop = topPos() - 22;
		previousBundleButton = addRenderableWidget(Button.builder(Component.literal("◀"), button -> cycleBundle(-1))
			.tooltip(Tooltip.create(Component.translatable("screen.hydros_bundles.previous_bundle")))
			.bounds(left, buttonTop, 20, 20).build());
		nextBundleButton = addRenderableWidget(Button.builder(Component.literal("▶"), button -> cycleBundle(1))
			.tooltip(Tooltip.create(Component.translatable("screen.hydros_bundles.next_bundle")))
			.bounds(left + 22, buttonTop, 20, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.hydros_bundles.config"),
				button -> minecraft.gui.setScreen(new HydrosBundlesSettingsScreen(this)))
			.bounds(left + IMAGE_WIDTH - 98, buttonTop, 48, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.hydros_bundles.done"),
				button -> onClose())
			.bounds(left + IMAGE_WIDTH - 48, buttonTop, 48, 20).build());

		int pageX = left + IMAGE_WIDTH + 3;
		previousPageButton = addRenderableWidget(Button.builder(Component.literal("▲"), button -> changePage(-1))
			.tooltip(Tooltip.create(Component.translatable("screen.hydros_bundles.previous_page")))
			.bounds(pageX, contentTop(), 16, 16).build());
		nextPageButton = addRenderableWidget(Button.builder(Component.literal("▼"), button -> changePage(1))
			.tooltip(Tooltip.create(Component.translatable("screen.hydros_bundles.next_page")))
			.bounds(pageX, contentTop() + CONTENT_ROWS * SLOT_SIZE - 16, 16, 16).build());
		updateButtonStates();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || selectedBundle() == null) {
			return false;
		}

		int contentSlot = contentSlotAt(event.x(), event.y());
		if (contentSlot >= 0) {
			int contentIndex = contentPage * pageSize() + contentSlot;
			withdraw(contentIndex);
			return true;
		}

		if (HydrosBundlesClient.config().showPlayerInventory) {
			int displaySlot = inventorySlotAt(event.x(), event.y());
			if (displaySlot >= 0) {
				insertFromInventory(displayToInventoryIndex(displaySlot));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= contentLeft() && mouseX < contentLeft() + CONTENT_COLUMNS * SLOT_SIZE
			&& mouseY >= contentTop() && mouseY < contentTop() + CONTENT_ROWS * SLOT_SIZE
			&& verticalAmount != 0.0) {
			changePage(verticalAmount > 0.0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		updateButtonStates();
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

		boolean showInventory = HydrosBundlesClient.config().showPlayerInventory;
		renderPanel(graphics, showInventory);
		renderTitle(graphics, bundle);

		BundleContents contents = BundleInventory.contents(bundle);
		renderContents(graphics, contents, mouseX, mouseY);
		if (showInventory) {
			renderPlayerInventory(graphics, mouseX, mouseY);
		}
		graphics.centeredText(
			font,
			Component.translatable("screen.hydros_bundles.hint"),
			width / 2,
			topPos() + imageHeight() + 4,
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

	private void renderPanel(GuiGraphicsExtractor graphics, boolean showInventory) {
		int left = leftPos();
		int top = topPos();
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, left, top, 0.0F, 0.0F,
			IMAGE_WIDTH, CONTENT_HEIGHT, 256, 256);
		if (showInventory) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, left, top + CONTENT_HEIGHT,
				0.0F, INVENTORY_V, IMAGE_WIDTH, INVENTORY_HEIGHT, 256, 256);
		} else {
			graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, left, top + CONTENT_HEIGHT,
				0.0F, BOTTOM_BORDER_V, IMAGE_WIDTH, BOTTOM_BORDER_HEIGHT, 256, 256);
		}
	}

	private void renderTitle(GuiGraphicsExtractor graphics, ItemStack bundle) {
		int left = leftPos();
		int top = topPos();
		Component fullness = Component.translatable(
			"screen.hydros_bundles.fullness",
			Math.round(BundleItem.getFullnessDisplay(bundle) * 100.0F)
		);
		int fullnessWidth = font.width(fullness);
		graphics.text(font, fullness, left + IMAGE_WIDTH - 8 - fullnessWidth, top + 6, LABEL_COLOR, false);
		int titleWidth = IMAGE_WIDTH - 16 - fullnessWidth - 6;
		graphics.text(
			font,
			Language.getInstance().getVisualOrder(font.substrByWidth(bundle.getHoverName(), titleWidth)),
			left + 8,
			top + 6,
			LABEL_COLOR,
			false
		);
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
			int x = contentLeft() + displayIndex % CONTENT_COLUMNS * SLOT_SIZE;
			int y = contentTop() + displayIndex / CONTENT_COLUMNS * SLOT_SIZE;
			int contentIndex = start + displayIndex;
			ItemStack stack = contentIndex < items.size() ? items.get(contentIndex).create() : ItemStack.EMPTY;
			renderSlot(graphics, stack, x, y, mouseX, mouseY);
		}
		int pages = pageCount(contents);
		if (pages > 1) {
			graphics.centeredText(
				font,
				Component.translatable("screen.hydros_bundles.page", contentPage + 1, pages),
				leftPos() + IMAGE_WIDTH + 11,
				contentTop() + CONTENT_ROWS * SLOT_SIZE / 2 - 4,
				0xFFFFFFFF
			);
		}
	}

	private void renderPlayerInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (minecraft.player == null) {
			return;
		}
		graphics.text(
			font,
			Component.translatable("container.inventory"),
			leftPos() + 8,
			topPos() + imageHeight() - 94,
			LABEL_COLOR,
			false
		);
		for (int displayIndex = 0; displayIndex < BundleInventory.PLAYER_STORAGE_SIZE; displayIndex++) {
			int x = inventorySlotX(displayIndex);
			int y = inventorySlotY(displayIndex);
			int inventoryIndex = displayToInventoryIndex(displayIndex);
			if (inventoryIndex == selectedBundleIndex) {
				graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SELECTED_BUNDLE_COLOR);
			}
			renderSlot(graphics, minecraft.player.getInventory().getItem(inventoryIndex), x, y, mouseX, mouseY);
		}
	}

	private void renderSlot(
		GuiGraphicsExtractor graphics,
		ItemStack stack,
		int x,
		int y,
		int mouseX,
		int mouseY
	) {
		boolean hovered = isInside(mouseX, mouseY, x, y);
		if (hovered) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, x - 3, y - 3, 24, 24);
		}
		if (!stack.isEmpty()) {
			graphics.item(stack, x + 1, y + 1);
			graphics.itemDecorations(font, stack, x + 1, y + 1);
		}
		if (hovered) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, x - 3, y - 3, 24, 24);
			if (!stack.isEmpty()) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
		}
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
		contentPage = Math.floorMod(contentPage + direction, pageCount(BundleInventory.contents(bundle)));
	}

	private void updateButtonStates() {
		if (previousBundleButton == null || minecraft.player == null) {
			return;
		}
		boolean multipleBundles = BundleInventory.bundleInventoryIndices(minecraft.player.getInventory()).size() > 1;
		previousBundleButton.active = multipleBundles;
		nextBundleButton.active = multipleBundles;
		ItemStack bundle = selectedBundle();
		boolean multiplePages = bundle != null && pageCount(BundleInventory.contents(bundle)) > 1;
		previousPageButton.visible = multiplePages;
		nextPageButton.visible = multiplePages;
		if (bundle != null) {
			contentPage = Math.min(contentPage, pageCount(BundleInventory.contents(bundle)) - 1);
		}
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

	private static int pageSize() {
		return CONTENT_COLUMNS * CONTENT_ROWS;
	}

	private static int pageCount(BundleContents contents) {
		return Math.max(1, (contents.size() + pageSize() - 1) / pageSize());
	}

	private int imageHeight() {
		return CONTENT_HEIGHT + (HydrosBundlesClient.config().showPlayerInventory
			? INVENTORY_HEIGHT
			: BOTTOM_BORDER_HEIGHT);
	}

	private int leftPos() {
		return (width - IMAGE_WIDTH) / 2;
	}

	private int topPos() {
		return (height - imageHeight()) / 2;
	}

	// Slot coordinates below are the top-left of the 18x18 slot frame; items render one pixel in.
	private int contentLeft() {
		return leftPos() + 7;
	}

	private int contentTop() {
		return topPos() + 17;
	}

	private int inventorySlotX(int displayIndex) {
		return leftPos() + 7 + displayIndex % 9 * SLOT_SIZE;
	}

	private int inventorySlotY(int displayIndex) {
		int row = displayIndex / 9;
		// The hotbar row sits 4px below the main inventory, as in vanilla.
		return topPos() + CONTENT_HEIGHT + 13 + row * SLOT_SIZE + (row == 3 ? 4 : 0);
	}

	private int contentSlotAt(double mouseX, double mouseY) {
		int left = contentLeft();
		int top = contentTop();
		if (mouseX < left || mouseY < top
			|| mouseX >= left + CONTENT_COLUMNS * SLOT_SIZE
			|| mouseY >= top + CONTENT_ROWS * SLOT_SIZE) {
			return -1;
		}
		return (int) ((mouseY - top) / SLOT_SIZE) * CONTENT_COLUMNS
			+ (int) ((mouseX - left) / SLOT_SIZE);
	}

	private int inventorySlotAt(double mouseX, double mouseY) {
		for (int displayIndex = 0; displayIndex < BundleInventory.PLAYER_STORAGE_SIZE; displayIndex++) {
			int x = inventorySlotX(displayIndex);
			int y = inventorySlotY(displayIndex);
			if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
				return displayIndex;
			}
		}
		return -1;
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

	private static boolean isInside(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
	}
}
