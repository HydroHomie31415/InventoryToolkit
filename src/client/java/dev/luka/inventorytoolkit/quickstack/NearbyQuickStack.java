package dev.luka.inventorytoolkit.quickstack;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.containersearch.ContainerHighlights;
import dev.luka.inventorytoolkit.containersearch.ContainerMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Visits every chest, barrel, and shulker box within reach. Each container is
 * opened with an ordinary right-click; the menu the server sends back is kept
 * without a screen (see ClientPacketListenerMixin), recorded in the container
 * memory, optionally filled with matching items, and closed before the next
 * container is opened. Sorting runs several such rounds, planned by
 * {@link ChestSorter}. A sort preview only runs the first, reading round and
 * labels every container with the category it would get. A sort also opens
 * the player's Traveler's Backpack, if any, before the containers of a round
 * (see FabricMenuClientNetworkingMixin).
 */
public final class NearbyQuickStack {
	public enum Mode {
		QUICK_STACK,
		SCAN,
		SORT,
		SORT_PREVIEW;

		private String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final int OPEN_TIMEOUT_TICKS = 20;
	private static final int CONTENT_TIMEOUT_TICKS = 20;
	public static final int PREVIEW_CONFIRM_SECONDS = 20;

	private final Minecraft client;
	private final Deque<BlockPos> pendingContainers = new ArrayDeque<>();
	private AbstractContainerMenu silentMenu;
	private boolean silentMenuIsBackpack;
	private boolean backpackQueued;
	private boolean openingBackpack;
	private Component silentMenuTitle = Component.empty();
	private BlockPos openingPos;
	private ChestSorter sorter;
	private Mode mode = Mode.QUICK_STACK;
	private Runnable onFinished;
	private Screen allowedScreen;
	private boolean running;
	private long tickCounter;
	private long nextActionTick;
	private long waitDeadlineTick = -1;
	private int containersFilled;
	private int containersScanned;
	private int itemsMoved;
	private int round;
	private int roundTotal;
	private int roundVisited;
	private long previewConfirmUntilTick = -1;
	private boolean previewHighlightsShown;

	public NearbyQuickStack(Minecraft client) {
		this.client = client;
	}

	public boolean isRunning() {
		return running;
	}

	public Mode mode() {
		return mode;
	}

	/**
	 * Whether a sort preview was just shown, so the next sort request runs
	 * the sort itself.
	 */
	public boolean isSortPreviewPending() {
		return !running && previewConfirmUntilTick > tickCounter;
	}

	/**
	 * Sorts nearby containers, or first shows a preview when previews are
	 * enabled and none is waiting for confirmation.
	 */
	public void startSort(QuickStackConfig config) {
		start(config, config.sortPreview && !isSortPreviewPending() ? Mode.SORT_PREVIEW : Mode.SORT, null);
	}

	public void start(QuickStackConfig config) {
		start(config, Mode.QUICK_STACK, null);
	}

	/**
	 * May be started in the world or from the inventory screen; that screen then
	 * stays open for the whole run. Opening any other screen cancels the run.
	 *
	 * @param onFinished run after a run completes normally (not when cancelled)
	 */
	public void start(QuickStackConfig config, Mode mode, Runnable onFinished) {
		LocalPlayer player = client.player;
		Screen screen = client.gui.screen();
		if (running
			|| (screen != null && !(screen instanceof InventoryScreen))
			|| player == null
			|| client.level == null
			|| client.gameMode == null
			|| player.isSpectator()) {
			return;
		}
		// Messages explaining why nothing happens are shown even when summary
		// messages are turned off.
		if (!isModeEnabled(config, mode)) {
			explain(Component.translatable("message.inventory_toolkit.disabled." + mode.key()));
			return;
		}
		if (player.containerMenu != player.inventoryMenu) {
			explain(Component.translatable("message.inventory_toolkit.busy"));
			return;
		}
		if (!player.inventoryMenu.getCarried().isEmpty()) {
			explain(Component.translatable("message.inventory_toolkit.cursor_not_empty"));
			return;
		}

		boolean sorting = mode == Mode.SORT || mode == Mode.SORT_PREVIEW;
		// Without a free slot nothing can be carried, though the player's own
		// items can still be put away.
		if (sorting && !config.sortInventory && player.getInventory().getFreeSlot() < 0) {
			explain(Component.translatable("message.inventory_toolkit.sort.inventory_full"));
			return;
		}

		List<BlockPos> containers = findContainers(client.level, player);
		if (sorting && !config.sortIncludeShulkerBoxes) {
			containers.removeIf(pos -> client.level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity);
		}
		pendingContainers.clear();
		pendingContainers.addAll(containers);
		if (pendingContainers.isEmpty()) {
			explain(Component.translatable("message.inventory_toolkit.quick_stack.no_containers"));
			return;
		}
		sorter = sorting ? new ChestSorter(client, containers, config) : null;
		backpackQueued = sorting && config.sortIncludeBackpack && TravelersBackpack.isLoaded();
		openingBackpack = false;
		previewConfirmUntilTick = -1;
		if (previewHighlightsShown) {
			previewHighlightsShown = false;
			ContainerHighlights.clear();
		}

		running = true;
		this.mode = mode;
		this.onFinished = onFinished;
		allowedScreen = screen;
		containersFilled = 0;
		containersScanned = 0;
		itemsMoved = 0;
		round = 0;
		roundTotal = containers.size();
		roundVisited = 0;
		nextActionTick = tickCounter;
		waitDeadlineTick = -1;
	}

