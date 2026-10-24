package dev.luka.smarthotbar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartHotbarClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("smart_hotbar_loadouts");
    public static LoadoutConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = LoadoutConfig.load();
        KeyMapping.Category category = InventoryToolkitClient.keyCategory();

        KeyMapping apply = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smart_hotbar_loadouts.apply",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_R,
                category
        ));
        KeyMapping configure = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smart_hotbar_loadouts.configure",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                category
        ));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                InventoryPresetControls.attach(client, screen, CONFIG));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (apply.consumeClick()) {
                LoadoutApplicator.apply(client, CONFIG);
            }
            while (configure.consumeClick()) {
                client.gui.setScreen(new LoadoutScreen(null, CONFIG));
            }
        });
    }
}
