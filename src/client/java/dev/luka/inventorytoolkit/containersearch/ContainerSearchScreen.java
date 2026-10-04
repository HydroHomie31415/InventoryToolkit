package dev.luka.inventorytoolkit.containersearch;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.quickstack.NearbyQuickStack;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import dev.luka.inventorytoolkit.quickstack.QuickStackConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ContainerSearchScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int LIST_TOP = 50;
	private static final int LIST_BOTTOM_MARGIN = 34;

	private final boolean returnToInventory;
	private final String initialQuery;
	private EditBox searchBox;
	private List<Result> results = List.of();
	private int scrollRow;

	private record Match(ContainerMemory.StoredContainer container, int count, double distance) {
	}

	private record Result(
		ItemStack icon,
		String name,
		int total,
		boolean onlyInsideContainers,
		List<Match> matches
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
		if (results.isEmpty()) {
			Component message = ContainerMemory.size() == 0
				? Component.translatable("screen.inventory_toolkit.container_search.empty_memory")
				: Component.translatable("screen.inventory_toolkit.container_search.no_results");
			graphics.centeredText(font, message, width / 2, LIST_TOP + 20, 0xFFAAAAAA);
		}

		int visibleRows = visibleRows();
		for (int row = 0; row < visibleRows; row++) {
			int index = scrollRow + row;
			if (index >= results.size()) {
				break;
			}
			Result result = results.get(index);
			int y = LIST_TOP + row * ROW_HEIGHT;
			boolean hovered = mouseX >= left && mouseX < left + listWidth()
				&& mouseY >= y && mouseY < y + ROW_HEIGHT;
			graphics.fill(left, y, left + listWidth(), y + ROW_HEIGHT - 2, hovered ? 0x90606060 : 0x90303030);
			graphics.item(result.icon(), left + 2, y + 1);
			graphics.text(font, result.name(), left + 22, y + 5, 0xFFFFFFFF);

			Component details = Component.translatable(
				result.onlyInsideContainers()
					? "screen.inventory_toolkit.container_search.row_nested"
					: "screen.inventory_toolkit.container_search.row",
				result.total(),
				result.matches().size(),
				Math.round(result.nearestDistance())
			);
			graphics.text(
				font,
				details,
				left + listWidth() - 4 - font.width(details),
				y + 5,
				0xFFAAAAAA
			);
		}

		if (results.size() > visibleRows) {
			graphics.centeredText(
				font,
				Component.translatable(
					"screen.inventory_toolkit.container_search.scroll",
					scrollRow + 1,
					Math.min(results.size(), scrollRow + visibleRows),
					results.size()
				),
				width / 2,
				LIST_TOP + visibleRows * ROW_HEIGHT + 1,
				0xFF888888
			);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
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
		int index = scrollRow + row;
		if (row >= visibleRows() || index >= results.size()) {
			return false;
		}
		highlight(results.get(index));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0.0 && results.size() > visibleRows()) {
			int maximum = results.size() - visibleRows();
			scrollRow = Math.max(0, Math.min(maximum, scrollRow + (verticalAmount > 0.0 ? -1 : 1)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
		String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		Vec3 playerPosition = minecraft.player == null ? Vec3.ZERO : minecraft.player.position();

		Map<String, ResultBuilder> builders = new LinkedHashMap<>();
		for (ContainerMemory.StoredContainer container : ContainerMemory.containersInCurrentDimension()) {
			double distance = Math.sqrt(container.blockPos().distToCenterSqr(playerPosition));
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
			return new Result(icon, first.name(), total, onlyInsideContainers, List.copyOf(matches));
		}
	}
}