	/**
	 * Cancels a run the player asked to stop, and says so.
	 */
	public void cancelByPlayer() {
		if (running) {
			Mode cancelled = mode;
			cancel();
			explain(Component.translatable("message.inventory_toolkit.cancelled." + cancelled.key()));
		}
	}

	public void cancel() {
		LocalPlayer player = client.player;
		if (player != null && silentMenu != null && player.containerMenu == silentMenu) {
			closeSilently(player);
		}
		silentMenu = null;
		silentMenuIsBackpack = false;
		sorter = null;
		pendingContainers.clear();
		backpackQueued = false;
		openingBackpack = false;
		running = false;
		onFinished = null;
		allowedScreen = null;
		waitDeadlineTick = -1;
	}

	/**
	 * Called for every container screen the server opens. Returns true when the
	 * menu was taken over by an active run and no screen should be shown.
	 */
	public boolean openSilently(MenuType<?> type, int containerId, Component title) {
		LocalPlayer player = client.player;
		if (!running || player == null || !isSupportedMenuType(type)) {
			return false;
		}

		silentMenu = type.create(containerId, player.getInventory());
		silentMenuTitle = title;
		player.containerMenu = silentMenu;
		waitDeadlineTick = tickCounter + CONTENT_TIMEOUT_TICKS;
		return true;
	}

	/**
	 * Whether a backpack menu that is being opened belongs to an active run.
	 */
	public boolean expectsBackpack(MenuType<?> type) {
		return running && openingBackpack && client.player != null && TravelersBackpack.isBackpackMenu(type);
	}

	/**
	 * Takes over the backpack menu {@link #expectsBackpack} asked for.
	 */
	public void openBackpackSilently(AbstractContainerMenu menu) {
		openingBackpack = false;
		silentMenu = menu;
		silentMenuIsBackpack = true;
		client.player.containerMenu = menu;
		waitDeadlineTick = tickCounter + CONTENT_TIMEOUT_TICKS;
	}

	public void claimTick() {
		if (running) {
			InventoryActionCoordinator.claimManual(InventoryActionCoordinator.QUICK_STACK);
		}
	}

	public void tick(QuickStackConfig config) {
		tickCounter++;
		if (!running) {
			return;
		}

		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			silentMenu = null;
			cancel();
			return;
		}
		claimTick();
		if (!isModeEnabled(config, mode)
			|| player.isSpectator()
			|| !isScreenAllowed(client.gui.screen())) {
			cancel();
			return;
		}

		if (silentMenu != null) {
			if (player.containerMenu != silentMenu) {
				// The server closed the container before it could be filled.
				silentMenu = null;
				silentMenuIsBackpack = false;
				waitForNextContainer(config);
				return;
			}
			// A state ID of zero means the container contents have not arrived yet.
			if (silentMenu.getStateId() == 0 && tickCounter < waitDeadlineTick) {
				return;
			}

			boolean contentsArrived = silentMenu.getStateId() != 0;
			if (silentMenuIsBackpack) {
				if (sorter != null) {
					sorter.visitBackpack(silentMenu, contentsArrived);
				}
			} else {
				if (contentsArrived) {
					containersScanned++;
				}
				if (mode == Mode.QUICK_STACK) {
					int moved = QuickStackDepositor.deposit(client, silentMenu, config);
					if (moved > 0) {
						containersFilled++;
						itemsMoved += moved;
					}
				} else if (sorter != null) {
					sorter.visit(openingPos, silentMenu, silentMenuTitle, contentsArrived);
				}
				ContainerMemory.capture(silentMenu);
			}
			closeSilently(player);
			waitForNextContainer(config);
			return;
		}

