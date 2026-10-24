package dev.luka.inventorytoolkit.compact;

import dev.luka.inventorytoolkit.CornerButtons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;

public final class CompactClient {
	private static CompactConfig config;
	private static InventoryCompactor compactor;

	private CompactClient() {
	}

	public static void initialize() {
		config = CompactConfig.load();
		compactor = new InventoryCompactor(Minecraft.getInstance());

		ScreenEvents.AFTER_INIT.register(CompactClient::attach);
		// Registered after the coordinator resets each tick, so an active run
		// owns the whole tick before any other module can act.
		ClientTickEvents.START_CLIENT_TICK.register(client -> compactor.claimTick());
		ClientTickEvents.END_CLIENT_TICK.register(client -> compactor.tick(config));
	}

	public static CompactConfig config() {
		return config;
	}

	public static InventoryCompactor compactor() {
		return compactor;
	}

	private static void attach(Minecraft client, Screen screen, int width, int height) {
		if (!config.enabled || !(screen instanceof CraftingScreen craftingScreen)) {
			return;
		}
		Button button = CornerButtons.add(
			craftingScreen,
			0,
			"button.inventory_toolkit.compact",
			"button.inventory_toolkit.compact.tooltip",
			() -> {
				if (compactor.isRunning()) {
					compactor.cancel();
				} else {
					compactor.start(config);
				}
			}
		);
		// Clicks during a run would race the compactor's own clicks; the
		// button stays clickable so it can cancel the run.
		ScreenMouseEvents.allowMouseClick(craftingScreen).register((ignored, event) ->
			!compactor.isRunning() || button.isMouseOver(event.x(), event.y())
		);
	}
}
