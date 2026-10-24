package dev.luka.inventorytoolkit.containersearch;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.quickstack.NearbyQuickStack;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import dev.luka.inventorytoolkit.quickstack.QuickStackConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ContainerSearchScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int LIST_TOP = 50;
	private static final int LIST_BOTTOM_MARGIN = 34;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int TOOLTIP_CONTAINERS = 6;

	private final boolean returnToInventory;
	private final String initialQuery;
	private EditBox searchBox;
	private List<Result> results = List.of();
	private Component elsewhere;
	private int scrollRow;
	private int selectedIndex;
	private boolean draggingScrollbar;

	private record Match(ContainerMemory.StoredContainer container, int count, double distance) {
	}

	/**
	 * @param plan for a result that can be crafted from storage, how; null for
	 *     a result found in containers
	 * @param sources for a craftable result, where to take its ingredients
	 */
	private record Result(
		ItemStack icon,
		String name,
		int total,
		boolean onlyInsideContainers,
		List<Match> matches,
		StorageCrafting.Plan plan,
		List<StorageCrafting.Source> sources
	) {
		double nearestDistance() {
			return matches.getFirst().distance();
		}
	}

	public ContainerSearchScreen(boolean returnToInventory, String initialQuery) {
		super(Component.translatable("screen.inventory_toolkit.container_search.title"));
		this.returnToInventory = returnToInventory;
		this.initialQuery = initialQuery == null ? "" : initialQuery;
	}

	@Override
	protected void init() {
		int listWidth = listWidth();
		int left = (width - listWidth) / 2;
		String query = searchBox == null ? initialQuery : searchBox.getValue();
		searchBox = new EditBox(font, left, 26, listWidth, 18,
			Component.translatable("screen.inventory_toolkit.container_search.search"));
		searchBox.setMaxLength(64);
		searchBox.setHint(Component.translatable("screen.inventory_toolkit.container_search.hint"));
		searchBox.setValue(query);
		searchBox.setResponder(value -> refresh());
		addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		int buttonY = height - 26;
		int buttonWidth = (listWidth - 8) / 3;
		addRenderableWidget(Button.builder(
				Component.translatable("screen.inventory_toolkit.container_search.scan"),
				button -> scanNearby()
			)
			.bounds(left, buttonY, buttonWidth, 20)
			.build());
		addRenderableWidget(Button.builder(
				Component.translatable("screen.inventory_toolkit.container_search.clear_highlights"),
				button -> ContainerHighlights.clear()
			)
			.bounds(left + buttonWidth + 4, buttonY, buttonWidth, 20)
			.build());
		addRenderableWidget(Button.builder(
				Component.translatable("screen.inventory_toolkit.done"),
				button -> onClose()
			)
			.bounds(left + (buttonWidth + 4) * 2, buttonY, buttonWidth, 20)
			.build());

		refresh();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);

		int left = (width - listWidth()) / 2;
		int rowWidth = rowWidth();
		if (results.isEmpty()) {
			Component message = ContainerMemory.size() == 0
				? Component.translatable("screen.inventory_toolkit.container_search.empty_memory")
				: Component.translatable("screen.inventory_toolkit.container_search.no_results");
			graphics.centeredText(font, message, width / 2, LIST_TOP + 20, 0xFFAAAAAA);
			if (elsewhere != null) {
				graphics.centeredText(
					font,
					Component.translatable("screen.inventory_toolkit.container_search.found_elsewhere", elsewhere),
					width / 2,
					LIST_TOP + 34,
					0xFFFFC832
				);
			}
		} else {
			graphics.centeredText(
				font,
				Component.translatable("screen.inventory_toolkit.container_search.keys_hint"),
				width / 2,
				height - 38,
				0xFF888888
			);
		}

		int visibleRows = visibleRows();
		Result hoveredResult = null;
		for (int row = 0; row < visibleRows; row++) {
			int index = scrollRow + row;
			if (index >= results.size()) {
				break;
			}
			Result result = results.get(index);
			int y = LIST_TOP + row * ROW_HEIGHT;
			boolean hovered = mouseX >= left && mouseX < left + rowWidth
				&& mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
			if (hovered) {
				hoveredResult = result;
			}
			graphics.fill(left, y, left + rowWidth, y + ROW_HEIGHT - 2, hovered ? 0x90606060 : 0x90303030);
			if (index == selectedIndex) {
				graphics.outline(left, y, rowWidth, ROW_HEIGHT - 2, 0xFFFFC832);
			}
			graphics.item(result.icon(), left + 2, y + 1);

			Component details = details(result);
			int detailsX = left + rowWidth - 4 - font.width(details);
			graphics.text(font, details, detailsX, y + 5, result.plan() == null ? 0xFFAAAAAA : 0xFF7FD47F);
			graphics.text(font, fit(result.name(), detailsX - 6 - (left + 22)), left + 22, y + 5, 0xFFFFFFFF);
		}

		if (results.size() > visibleRows) {
			int trackX = left + listWidth() - SCROLLBAR_WIDTH;
			int trackHeight = visibleRows * ROW_HEIGHT - 2;
			int thumbHeight = Math.max(12, trackHeight * visibleRows / results.size());
			int thumbY = LIST_TOP + (trackHeight - thumbHeight) * scrollRow / maxScroll();
			graphics.fill(trackX, LIST_TOP, trackX + SCROLLBAR_WIDTH, LIST_TOP + trackHeight, 0x90303030);
			graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (hoveredResult != null) {
			graphics.setTooltipForNextFrame(font, tooltipFor(hoveredResult), mouseX, mouseY);
		}
	}

	private static Component details(Result result) {
		if (result.plan() != null) {
			return result.matches().isEmpty()
				? Component.translatable("screen.inventory_toolkit.container_search.row_craft_inventory", result.total())
				: Component.translatable(
					"screen.inventory_toolkit.container_search.row_craft",
					result.total(),
					result.matches().size(),
					Math.round(result.nearestDistance())
				);
		}
		Match nearest = result.matches().getFirst();
		return Component.translatable(
			result.onlyInsideContainers()
				? "screen.inventory_toolkit.container_search.row_nested"
				: "screen.inventory_toolkit.container_search.row",
			result.total(),
			result.matches().size(),
			Math.round(nearest.distance()),
			age(nearest.container().updatedAt())
		);
	}

	/**
	 * One line per container, nearest first: count, distance, and when its
	 * contents were last seen.
	 */
	private List<FormattedCharSequence> tooltipFor(Result result) {
		if (result.plan() != null) {
			return craftTooltipFor(result);
		}
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(Component.literal(result.name()).getVisualOrderText());
		List<Match> matches = result.matches();
		for (int index = 0; index < Math.min(TOOLTIP_CONTAINERS, matches.size()); index++) {
			Match match = matches.get(index);
			BlockPos pos = match.container().blockPos();
			lines.add(Component.translatable(
				"screen.inventory_toolkit.container_search.tooltip_row",
				match.count(),
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				Math.round(match.distance()),
				age(match.container().updatedAt())
			).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}
		if (matches.size() > TOOLTIP_CONTAINERS) {
			lines.add(Component.translatable(
				"screen.inventory_toolkit.container_search.tooltip_more",
				matches.size() - TOOLTIP_CONTAINERS
			).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		}
		lines.add(Component.translatable("screen.inventory_toolkit.container_search.tooltip_click")
			.withStyle(ChatFormatting.YELLOW).getVisualOrderText());
		return lines;
	}

	/**
	 * What each craft takes, what runs out first, and the containers to take
	 * the ingredients from, nearest first.
	 */
	private List<FormattedCharSequence> craftTooltipFor(Result result) {
		StorageCrafting.Plan plan = result.plan();
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(Component.literal(result.name()).getVisualOrderText());
		lines.add(Component.translatable(
			plan.recipe().resultCount() > 1
				? "screen.inventory_toolkit.container_search.craft_count_batches"
				: "screen.inventory_toolkit.container_search.craft_count",
			plan.made(),
			plan.crafts()
		).withStyle(ChatFormatting.GREEN).getVisualOrderText());
		lines.add(Component.translatable("screen.inventory_toolkit.container_search.craft_takes")
			.withStyle(ChatFormatting.GRAY).getVisualOrderText());
		for (StorageCrafting.Ingredient ingredient : plan.recipe().ingredients()) {
			lines.add(Component.translatable(
				"screen.inventory_toolkit.container_search.craft_ingredient",
				ingredient.perCraft(),
				ingredientName(ingredient, plan)
			).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}
		lines.add(Component.translatable(
			"screen.inventory_toolkit.container_search.craft_limit",
			ingredientName(plan.limiting(), plan)
		).withStyle(ChatFormatting.GRAY).getVisualOrderText());

		List<StorageCrafting.Source> sources = result.sources();
		for (int index = 0; index < Math.min(TOOLTIP_CONTAINERS, sources.size()); index++) {
			StorageCrafting.Source source = sources.get(index);
			BlockPos pos = source.container().blockPos();
			lines.add(Component.translatable(
				"screen.inventory_toolkit.container_search.craft_source",
				takenLabel(source),
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				Math.round(source.distance()),
				age(source.container().updatedAt())
			).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}
		if (sources.size() > TOOLTIP_CONTAINERS) {
			lines.add(Component.translatable(
				"screen.inventory_toolkit.container_search.tooltip_more",
				sources.size() - TOOLTIP_CONTAINERS
			).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		}
		lines.add(Component.translatable(sources.isEmpty()
				? "screen.inventory_toolkit.container_search.craft_all_carried"
				: "screen.inventory_toolkit.container_search.craft_click")
			.withStyle(ChatFormatting.YELLOW).getVisualOrderText());
		return lines;
	}

	/**
	 * The item the plan uses for the ingredient, noting when other kinds
	 * (another wood's planks, say) would do as well.
	 */
	private static Component ingredientName(StorageCrafting.Ingredient ingredient, StorageCrafting.Plan plan) {
		Item shown = ingredient.choices().stream()
			.filter(plan.used()::containsKey)
			.findFirst()
			.orElse(ingredient.choices().getFirst());
		Component name = new ItemStack(shown).getHoverName();
		return ingredient.choices().size() > 1
			? Component.translatable("screen.inventory_toolkit.container_search.craft_or_similar", name)
			: name;
	}

	private static String takenLabel(StorageCrafting.Source source) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Item, Integer> taken : source.taken().entrySet()) {
			parts.add("×" + taken.getValue() + " " + new ItemStack(taken.getKey()).getHoverName().getString());
		}
		return String.join(", ", parts);
	}

	private String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
	}

	private static Component age(long updatedAt) {
		long minutes = Math.max(0L, (System.currentTimeMillis() - updatedAt) / 60_000L);
		if (minutes < 1) {
			return Component.translatable("screen.inventory_toolkit.container_search.age.now");
		}
		if (minutes < 60) {
			return Component.translatable("screen.inventory_toolkit.container_search.age.minutes", minutes);
		}
		if (minutes < 60 * 24) {
			return Component.translatable("screen.inventory_toolkit.container_search.age.hours", minutes / 60);
		}
		return Component.translatable("screen.inventory_toolkit.container_search.age.days", minutes / (60 * 24));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int left = (width - listWidth()) / 2;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.x() < left || event.x() >= left + listWidth()
			|| event.y() < LIST_TOP) {
			return false;
		}
		int row = (int) ((event.y() - LIST_TOP) / ROW_HEIGHT);
		if (row >= visibleRows()) {
			return false;
		}
		if (event.x() >= left + rowWidth()) {
			if (results.size() > visibleRows()) {
				draggingScrollbar = true;
				scrollToMouse(event.y());
				return true;
			}
			return false;
		}
		int index = scrollRow + row;
		if (index >= results.size()) {
			return false;
		}
		highlight(results.get(index));
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingScrollbar = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0.0 && results.size() > visibleRows()) {
			scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow + (verticalAmount > 0.0 ? -1 : 1)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	/**
	 * Up and Down move the selection, Page Up and Page Down move it a page,
	 * and Enter highlights the selected result.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!results.isEmpty()) {
			if (event.isUp()) {
				select(selectedIndex - 1);
				return true;
			}
			if (event.isDown()) {
				select(selectedIndex + 1);
				return true;
			}
			if (event.key() == InputConstants.KEY_PAGEUP) {
				select(selectedIndex - visibleRows());
				return true;
			}
			if (event.key() == InputConstants.KEY_PAGEDOWN) {
				select(selectedIndex + visibleRows());
				return true;
			}
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				highlight(results.get(selectedIndex));
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void select(int index) {
		selectedIndex = Math.max(0, Math.min(results.size() - 1, index));
		if (selectedIndex < scrollRow) {
			scrollRow = selectedIndex;
		} else if (selectedIndex >= scrollRow + visibleRows()) {
			scrollRow = selectedIndex - visibleRows() + 1;
		}
	}

	private void scrollToMouse(double mouseY) {
		int trackHeight = visibleRows() * ROW_HEIGHT - 2;
		double fraction = (mouseY - LIST_TOP) / Math.max(1, trackHeight);
		scrollRow = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * maxScroll());
	}

	private int maxScroll() {
		return Math.max(1, results.size() - visibleRows());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (returnToInventory && minecraft.player != null) {
			minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
		} else {
			minecraft.gui.setScreen(null);
		}
	}

	private void highlight(Result result) {
		if (result.plan() != null) {
			highlightIngredients(result);
			return;
		}
		QuickStackConfig config = QuickStackClient.config();
		ContainerHighlights.clear();
		for (Match match : result.matches()) {
			ContainerHighlights.show(match.container(), "×" + match.count(), config.highlightSeconds);
		}
		InventoryToolkitClient.LOGGER.info(
			"Highlighting {} containers holding {}",
			result.matches().size(),
			result.name()
		);
		minecraft.gui.setScreen(null);
		minecraft.gui.hud.setOverlayMessage(Component.translatable(
			"message.inventory_toolkit.container_search.highlighting",
			result.matches().size(),
			result.name()
		), false);
	}

	/**
	 * Outlines the containers to take a craftable result's ingredients from,
	 * each labelled with what to take.
	 */
	private void highlightIngredients(Result result) {
		ContainerHighlights.clear();
		for (StorageCrafting.Source source : result.sources()) {
			ContainerHighlights.show(source.container(), takenLabel(source), QuickStackClient.config().highlightSeconds);
		}
		minecraft.gui.setScreen(null);
		minecraft.gui.hud.setOverlayMessage(result.sources().isEmpty()
			? Component.translatable("message.inventory_toolkit.container_search.craft_all_carried", result.name())
			: Component.translatable(
				"message.inventory_toolkit.container_search.highlighting_ingredients",
				result.sources().size(),
				result.total(),
				result.name()
			), false);
	}

	private void scanNearby() {
		String query = searchBox.getValue();
		boolean reopenInventory = returnToInventory;
		minecraft.gui.setScreen(null);
		QuickStackClient.nearby().start(
			QuickStackClient.config(),
			NearbyQuickStack.Mode.SCAN,
			() -> {
				if (minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new ContainerSearchScreen(reopenInventory, query));
				}
			}
		);
	}

	private void refresh() {
		scrollRow = 0;
		selectedIndex = 0;
		String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		Vec3 playerPosition = minecraft.player == null ? Vec3.ZERO : minecraft.player.position();

		List<ContainerMemory.StoredContainer> containers = new ArrayList<>(ContainerMemory.containersInCurrentDimension());
		// Stored containers compare by their contents, so look them up by identity.
		Map<ContainerMemory.StoredContainer, Double> distances = new IdentityHashMap<>();
		for (ContainerMemory.StoredContainer container : containers) {
			distances.put(container, Math.sqrt(container.blockPos().distToCenterSqr(playerPosition)));
		}
		containers.sort(Comparator.comparingDouble(distances::get));

		Map<String, ResultBuilder> builders = new LinkedHashMap<>();
		for (ContainerMemory.StoredContainer container : containers) {
			double distance = distances.get(container);
			Map<String, Integer> countsInContainer = new LinkedHashMap<>();
			for (ContainerMemory.StoredStack stack : container.items()) {
				if (!matches(stack, query)) {
					continue;
				}
				String key = stack.item() + "|" + stack.name();
				countsInContainer.merge(key, stack.count(), Integer::sum);
				builders.computeIfAbsent(key, ignored -> new ResultBuilder(stack))
					.add(stack);
			}
			for (Map.Entry<String, Integer> entry : countsInContainer.entrySet()) {
				builders.get(entry.getKey()).matches.add(new Match(container, entry.getValue(), distance));
			}
		}

		List<Result> built = new ArrayList<>();
		for (ResultBuilder builder : builders.values()) {
			built.add(builder.build());
		}
		built.sort(Comparator.comparingDouble(Result::nearestDistance).thenComparing(Result::name));
		elsewhere = built.isEmpty() && !query.isEmpty()
			? ContainerMemory.describeOtherDimensions(stack -> matches(stack, query))
			: null;

		if (!query.isEmpty() && QuickStackClient.config().searchCraftable) {
			List<Double> sortedDistances = containers.stream().map(distances::get).toList();
			StorageCrafting.Stock stock = StorageCrafting.stock(minecraft, containers);
			for (StorageCrafting.Plan plan : StorageCrafting.craftable(minecraft, query, stock)) {
				List<StorageCrafting.Source> sources = StorageCrafting.sources(plan, stock, containers, sortedDistances);
				List<Match> sourceMatches = sources.stream()
					.map(source -> new Match(
						source.container(),
						source.taken().values().stream().mapToInt(Integer::intValue).sum(),
						source.distance()
					))
					.toList();
				ItemStack icon = new ItemStack(plan.recipe().result());
				built.add(new Result(
					icon,
					icon.getHoverName().getString(),
					plan.made(),
					false,
					sourceMatches,
					plan,
					sources
				));
			}
		}
		results = built;
	}

	private static boolean matches(ContainerMemory.StoredStack stack, String query) {
		return query.isEmpty()
			|| stack.name().toLowerCase(Locale.ROOT).contains(query)
			|| stack.item().contains(query);
	}

	private int listWidth() {
		return Math.min(320, width - 20);
	}

	/**
	 * The list width less the scrollbar column.
	 */
	private int rowWidth() {
		return listWidth() - SCROLLBAR_WIDTH - 2;
	}

	private int visibleRows() {
		return Math.max(1, (height - LIST_TOP - LIST_BOTTOM_MARGIN - 10) / ROW_HEIGHT);
	}

	private static final class ResultBuilder {
		private final ContainerMemory.StoredStack first;
		private final List<Match> matches = new ArrayList<>();
		private int total;
		private boolean onlyInsideContainers = true;

		private ResultBuilder(ContainerMemory.StoredStack first) {
			this.first = first;
		}

		private void add(ContainerMemory.StoredStack stack) {
			total += stack.count();
			onlyInsideContainers &= stack.nested();
		}

		private Result build() {
			matches.sort(Comparator.comparingDouble(Match::distance));
			Identifier id = Identifier.tryParse(first.item());
			Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
			ItemStack icon = item == null ? ItemStack.EMPTY : new ItemStack(item);
			return new Result(icon, first.name(), total, onlyInsideContainers, List.copyOf(matches), null, List.of());
		}
	}
}
