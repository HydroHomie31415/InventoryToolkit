package dev.luka.inventorytoolkit.config;

import be.luka.quickoffhand.QuickOffhandClient;
import be.luka.quickoffhand.QuickOffhandConfig;
import dev.hydro.hydrosbundles.HydrosBundlesClient;
import dev.hydro.hydrosbundles.HydrosBundlesConfig;
import dev.hydro.hydrosbundles.config.HydrosBundlesSettingsScreen;
import dev.hydro.hydrosrecipebook.HydrosRecipeBookClient;
import dev.hydro.hydrosrecipebook.RecipeBookConfig;
import dev.hydro.hydrosrecipebook.RecipeBookConfigScreen;
import dev.inventorysweeper.InventorySweeperClient;
import dev.inventorysweeper.config.SweeperConfigScreen;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchClient;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfig;
import dev.luka.inventorytoolkit.autotool.AutoToolSwitchConfigScreen;
import dev.luka.inventorytoolkit.compact.CompactClient;
import dev.luka.inventorytoolkit.compact.CompactConfig;
import dev.luka.inventorytoolkit.compact.CompactConfigScreen;
import dev.luka.inventorytoolkit.dropprotection.DropProtectionClient;
import dev.luka.inventorytoolkit.dropprotection.DropProtectionConfigScreen;
import dev.luka.inventorytoolkit.inventorysort.InventorySortClient;
import dev.luka.inventorytoolkit.inventorysort.InventorySortConfigScreen;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import dev.luka.inventorytoolkit.quickstack.QuickStackConfig;
import dev.luka.inventorytoolkit.quickstack.QuickStackConfigScreen;
import dev.luka.inventorytoolkit.quicktrade.QuickTradeClient;
import dev.luka.inventorytoolkit.quicktrade.QuickTradeConfig;
import dev.luka.inventorytoolkit.quicktrade.QuickTradeConfigScreen;
import dev.luka.inventorytoolkit.shulkerpreview.ShulkerPreviewClient;
import dev.luka.inventorytoolkit.shulkerpreview.ShulkerPreviewConfigScreen;
import dev.luka.inventorytoolkit.slotlock.SlotLockClient;
import dev.luka.inventorytoolkit.slotlock.SlotLockConfigScreen;
import dev.luka.shiftdragtransfer.ShiftDragTransferClient;
import dev.luka.shiftdragtransfer.ShiftDragTransferConfig;
import dev.luka.shiftdragtransfer.config.ShiftDragTransferConfigScreen;
import dev.luka.smarthotbar.LoadoutScreen;
import dev.luka.smarthotbar.SmartHotbarClient;
import dev.luka.stackrefill.StackRefillClient;
import dev.luka.stackrefill.config.StackRefillConfigScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The configuration hub. Modules are grouped by where they act, and each one
 * has an on/off switch next to the button that opens its settings.
 */
