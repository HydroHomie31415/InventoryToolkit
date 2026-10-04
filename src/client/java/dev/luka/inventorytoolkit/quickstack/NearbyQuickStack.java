package dev.luka.inventorytoolkit.quickstack;

import dev.luka.inventorytoolkit.InventoryActionCoordinator;
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
import java.util.Set;

/**
 * Visits every chest, barrel, and shulker box within reach. Each container is
 * opened with an ordinary right-click; the menu the server sends back is kept
 * without a screen (see ClientPacketListenerMixin), recorded in the container
 * memory, optionally filled with matching items, and closed before the next
 * container is opened.
 */
public final class NearbyQuickStack {
	public enum Mode {
		QUICK_STACK,
		SCAN
	}

	private static final int OPEN_TIMEOUT_TICKS = 20;
	private static final int CONTENT_TIMEOUT_TICKS = 20;

	private final Minecraft client;
	private final Deque<BlockPos> pendingContainers = new ArrayDeque<>();
	private AbstractContainerMenu silentMenu;
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

	public NearbyQuickStack(Minecraft client) {
		this.client = client;
	}

	public boolean isRunning() {
		return running;
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
			|| !isModeEnabled(config, mode)
			|| player == null
			|| client.level == null
			|| client.gameMode == null
			|| player.isSpectator()) {
			return;
		}
		if (player.containerMenu != player.inventoryMenu
			|| !player.inventoryMenu.getCarried().isEmpty()) {
			notifyPlayer(config, Component.translatable("message.inventory_toolkit.busy"));
			return;
		}

		pendingContainers.clear();
		pendingContainers.addAll(findContainers(client.level, player));
		if (pendingContainers.isEmpty()) {
			notifyPlayer(config, Component.translatable(
				"message.inventory_toolkit.quick_stack.no_containers"
			));
			return;
		}

		running = true;
		this.mode = mode;
		this.onFinished = onFinished;
		allowedScreen = screen;
		containersFilled = 0;
		containersScanned = 0;
		itemsMoved = 0;
		nextActionTick = tickCounter;
		waitDeadlineTick = -1;
	}

	public void cancel() {
		LocalPlayer player = client.player;
		if (player != null && silentMenu != null && player.containerMenu == silentMenu) {
			closeSilently(player);
		}
		silentMenu = null;
		pendingContainers.clear();
		running = false;
		onFinished = null;
		allowedScreen = null;
		waitDeadlineTick = -1;
	}

	/**
	 * Called for every container screen the server opens. Returns true when the
	 * menu was taken over by an active run and no screen should be shown.
	 */
	public boolean openSilently(MenuType<?> type, int containerId) {
		LocalPlayer player = client.player;
		if (!running || player == null || !isSupportedMenuType(type)) {
			return false;
		}

		silentMenu = type.create(containerId, player.getInventory());
		player.containerMenu = silentMenu;
		waitDeadlineTick = tickCounter + CONTENT_TIMEOUT_TICKS;
		return true;
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
				waitForNextContainer(config);
				return;
			}
			// A state ID of zero means the container contents have not arrived yet.
			if (silentMenu.getStateId() == 0 && tickCounter < waitDeadlineTick) {
				return;
			}

			if (silentMenu.getStateId() != 0) {
				containersScanned++;
			}
			if (mode == Mode.QUICK_STACK) {
				int moved = QuickStackDepositor.deposit(client, silentMenu, config);
				if (moved > 0) {
					containersFilled++;
					itemsMoved += moved;
				}
			}
			ContainerMemory.capture(silentMenu);
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
		}
		if (tickCounter < nextActionTick || player.isSecondaryUseActive()) {
			return;
		}

		while (!pendingContainers.isEmpty()) {
			BlockPos pos = pendingContainers.removeFirst();
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
			waitDeadlineTick = tickCounter + OPEN_TIMEOUT_TICKS;
			return;
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
		if (callback != null) {
			callback.run();
		}
	}

	private static boolean isModeEnabled(QuickStackConfig config, Mode mode) {
		return mode == Mode.SCAN
			? config.memoryEnabled
			: config.enabled && config.nearbyEnabled;
	}

	private void closeSilently(LocalPlayer player) {
		player.connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
		player.containerMenu = player.inventoryMenu;
		silentMenu = null;
	}

	private void notifyPlayer(QuickStackConfig config, Component message) {
		if (config.showMessages && client.player != null) {
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
