package dev.hydro.hydrosbundles.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hydro.hydrosbundles.BundleInventory;
import dev.hydro.hydrosbundles.HydrosBundlesClient;
import dev.hydro.hydrosbundles.RedstoneClassifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VisualRoutingEditorScreen extends Screen {
	private static final int SLOT_SIZE = 18;
	private static final int ITEM_ROWS = 3;

	private final Screen parent;
	private final List<EditableRoute> routes = new ArrayList<>();
	private final List<ItemChoice> allItems = new ArrayList<>();
	private final List<String> detectedBundleNames = new ArrayList<>();
	private List<ItemChoice> filteredItems = List.of();

	private int selectedRoute;
	private int itemPage;
	private EditBox bundleNameBox;
	private EditBox searchBox;
	private Button blocksButton;
	private Button redstoneButton;
	private Component statusMessage = Component.empty();
	private int statusColor = 0xFFAAAAAA;

	public VisualRoutingEditorScreen(Screen parent) {
		super(Component.translatable("config.hydros_bundles.visual_routing.title"));
		this.parent = parent;
		for (String rawRule : HydrosBundlesClient.config().routingRules) {
			routes.add(EditableRoute.fromConfig(rawRule));
		}
		loadItemChoices();
		detectNamedBundles();
		if (routes.isEmpty()) {
			routes.add(new EditableRoute(nextSuggestedBundleName()));
		}
	}

	@Override
	protected void init() {
		detectNamedBundles();
		if (currentRoute().bundleName.isBlank()) {
			currentRoute().bundleName = nextSuggestedBundleName();
		}
		int center = width / 2;
		int navWidth = Math.min(310, width - 12);
		int navLeft = center - navWidth / 2;

		addRenderableWidget(Button.builder(Component.literal("◀"), button -> selectRelative(-1))
			.bounds(navLeft, 22, 24, 20).build());
		addRenderableWidget(Button.builder(Component.literal("▶"), button -> selectRelative(1))
			.bounds(navLeft + 28, 22, 24, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("config.hydros_bundles.route_up"),
				button -> moveRoute(-1))
			.bounds(navLeft + 58, 22, 38, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("config.hydros_bundles.route_down"),
				button -> moveRoute(1))
			.bounds(navLeft + 100, 22, 38, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("config.hydros_bundles.route_add"),
				button -> addRoute())
			.bounds(navLeft + navWidth - 106, 22, 50, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("config.hydros_bundles.route_delete"),
				button -> deleteRoute())
			.bounds(navLeft + navWidth - 52, 22, 52, 20).build());

		bundleNameBox = new EditBox(
			font,
			center - 126,
			51,
			252,
			20,
			Component.translatable("config.hydros_bundles.bundle_name")
		);
		bundleNameBox.setMaxLength(80);
		bundleNameBox.setHint(Component.translatable("config.hydros_bundles.bundle_name.hint"));
		bundleNameBox.setResponder(value -> {
			currentRoute().bundleName = value;
		});
		addRenderableWidget(bundleNameBox);

		blocksButton = addRenderableWidget(Button.builder(Component.empty(), button -> togglePreset("$blocks"))
			.bounds(center - 126, 76, 122, 20).build());
		redstoneButton = addRenderableWidget(Button.builder(Component.empty(), button -> togglePreset("$redstone"))
			.bounds(center + 4, 76, 122, 20).build());

		searchBox = new EditBox(
			font,
			center - 126,
			102,
			252,
			20,
			Component.translatable("config.hydros_bundles.item_search")
		);
		searchBox.setMaxLength(80);
		searchBox.setHint(Component.translatable("config.hydros_bundles.item_search.hint"));
		searchBox.setResponder(value -> {
			itemPage = 0;
			updateFilteredItems();
		});
		addRenderableWidget(searchBox);

		int footerY = Math.min(height - 24, 216);
		addRenderableWidget(Button.builder(Component.literal("◀"), button -> changeItemPage(-1))
			.bounds(center - 126, footerY - 22, 28, 18).build());
		addRenderableWidget(Button.builder(Component.literal("▶"), button -> changeItemPage(1))
			.bounds(center - 94, footerY - 22, 28, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("config.hydros_bundles.cancel"),
				button -> onClose())
			.bounds(center - 126, footerY, 122, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("config.hydros_bundles.save_done"),
				button -> saveAndClose())
			.bounds(center + 4, footerY, 122, 20).build());

		loadCurrentRouteIntoWidgets();
		updateFilteredItems();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		int displayIndex = slotAt(event.x(), event.y());
		if (displayIndex < 0) {
			return false;
		}
		int itemIndex = itemPage * pageSize() + displayIndex;
		if (itemIndex >= filteredItems.size()) {
			return true;
		}
		String itemId = filteredItems.get(itemIndex).id.toString();
		EditableRoute route = currentRoute();
		boolean nowIncluded;
		if (route.matchesPreset(filteredItems.get(itemIndex).stack)) {
			if (!route.excludedItemIds.remove(itemId)) {
				route.excludedItemIds.add(itemId);
			}
			route.exactItemIds.remove(itemId);
			nowIncluded = !route.excludedItemIds.contains(itemId);
		} else {
			if (!route.exactItemIds.remove(itemId)) {
				route.exactItemIds.add(itemId);
			}
			route.excludedItemIds.remove(itemId);
			nowIncluded = route.exactItemIds.contains(itemId);
		}
		statusMessage = Component.translatable(
			nowIncluded
				? "config.hydros_bundles.item_added"
				: "config.hydros_bundles.item_removed",
			filteredItems.get(itemIndex).stack.getHoverName()
		);
		statusColor = 0xFF80D890;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= itemGridLeft() && mouseX < itemGridLeft() + itemColumns() * SLOT_SIZE
			&& mouseY >= itemGridTop() && mouseY < itemGridTop() + ITEM_ROWS * SLOT_SIZE
			&& verticalAmount != 0.0) {
			changeItemPage(verticalAmount > 0.0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable(
				"config.hydros_bundles.route_position",
				selectedRoute + 1,
				routes.size(),
				currentRoute().bundleName.isBlank()
					? Component.translatable("config.hydros_bundles.unnamed_route")
					: Component.literal(currentRoute().bundleName)
			),
			width / 2,
			43,
			0xFFCCCCCC
		);
		renderItemGrid(graphics, mouseX, mouseY);
		int pages = Math.max(1, (filteredItems.size() + pageSize() - 1) / pageSize());
		graphics.text(
			font,
			Component.translatable(
				"config.hydros_bundles.item_results",
				filteredItems.size(),
				itemPage + 1,
				pages,
				currentRoute().exactItemIds.size(),
				currentRoute().excludedItemIds.size()
			),
			itemGridLeft() + 64,
			Math.min(height - 46, 190),
			0xFFAAAAAA
		);
		if (!currentRoute().advancedSelectors.isEmpty() && statusMessage.getString().isEmpty()) {
			graphics.centeredText(
				font,
				Component.translatable(
					"config.hydros_bundles.advanced_preserved",
					currentRoute().advancedSelectors.size()
				),
				width / 2,
				124,
				0xFFFFCC66
			);
		}
		if (!statusMessage.getString().isEmpty()) {
			graphics.centeredText(font, statusMessage, width / 2, 124, statusColor);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void renderItemGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int start = itemPage * pageSize();
		for (int displayIndex = 0; displayIndex < pageSize(); displayIndex++) {
			int x = itemGridLeft() + displayIndex % itemColumns() * SLOT_SIZE;
			int y = itemGridTop() + displayIndex / itemColumns() * SLOT_SIZE;
			int itemIndex = start + displayIndex;
			boolean selected = itemIndex < filteredItems.size()
				&& currentRoute().isIncluded(filteredItems.get(itemIndex));
			boolean excluded = itemIndex < filteredItems.size()
				&& currentRoute().excludedItemIds.contains(filteredItems.get(itemIndex).id.toString());
			boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE
				&& mouseY >= y && mouseY < y + SLOT_SIZE;
			graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hovered ? 0x90606060 : 0x90404040);
			graphics.outline(
				x,
				y,
				SLOT_SIZE,
				SLOT_SIZE,
				excluded ? 0xFFFF5555 : selected ? 0xFF55FF77 : 0xFF888888
			);
			if (itemIndex >= filteredItems.size()) {
				continue;
			}
			ItemStack stack = filteredItems.get(itemIndex).stack;
			graphics.item(stack, x + 1, y + 1);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
		}
	}

	private void selectRelative(int direction) {
		if (routes.isEmpty()) {
			return;
		}
		selectedRoute = Math.floorMod(selectedRoute + direction, routes.size());
		itemPage = 0;
		loadCurrentRouteIntoWidgets();
	}

	private void moveRoute(int direction) {
		int target = selectedRoute + direction;
		if (target < 0 || target >= routes.size()) {
			return;
		}
		EditableRoute route = routes.remove(selectedRoute);
		routes.add(target, route);
		selectedRoute = target;
		statusMessage = Component.translatable("config.hydros_bundles.priority_changed");
		statusColor = 0xFF80D890;
	}

	private void addRoute() {
		routes.add(new EditableRoute(nextSuggestedBundleName()));
		selectedRoute = routes.size() - 1;
		itemPage = 0;
		loadCurrentRouteIntoWidgets();
		statusMessage = Component.translatable("config.hydros_bundles.choose_items");
		statusColor = 0xFFFFCC66;
	}

	private void deleteRoute() {
		if (routes.size() == 1) {
			routes.set(0, new EditableRoute(nextSuggestedBundleName()));
			selectedRoute = 0;
		} else {
			routes.remove(selectedRoute);
			selectedRoute = Math.min(selectedRoute, routes.size() - 1);
		}
		loadCurrentRouteIntoWidgets();
	}

	private void togglePreset(String preset) {
		EditableRoute route = currentRoute();
		if (!route.presets.remove(preset)) {
			route.presets.add(preset);
		}
		updatePresetButtons();
	}

	private void changeItemPage(int direction) {
		int pages = Math.max(1, (filteredItems.size() + pageSize() - 1) / pageSize());
		itemPage = Math.floorMod(itemPage + direction, pages);
	}

	private void updateFilteredItems() {
		String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		filteredItems = allItems.stream()
			.filter(choice -> query.isEmpty()
				|| choice.searchName.contains(query)
				|| choice.id.toString().contains(query))
			.toList();
		int pages = Math.max(1, (filteredItems.size() + pageSize() - 1) / pageSize());
		itemPage = Math.min(itemPage, pages - 1);
	}

	private void loadCurrentRouteIntoWidgets() {
		if (bundleNameBox == null) {
			return;
		}
		bundleNameBox.setValue(currentRoute().bundleName);
		updatePresetButtons();
		statusMessage = Component.empty();
	}

	private void updatePresetButtons() {
		if (blocksButton == null || redstoneButton == null) {
			return;
		}
		blocksButton.setMessage(toggleLabel(
			"config.hydros_bundles.preset.blocks",
			currentRoute().presets.contains("$blocks")
		));
		redstoneButton.setMessage(toggleLabel(
			"config.hydros_bundles.preset.redstone",
			currentRoute().presets.contains("$redstone")
		));
	}

	private Component toggleLabel(String key, boolean enabled) {
		return Component.translatable(
			enabled ? "config.hydros_bundles.preset.on" : "config.hydros_bundles.preset.off",
			Component.translatable(key)
		);
	}

	private void saveAndClose() {
		for (EditableRoute route : routes) {
			if (route.bundleName.isBlank()) {
				showError("config.hydros_bundles.error.bundle_name");
				return;
			}
			if (!route.hasSelectors()) {
				showError("config.hydros_bundles.error.no_items");
				return;
			}
		}

		HydrosBundlesClient.config().routingRules = routes.stream()
			.map(EditableRoute::toConfig)
			.toList();
		HydrosBundlesClient.config().save();
		HydrosBundlesClient.reloadManagerConfig();
		minecraft.gui.setScreen(parent);
	}

	private void showError(String translationKey) {
		statusMessage = Component.translatable(translationKey);
		statusColor = 0xFFFF6666;
	}

	private EditableRoute currentRoute() {
		return routes.get(Math.max(0, Math.min(selectedRoute, routes.size() - 1)));
	}

	private String nextSuggestedBundleName() {
		for (String name : detectedBundleNames) {
			boolean used = routes.stream().anyMatch(route -> route.bundleName.equalsIgnoreCase(name));
			if (!used) {
				return name;
			}
		}
		return "";
	}

	private void detectNamedBundles() {
		if (minecraft == null || minecraft.player == null || !detectedBundleNames.isEmpty()) {
			return;
		}
		for (int index : BundleInventory.bundleInventoryIndices(minecraft.player.getInventory())) {
			ItemStack bundle = minecraft.player.getInventory().getItem(index);
			if (bundle.getCustomName() != null) {
				String name = bundle.getCustomName().getString().trim();
				if (!name.isEmpty() && detectedBundleNames.stream().noneMatch(name::equalsIgnoreCase)) {
					detectedBundleNames.add(name);
				}
			}
		}
	}

	private void loadItemChoices() {
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (stack.isEmpty() || !BundleContents.canItemBeInBundle(stack)) {
				continue;
			}
			String displayName = stack.getHoverName().getString();
			allItems.add(new ItemChoice(
				id,
				stack,
				(displayName + " " + id).toLowerCase(Locale.ROOT)
			));
		}
		allItems.sort(Comparator.comparing(choice -> choice.stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
		filteredItems = List.copyOf(allItems);
	}

	private int itemColumns() {
		return Math.max(6, Math.min(14, (Math.min(width - 16, 270)) / SLOT_SIZE));
	}

	private int pageSize() {
		return itemColumns() * ITEM_ROWS;
	}

	private int itemGridLeft() {
		return (width - itemColumns() * SLOT_SIZE) / 2;
	}

	private int itemGridTop() {
		return 135;
	}

	private int slotAt(double mouseX, double mouseY) {
		int left = itemGridLeft();
		int top = itemGridTop();
		if (mouseX < left || mouseY < top
			|| mouseX >= left + itemColumns() * SLOT_SIZE
			|| mouseY >= top + ITEM_ROWS * SLOT_SIZE) {
			return -1;
		}
		return (int) ((mouseY - top) / SLOT_SIZE) * itemColumns()
			+ (int) ((mouseX - left) / SLOT_SIZE);
	}

	private record ItemChoice(Identifier id, ItemStack stack, String searchName) {
	}

	private static final class EditableRoute {
		private String bundleName;
		private final Set<String> presets = new LinkedHashSet<>();
		private final Set<String> exactItemIds = new LinkedHashSet<>();
		private final Set<String> excludedItemIds = new LinkedHashSet<>();
		private final List<String> advancedSelectors = new ArrayList<>();

		private EditableRoute(String bundleName) {
			this.bundleName = bundleName;
		}

		private static EditableRoute fromConfig(String raw) {
			int separator = raw.indexOf('=');
			EditableRoute route = new EditableRoute(
				separator > 0 ? raw.substring(0, separator).trim() : ""
			);
			if (separator < 0 || separator == raw.length() - 1) {
				return route;
			}
			for (String part : raw.substring(separator + 1).split(",")) {
				String selector = part.trim();
				if (selector.equals("$blocks") || selector.equals("$redstone")) {
					route.presets.add(selector);
					continue;
				}
				boolean excluded = selector.startsWith("!");
				String itemSelector = excluded ? selector.substring(1).trim() : selector;
				Identifier id = itemSelector.startsWith("#")
					|| selector.contains("*")
					? null
					: Identifier.tryParse(itemSelector);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					(excluded ? route.excludedItemIds : route.exactItemIds).add(id.toString());
				} else if (!selector.isEmpty()) {
					route.advancedSelectors.add(selector);
				}
			}
			return route;
		}

		private boolean hasSelectors() {
			return !presets.isEmpty() || !exactItemIds.isEmpty() || !advancedSelectors.isEmpty();
		}

		private boolean matchesPreset(ItemStack stack) {
			return presets.contains("$blocks") && stack.getItem() instanceof BlockItem
				|| presets.contains("$redstone") && RedstoneClassifier.matches(stack);
		}

		private boolean isIncluded(ItemChoice choice) {
			String itemId = choice.id.toString();
			return !excludedItemIds.contains(itemId)
				&& (exactItemIds.contains(itemId) || matchesPreset(choice.stack));
		}

		private String toConfig() {
			List<String> selectors = new ArrayList<>();
			if (presets.contains("$redstone")) {
				selectors.add("$redstone");
			}
			if (presets.contains("$blocks")) {
				selectors.add("$blocks");
			}
			selectors.addAll(advancedSelectors);
			selectors.addAll(excludedItemIds.stream().sorted().map(id -> "!" + id).toList());
			selectors.addAll(exactItemIds.stream().sorted().toList());
			return bundleName.trim() + " = " + String.join(", ", selectors);
		}
	}
}
