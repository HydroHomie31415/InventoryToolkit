package dev.luka.shiftdragtransfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ShiftDragTransferConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("shift-drag-transfer.json");

	public int configVersion = 1;
	public boolean enabled = true;

	public boolean dragEnabled = true;
	public InputModifier dragModifier = InputModifier.SHIFT;
	public DragMouseButton dragMouseButton = DragMouseButton.LEFT;
	public boolean dragFromContainer = true;
	public boolean dragFromPlayerInventory = true;
	public boolean allowDragStartOnEmptySlot = true;
	public boolean allowDragSlotRevisit = false;

	public boolean scrollEnabled = true;
	public InputModifier scrollModifier = InputModifier.NONE;
	public ScrollDirection scrollDirection = ScrollDirection.STANDARD;
	public boolean scrollContainerToPlayer = true;
	public boolean scrollPlayerToContainer = true;
	public int itemsPerScroll = 1;
	public int scrollCooldownMilliseconds = 0;

	public boolean useMainInventory = true;
	public boolean useHotbar = true;
	public DestinationPriority destinationPriority = DestinationPriority.MATCHING_STACKS;
	public SlotSearchOrder slotSearchOrder = SlotSearchOrder.FIRST_TO_LAST;

	public static ShiftDragTransferConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			ShiftDragTransferConfig config = new ShiftDragTransferConfig();
			config.save();
			return config;
		}

		try {
			ShiftDragTransferConfig config =
				GSON.fromJson(Files.readString(CONFIG_PATH), ShiftDragTransferConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration was empty");
			}
			config.normalize();
			config.save();
			return config;
		} catch (IOException | JsonParseException exception) {
			ShiftDragTransferClient.LOGGER.error(
				"Could not read {}; using defaults",
				CONFIG_PATH,
				exception
			);
			return new ShiftDragTransferConfig();
		}
	}

	public void save() {
		normalize();
		Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(temporaryPath, GSON.toJson(this));
			try {
				Files.move(
					temporaryPath,
					CONFIG_PATH,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			ShiftDragTransferClient.LOGGER.error("Could not save {}", CONFIG_PATH, exception);
		}
	}

	public void normalize() {
		configVersion = 1;
		itemsPerScroll = Math.max(1, Math.min(16, itemsPerScroll));
		scrollCooldownMilliseconds = Math.max(0, Math.min(500, scrollCooldownMilliseconds));

		if (dragModifier == null) {
			dragModifier = InputModifier.SHIFT;
		}
		if (dragMouseButton == null) {
			dragMouseButton = DragMouseButton.LEFT;
		}
		if (scrollModifier == null) {
			scrollModifier = InputModifier.NONE;
		}
		if (scrollDirection == null) {
			scrollDirection = ScrollDirection.STANDARD;
		}
		if (destinationPriority == null) {
			destinationPriority = DestinationPriority.MATCHING_STACKS;
		}
		if (slotSearchOrder == null) {
			slotSearchOrder = SlotSearchOrder.FIRST_TO_LAST;
		}
		if (!useMainInventory && !useHotbar) {
			useMainInventory = true;
		}
	}

	public enum InputModifier {
		NONE,
		SHIFT,
		CONTROL,
		ALT;

		public boolean matches(MouseButtonEvent event) {
			return switch (this) {
				case NONE -> true;
				case SHIFT -> event.hasShiftDown();
				case CONTROL -> event.hasControlDown();
				case ALT -> event.hasAltDown();
			};
		}

		public boolean isPressed(Minecraft minecraft) {
			return switch (this) {
				case NONE -> true;
				case SHIFT -> isEitherKeyDown(
					minecraft,
					InputConstants.KEY_LSHIFT,
					InputConstants.KEY_RSHIFT
				);
				case CONTROL -> isEitherKeyDown(
					minecraft,
					InputConstants.KEY_LCONTROL,
					InputConstants.KEY_RCONTROL
				);
				case ALT -> isEitherKeyDown(
					minecraft,
					InputConstants.KEY_LALT,
					InputConstants.KEY_RALT
				);
			};
		}

		private static boolean isEitherKeyDown(
				Minecraft minecraft,
				int firstKey,
				int secondKey
		) {
			return InputConstants.isKeyDown(minecraft.getWindow(), firstKey)
				|| InputConstants.isKeyDown(minecraft.getWindow(), secondKey);
		}
	}

	public enum DragMouseButton {
		LEFT(0),
		RIGHT(1),
		MIDDLE(2);

		private final int button;

		DragMouseButton(int button) {
			this.button = button;
		}

		public int button() {
			return button;
		}
	}

	public enum ScrollDirection {
		STANDARD,
		INVERTED;

		public boolean movesTowardPlayer(double verticalAmount) {
			return this == STANDARD ? verticalAmount < 0.0 : verticalAmount > 0.0;
		}
	}

	public enum DestinationPriority {
		MATCHING_STACKS,
		EMPTY_SLOTS
	}

	public enum SlotSearchOrder {
		FIRST_TO_LAST,
		LAST_TO_FIRST
	}
}