public final class InventoryToolkitScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 3;
	private static final int GRID_BUTTON_WIDTH = 190;
	private static final int MIN_GRID_BUTTON_WIDTH = 140;
	private static final int GRID_GAP = 8;
	private static final int TOGGLE_WIDTH = 20;
	private static final int TOGGLE_GAP = 2;
	private static final int HEADER_HEIGHT = 13;

	private record Module(
		String labelKey,
		String descriptionKey,
		UnaryOperator<Screen> settings,
		BooleanSupplier enabled,
		Consumer<Boolean> setEnabled
	) {
	}

	private record Group(String titleKey, List<Module> modules) {
	}

	private record Header(Component title, int y) {
	}

	private final Screen parent;
	private final List<Group> groups;
	private final List<Header> headers = new ArrayList<>();

	public InventoryToolkitScreen(Screen parent) {
		super(Component.translatable("screen.inventory_toolkit.title"));
		this.parent = parent;
		this.groups = createGroups();
	}

	private static List<Group> createGroups() {
		return List.of(
			new Group("screen.inventory_toolkit.group.world", List.of(
				new Module(
					"screen.inventory_toolkit.auto_tool",
					"screen.inventory_toolkit.auto_tool.description",
					AutoToolSwitchConfigScreen::create,
					() -> AutoToolSwitchClient.config().enabled,
					value -> {
						AutoToolSwitchConfig config = AutoToolSwitchClient.config();
						config.enabled = value;
						config.save();
						AutoToolSwitchClient.resetSession();
					}
				),
				new Module(
					"screen.inventory_toolkit.refill",
					"screen.inventory_toolkit.refill.description",
					StackRefillConfigScreen::create,
					() -> StackRefillClient.config().enabled,
					StackRefillClient::setEnabled
				),
				new Module(
					"screen.inventory_toolkit.sweeper",
					"screen.inventory_toolkit.sweeper.description",
					SweeperConfigScreen::create,
					() -> InventorySweeperClient.config().enabled,
					InventorySweeperClient::setEnabled
				),
				new Module(
					"screen.inventory_toolkit.offhand_swapper",
					"screen.inventory_toolkit.offhand_swapper.description",
					QuickOffhandClient::createConfigScreen,
					() -> QuickOffhandClient.config().enabled,
					value -> {
						QuickOffhandConfig config = QuickOffhandClient.config();
						config.enabled = value;
						config.save();
						QuickOffhandClient.onConfigChanged();
					}
				),
				new Module(
					"screen.inventory_toolkit.drop_protection",
					"screen.inventory_toolkit.drop_protection.description",
					DropProtectionConfigScreen::create,
					() -> DropProtectionClient.config().enabled,
					value -> {
						DropProtectionClient.config().enabled = value;
						DropProtectionClient.onConfigChanged();
					}
				)
			)),
			new Group("screen.inventory_toolkit.group.inventory", List.of(
				new Module(
					"screen.inventory_toolkit.quick_stack",
					"screen.inventory_toolkit.quick_stack.description",
					QuickStackConfigScreen::create,
					() -> {
						QuickStackConfig config = QuickStackClient.config();
						return config.enabled || config.sortEnabled || config.memoryEnabled;
					},
					value -> {
						QuickStackConfig config = QuickStackClient.config();
						QuickStackClient.nearby().cancel();
						config.enabled = value;
						config.sortEnabled = value;
						config.memoryEnabled = value;
						config.save();
					}
				),
				new Module(
					"screen.inventory_toolkit.transfers",
					"screen.inventory_toolkit.transfers.description",
					ShiftDragTransferConfigScreen::create,
					() -> ShiftDragTransferClient.config().enabled,
					value -> {
						ShiftDragTransferConfig config = ShiftDragTransferClient.config();
						config.enabled = value;
						config.save();
					}
				),
				new Module(
					"screen.inventory_toolkit.loadouts",
					"screen.inventory_toolkit.loadouts.description",
					parent -> new LoadoutScreen(parent, SmartHotbarClient.CONFIG),
					() -> SmartHotbarClient.CONFIG.isEnabled(),
					value -> {
						if (SmartHotbarClient.CONFIG.isEnabled() != value) {
							SmartHotbarClient.CONFIG.toggleEnabled();
						}
					}
				),
				new Module(
					"screen.inventory_toolkit.quick_trade",
					"screen.inventory_toolkit.quick_trade.description",
					QuickTradeConfigScreen::create,
					() -> QuickTradeClient.config().enabled,
					value -> {
						QuickTradeConfig config = QuickTradeClient.config();
						QuickTradeClient.trader().cancel();
						config.enabled = value;
						config.save();
					}
				),
				new Module(
					"screen.inventory_toolkit.bundles",
					"screen.inventory_toolkit.bundles.description",
					HydrosBundlesSettingsScreen::new,
					() -> HydrosBundlesClient.config().enabled,
					value -> {
						HydrosBundlesConfig config = HydrosBundlesClient.config();
						config.enabled = value;
						config.save();
						HydrosBundlesClient.reloadManagerConfig();
					}
				),
				new Module(
					"screen.inventory_toolkit.inventory_sort",
					"screen.inventory_toolkit.inventory_sort.description",
					InventorySortConfigScreen::create,
					() -> InventorySortClient.config().enabled,
					value -> {
						InventorySortClient.config().enabled = value;
						InventorySortClient.config().save();
					}
				),
				new Module(
					"screen.inventory_toolkit.slot_lock",
					"screen.inventory_toolkit.slot_lock.description",
					SlotLockConfigScreen::create,
					() -> SlotLockClient.config().enabled,
					value -> {
						SlotLockClient.config().enabled = value;
						SlotLockClient.onConfigChanged();
					}
				),
				new Module(
					"screen.inventory_toolkit.shulker_preview",
					"screen.inventory_toolkit.shulker_preview.description",
					ShulkerPreviewConfigScreen::create,
					() -> ShulkerPreviewClient.config().enabled,
					value -> {
						ShulkerPreviewClient.config().enabled = value;
						ShulkerPreviewClient.config().save();
					}
				)
			)),
			new Group("screen.inventory_toolkit.group.crafting", List.of(
				new Module(
					"screen.inventory_toolkit.recipe_book",
					"screen.inventory_toolkit.recipe_book.description",
					RecipeBookConfigScreen::create,
					HydrosRecipeBookClient::isEnabled,
					value -> {
						RecipeBookConfig config = HydrosRecipeBookClient.config();
						config.enabled = value;
						config.save();
					}
				),
				new Module(
					"screen.inventory_toolkit.compact",
					"screen.inventory_toolkit.compact.description",
					CompactConfigScreen::create,
					() -> CompactClient.config().enabled,
					value -> {
						CompactConfig config = CompactClient.config();
						CompactClient.compactor().cancel();
						config.enabled = value;
						config.save();
					}
				)
			))
		);
	}

	@Override
	protected void init() {
		headers.clear();
		int columns = width >= 320 ? 2 : 1;
		// A third column keeps every module on screen at large GUI scales.
		if (columns == 2
			&& 50 + contentHeight(2) + 27 > height
			&& (width - GRID_GAP * 2 - 12) / 3 >= MIN_GRID_BUTTON_WIDTH) {
			columns = 3;
		}
		int cellWidth = columns > 1
			? Math.min(GRID_BUTTON_WIDTH, (width - GRID_GAP * (columns - 1) - 12) / columns)
			: Math.min(BUTTON_WIDTH, width - 12);
		int gridWidth = cellWidth * columns + GRID_GAP * (columns - 1);
		int firstX = width / 2 - gridWidth / 2;

		int contentHeight = contentHeight(columns);
		int y = Math.max(50, height / 2 - (contentHeight + 27) / 2);

		for (Group group : groups) {
			headers.add(new Header(Component.translatable(group.titleKey()), y));
			y += HEADER_HEIGHT;
			List<Module> modules = group.modules();
			for (int index = 0; index < modules.size(); index++) {
				int x = firstX + (index % columns) * (cellWidth + GRID_GAP);
				int rowY = y + (index / columns) * (BUTTON_HEIGHT + GAP);
				addModule(modules.get(index), x, rowY, cellWidth);
			}
			y += (modules.size() + columns - 1) / columns * (BUTTON_HEIGHT + GAP);
		}

		addRenderableWidget(Button.builder(
			Component.translatable("screen.inventory_toolkit.done"),
			button -> onClose()
		).bounds(width / 2 - 70, y + 7, 140, 20).build());
	}

	private int contentHeight(int columns) {
		int contentHeight = 0;
		for (Group group : groups) {
			int rows = (group.modules().size() + columns - 1) / columns;
			contentHeight += HEADER_HEIGHT + rows * (BUTTON_HEIGHT + GAP);
		}
		return contentHeight;
	}

	private void addModule(Module module, int x, int y, int cellWidth) {
		boolean enabled = module.enabled().getAsBoolean();
		Component name = Component.translatable(module.labelKey());

		Button toggle = Button.builder(
			Component.literal(enabled ? "✔" : "✖")
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
			button -> {
				module.setEnabled().accept(!module.enabled().getAsBoolean());
				rebuildWidgets();
			}
		).bounds(x, y, TOGGLE_WIDTH, BUTTON_HEIGHT).build();
		toggle.setTooltip(Tooltip.create(Component.translatable(
			enabled ? "screen.inventory_toolkit.toggle.on" : "screen.inventory_toolkit.toggle.off",
			name
		)));
		addRenderableWidget(toggle);

		Button settings = Button.builder(
			enabled ? name : name.copy().withStyle(ChatFormatting.GRAY),
			button -> minecraft.gui.setScreen(module.settings().apply(this))
		).bounds(x + TOGGLE_WIDTH + TOGGLE_GAP, y, cellWidth - TOGGLE_WIDTH - TOGGLE_GAP, BUTTON_HEIGHT).build();
		settings.setTooltip(Tooltip.create(Component.translatable(module.descriptionKey())));
		addRenderableWidget(settings);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xD0101010);
		graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable("screen.inventory_toolkit.subtitle"),
			width / 2,
			32,
			0xFFAAAAAA
		);
		for (Header header : headers) {
			graphics.centeredText(font, header.title(), width / 2, header.y() + 2, 0xFFFFC832);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