		if (waitDeadlineTick >= 0) {
			if (tickCounter < waitDeadlineTick) {
				return;
			}
			// The container never opened (blocked lid, protected claim, lag).
			waitDeadlineTick = -1;
			openingBackpack = false;
		}
		if (tickCounter < nextActionTick || player.isSecondaryUseActive()) {
			return;
		}

		if (backpackQueued) {
			backpackQueued = false;
			if (TravelersBackpack.requestOpen(player)) {
				openingBackpack = true;
				waitDeadlineTick = tickCounter + OPEN_TIMEOUT_TICKS;
				return;
			}
		}

		while (!pendingContainers.isEmpty()) {
			BlockPos pos = pendingContainers.removeFirst();
			roundVisited++;
			BlockHitResult hit = targetFor(client.level, player, pos, config);
			if (hit == null) {
				continue;
			}

			// Mirrors vanilla right-click handling in Minecraft#startUseItem.
			InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
			if (result instanceof InteractionResult.Success success
				&& success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
				player.swing(
					InteractionHand.MAIN_HAND,
					player.getMainHandItem().getInteractAnimation(),
					false
				);
			}
			openingPos = pos;
			waitDeadlineTick = tickCounter + OPEN_TIMEOUT_TICKS;
			notifyProgress(config);
			return;
		}
		if (mode == Mode.SORT) {
			pendingContainers.addAll(sorter.nextRound());
			backpackQueued = config.sortIncludeBackpack && sorter.visitsBackpack();
			if (!pendingContainers.isEmpty() || backpackQueued) {
				round++;
				roundTotal = pendingContainers.size();
				roundVisited = 0;
				return;
			}
		}
		finish(config);
	}

	private void waitForNextContainer(QuickStackConfig config) {
		waitDeadlineTick = -1;
		nextActionTick = tickCounter + config.containerDelayTicks;
	}

	/**
	 * Closing the inventory mid-run is fine; the run carries on in the world.
	 */
	private boolean isScreenAllowed(Screen screen) {
		return screen == null || screen == allowedScreen;
	}

	private void finish(QuickStackConfig config) {
		running = false;
		allowedScreen = null;
		Runnable callback = onFinished;
		onFinished = null;
		if (mode == Mode.SCAN) {
			notifyPlayer(config, Component.translatable(
				"message.inventory_toolkit.container_search.scanned",
				containersScanned
			));
		} else if (mode == Mode.SORT) {
			notifySortFinished(config);
		} else if (mode == Mode.SORT_PREVIEW) {
			showSortPreview();
		} else if (itemsMoved > 0) {
			notifyPlayer(config, Component.translatable(
				"message.inventory_toolkit.quick_stack.finished",
				itemsMoved,
				containersFilled
			));
		} else {
			notifyPlayer(config, Component.translatable(
				"message.inventory_toolkit.quick_stack.nothing_matched"
			));
		}
		sorter = null;
		if (callback != null) {
			callback.run();
		}
	}

	private void notifySortFinished(QuickStackConfig config) {
		int leftOver = sorter.itemsLeftInInventory();
		if (leftOver > 0) {
			notifyPlayer(config, Component.translatable(
				"message.inventory_toolkit.sort.finished_left_over",
				sorter.itemsMoved(),
				leftOver
			));
		} else if (sorter.itemsMoved() > 0) {
			notifyPlayer(config, Component.translatable(
				"message.inventory_toolkit.sort.finished",
				sorter.itemsMoved(),
				sorter.containersSorted()
			));
		} else {
			notifyPlayer(config, Component.translatable("message.inventory_toolkit.sort.nothing_to_sort"));
		}
	}

	/**
	 * Labels every read container with the category it would get and waits
	 * for the player to confirm.
	 */
	private void showSortPreview() {
		Map<BlockPos, SortCategory> plan = sorter.preview();
		ContainerHighlights.clear();
		previewHighlightsShown = true;
		Component unchanged = Component.translatable("sort_category.inventory_toolkit.unchanged");
		for (Map.Entry<BlockPos, SortCategory> entry : plan.entrySet()) {
			BlockPos pos = entry.getKey();
			Component label = entry.getValue() == null ? unchanged : entry.getValue().displayName();
			ContainerHighlights.show(
				pos,
				client.level == null ? null : chestPartner(client.level.getBlockState(pos), pos),
				label.getString(),
				PREVIEW_CONFIRM_SECONDS
			);
		}

		int items = sorter.itemsToMove();
		if (items == 0) {
			explain(Component.translatable("message.inventory_toolkit.sort.nothing_to_sort"));
			return;
		}
		previewConfirmUntilTick = tickCounter + PREVIEW_CONFIRM_SECONDS * 20L;
		explain(Component.translatable(
			"message.inventory_toolkit.sort.preview",
			items,
			plan.size(),
			PREVIEW_CONFIRM_SECONDS
		));
	}

