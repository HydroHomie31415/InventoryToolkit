package dev.luka.shiftdragtransfer;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShiftDragTransferClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Shift Drag Transfer");

	private static ShiftDragTransferConfig config;

	@Override
	public void onInitializeClient() {
		config = ShiftDragTransferConfig.load();
	}

	public static ShiftDragTransferConfig config() {
		if (config == null) {
			config = ShiftDragTransferConfig.load();
		}
		return config;
	}
}
