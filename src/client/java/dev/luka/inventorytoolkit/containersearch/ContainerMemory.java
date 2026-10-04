package dev.luka.inventorytoolkit.containersearch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import dev.luka.inventorytoolkit.quickstack.NearbyQuickStack;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Remembers what was inside every chest, barrel, and shulker box the player
 * has opened, per world or server. The client is never told where an opened
 * menu belongs, so each menu is tied to the container block that was
 * right-clicked just before the server opened it.
 */
public final class ContainerMemory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIRECTORY = FabricLoader.getInstance()
		.getGameDir()
		.resolve("inventory-toolkit")
		.resolve("container-memory");
	private static final int CLICK_MATCH_TICKS = 40;
	private static final int SAVE_INTERVAL_TICKS = 100;

	private static final Map<String, StoredContainer> containers = new LinkedHashMap<>();
	private static Path file;
	private static boolean dirty;
	private static long tickCounter;
	private static long lastSaveTick;

	private static BlockPos clickedPos;
	private static String clickedDimension;
	private static long clickedTick;
	private static int trackedContainerId = -1;
	private static BlockPos trackedPos;
	private static String trackedDimension;

	public record StoredStack(String item, String name, int count, boolean nested) {
	}

	public record StoredContainer(
		String dimension,
		int[] pos,
		int[] partner,
		long updatedAt,
		List<StoredStack> items
	) {
		public BlockPos blockPos() {
			return new BlockPos(pos[0], pos[1], pos[2]);
		}

		public BlockPos partnerPos() {
			return partner == null ? null : new BlockPos(partner[0], partner[1], partner[2]);
		}
	}

	private record MemoryFile(int version, List<StoredContainer> containers) {
	}

	private ContainerMemory() {
	}

	public static void initialize() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()
				&& NearbyQuickStack.isSupportedContainer(level.getBlockEntity(hit.getBlockPos()))) {
				clickedPos = hit.getBlockPos().immutable();
				clickedDimension = dimensionId(level);
				clickedTick = tickCounter;
			}
			return InteractionResult.PASS;
		});
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> load(client));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> unload());
		ClientTickEvents.END_CLIENT_TICK.register(ContainerMemory::tick);
	}

	/**
	 * Called for every menu the server opens, before any screen is created.
	 */
	public static void onMenuOpened(MenuType<?> type, int containerId) {
		boolean matchesClick = clickedPos != null
			&& tickCounter - clickedTick <= CLICK_MATCH_TICKS
			&& NearbyQuickStack.isSupportedMenuType(type);
		trackedContainerId = matchesClick ? containerId : -1;
		trackedPos = matchesClick ? clickedPos : null;
		trackedDimension = matchesClick ? clickedDimension : null;
		clickedPos = null;
	}

	/**
	 * Records the contents of the menu if it belongs to a known container block.
	 */
	public static void capture(AbstractContainerMenu menu) {
		Minecraft client = Minecraft.getInstance();
		if (!QuickStackClient.config().memoryEnabled
			|| file == null
			|| client.level == null
			|| trackedPos == null
			|| menu.containerId != trackedContainerId
			|| menu.getStateId() == 0) {
			return;
		}

		List<StoredStack> items = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && slot.hasItem()) {
				addStack(items, slot.getItem(), false);
			}
		}

		BlockPos partner = NearbyQuickStack.chestPartner(client.level.getBlockState(trackedPos), trackedPos);
		String key = key(trackedDimension, trackedPos, partner);
		StoredContainer previous = containers.get(key);
		if (previous != null && previous.items().equals(items)) {
			return;
		}

		containers.remove(key(trackedDimension, trackedPos, null));
		if (partner != null) {
			containers.remove(key(trackedDimension, partner, null));
		}
		containers.put(key, new StoredContainer(
			trackedDimension,
			coordinates(trackedPos),
			partner == null ? null : coordinates(partner),
			System.currentTimeMillis(),
			items
		));
		dirty = true;
	}

	/**
	 * Returns the remembered containers in the player's current dimension,
	 * dropping any whose block has since been broken or replaced.
	 */
	public static List<StoredContainer> containersInCurrentDimension() {
		Minecraft client = Minecraft.getInstance();
		List<StoredContainer> result = new ArrayList<>();
		if (client.level == null) {
			return result;
		}

		String dimension = dimensionId(client.level);
		Iterator<StoredContainer> iterator = containers.values().iterator();
		while (iterator.hasNext()) {
			StoredContainer container = iterator.next();
			if (!container.dimension().equals(dimension)) {
				continue;
			}
			BlockPos pos = container.blockPos();
			if (client.level.isLoaded(pos)
				&& !NearbyQuickStack.isSupportedContainer(client.level.getBlockEntity(pos))) {
				iterator.remove();
				dirty = true;
				continue;
			}
			result.add(container);
		}
		return result;
	}

	public static int size() {
		return containers.size();
	}

	public static void clear() {
		containers.clear();
		dirty = true;
		save();
	}

	private static void tick(Minecraft client) {
		tickCounter++;
		LocalPlayer player = client.player;
		if (player == null || file == null) {
			return;
		}
		if (player.containerMenu != player.inventoryMenu) {
			capture(player.containerMenu);
		} else if (dirty && tickCounter - lastSaveTick >= SAVE_INTERVAL_TICKS) {
			save();
		}
	}

	private static void addStack(List<StoredStack> items, ItemStack stack, boolean nested) {
		items.add(new StoredStack(
			BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
			stack.getHoverName().getString(),
			stack.getCount(),
			nested
		));
		if (nested) {
			return;
		}

		// Look one level into shulker boxes and bundles stored in the container.
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container != null) {
			for (ItemStackTemplate inner : container.nonEmptyItems()) {
				addStack(items, inner.create(), true);
			}
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStackTemplate inner : bundle.items()) {
				addStack(items, inner.create(), true);
			}
		}
	}

	private static void load(Minecraft client) {
		unload();
		file = DIRECTORY.resolve(worldId(client) + ".json");
		if (!Files.exists(file)) {
			return;
		}

		try {
			MemoryFile memory = GSON.fromJson(Files.readString(file), MemoryFile.class);
			if (memory == null || memory.containers() == null) {
				throw new JsonParseException("Container memory was empty");
			}
			for (StoredContainer container : memory.containers()) {
				if (container.dimension() != null
					&& container.pos() != null
					&& container.pos().length == 3
					&& container.items() != null) {
					containers.put(
						key(container.dimension(), container.blockPos(), container.partnerPos()),
						container
					);
				}
			}
		} catch (IOException | JsonParseException exception) {
			InventoryToolkitClient.LOGGER.error("Could not read container memory {}", file, exception);
		}
	}

	private static void unload() {
		if (dirty) {
			save();
		}
		containers.clear();
		file = null;
		dirty = false;
		clickedPos = null;
		trackedContainerId = -1;
		trackedPos = null;
	}

	private static void save() {
		if (file == null) {
			return;
		}
		lastSaveTick = tickCounter;
		dirty = false;
		Path temporaryPath = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(temporaryPath, GSON.toJson(new MemoryFile(1, List.copyOf(containers.values()))));
			try {
				Files.move(
					temporaryPath,
					file,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			InventoryToolkitClient.LOGGER.error("Could not save container memory {}", file, exception);
		}
	}

	private static String worldId(Minecraft client) {
		IntegratedServer singleplayer = client.getSingleplayerServer();
		ServerData server = client.getCurrentServer();
		String id;
		if (singleplayer != null) {
			Path worldPath = singleplayer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			id = "singleplayer_" + worldPath.getFileName();
		} else if (server != null) {
			id = "server_" + server.ip;
		} else {
			id = "unknown";
		}
		return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}

	/**
	 * Both halves of a double chest share one key: the lower of the two positions.
	 */
	private static String key(String dimension, BlockPos pos, BlockPos partner) {
		long position = partner == null ? pos.asLong() : Math.min(pos.asLong(), partner.asLong());
		return dimension + "|" + position;
	}

	private static int[] coordinates(BlockPos pos) {
		return new int[]{pos.getX(), pos.getY(), pos.getZ()};
	}
}