	private void notifyProgress(QuickStackConfig config) {
		String key = mode == Mode.SORT && round > 0 ? "sort_moving" : mode.key();
		notifyPlayer(config, Component.translatable(
			"message.inventory_toolkit.progress." + key,
			roundVisited,
			roundTotal
		));
	}

	private static boolean isModeEnabled(QuickStackConfig config, Mode mode) {
		return switch (mode) {
			case QUICK_STACK -> config.enabled && config.nearbyEnabled;
			case SCAN -> config.memoryEnabled;
			case SORT, SORT_PREVIEW -> config.sortEnabled;
		};
	}

	private void closeSilently(LocalPlayer player) {
		player.connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
		if (silentMenuIsBackpack) {
			// As its screen would on closing, so the backpack forgets this user.
			player.containerMenu.removed(player);
		}
		player.containerMenu = player.inventoryMenu;
		silentMenu = null;
		silentMenuIsBackpack = false;
	}

	private void notifyPlayer(QuickStackConfig config, Component message) {
		if (config.showMessages) {
			explain(message);
		}
	}

	private void explain(Component message) {
		if (client.player != null) {
			client.gui.hud.setOverlayMessage(message, false);
		}
	}

	private static List<BlockPos> findContainers(Level level, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		BlockPos origin = BlockPos.containing(eye);
		int radius = (int) Math.ceil(player.blockInteractionRange()) + 1;
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
			origin.offset(-radius, -radius, -radius),
			origin.offset(radius, radius, radius)
		)) {
			if (player.isWithinBlockInteractionRange(pos, 0.0)
				&& isSupportedContainer(level.getBlockEntity(pos))) {
				candidates.add(pos.immutable());
			}
		}
		candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eye)));

		// Both halves of a double chest open the same container; keep the nearer one.
		Set<BlockPos> covered = new HashSet<>();
		List<BlockPos> containers = new ArrayList<>();
		for (BlockPos pos : candidates) {
			if (!covered.add(pos)) {
				continue;
			}
			BlockPos partner = chestPartner(level.getBlockState(pos), pos);
			if (partner != null) {
				covered.add(partner);
			}
			containers.add(pos);
		}
		return containers;
	}

	private static BlockHitResult targetFor(
		Level level,
		LocalPlayer player,
		BlockPos pos,
		QuickStackConfig config
	) {
		if (!player.isWithinBlockInteractionRange(pos, 0.0)
			|| !isSupportedContainer(level.getBlockEntity(pos))) {
			return null;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos);
		BlockHitResult sightLine = level.clip(new ClipContext(
			eye,
			center,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			player
		));
		BlockPos partner = chestPartner(level.getBlockState(pos), pos);
		boolean visible = sightLine.getType() == HitResult.Type.BLOCK
			&& (sightLine.getBlockPos().equals(pos) || sightLine.getBlockPos().equals(partner));
		if (visible) {
			return sightLine;
		}
		if (config.requireLineOfSight) {
			return null;
		}

		Vec3 toEye = eye.subtract(center);
		Direction face = Direction.getApproximateNearest(toEye.x, toEye.y, toEye.z);
		Vec3 location = center.add(face.getUnitVec3().scale(0.5));
		return new BlockHitResult(location, face, pos, false);
	}

	public static BlockPos chestPartner(BlockState state, BlockPos pos) {
		if (!(state.getBlock() instanceof ChestBlock)
			|| !state.hasProperty(ChestBlock.TYPE)
			|| state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
			return null;
		}
		return pos.relative(ChestBlock.getConnectedDirection(state));
	}

	public static boolean isSupportedContainer(BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity
			|| blockEntity instanceof BarrelBlockEntity
			|| blockEntity instanceof ShulkerBoxBlockEntity;
	}

	public static boolean isSupportedMenuType(MenuType<?> type) {
		return type == MenuType.GENERIC_9x3
			|| type == MenuType.GENERIC_9x6
			|| type == MenuType.SHULKER_BOX;
	}
}
