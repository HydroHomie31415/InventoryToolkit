package dev.luka.smarthotbar;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class InventoryPresetControls {
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;

    private InventoryPresetControls() {
    }

    public static void attach(Minecraft client, Screen screen, LoadoutConfig config) {
        if (client.player == null
                || !(screen instanceof AbstractContainerScreen<?> containerScreen)
                || containerScreen.getMenu() != client.player.inventoryMenu) {
            return;
        }

        int inventoryLeft = (screen.width - INVENTORY_WIDTH) / 2;
        int inventoryTop = (screen.height - INVENTORY_HEIGHT) / 2;
        int left = inventoryLeft + config.inventoryControlsOffsetX();
        int y = inventoryTop + config.inventoryControlsOffsetY();
        InventoryControlsSize size = config.inventoryControlsSize();

        Button[] presetButton = new Button[1];
        Button[] autoButton = new Button[1];

        presetButton[0] = Button.builder(presetLabel(config), button -> {
            config.selectRelative(1);
            presetButton[0].setMessage(presetLabel(config));
            presetButton[0].setTooltip(
                    Tooltip.create(Component.literal("Next preset: " + config.presetName())));
            LoadoutApplicator.apply(client, config);
        }).bounds(left, y, size.presetWidth(), size.height()).build();
        presetButton[0].setTooltip(
                Tooltip.create(Component.literal("Switch to the next preset and apply it")));

        autoButton[0] = Button.builder(autoLabel(config), button -> {
            config.toggleAutoApply();
            autoButton[0].setMessage(autoLabel(config));
        }).bounds(left + size.presetWidth() + size.gap(), y,
                size.autoWidth(), size.height()).build();
        autoButton[0].setTooltip(Tooltip.create(
                Component.literal("Automatically apply the selected preset when inventory opens")));

        Screens.getWidgets(screen).add(presetButton[0]);
        Screens.getWidgets(screen).add(autoButton[0]);

        // InventoryScreen overrides Screen#extractRenderState without rendering
        // its normal widget list in 26.2. Keep the widgets in that list for
        // input/narration, and explicitly extract our four controls afterward.
        List<AbstractWidget> controls = List.of(presetButton[0], autoButton[0]);
        ScreenEvents.afterExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) -> {
            for (AbstractWidget control : controls) {
                control.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        });

        if (config.isAutoApply()) {
            LoadoutApplicator.apply(client, config);
        }
    }

    private static Component presetLabel(LoadoutConfig config) {
        return Component.literal("P" + (config.selectedPreset() + 1));
    }

    private static Component autoLabel(LoadoutConfig config) {
        return Component.literal(config.isAutoApply() ? "A:On" : "A:Off");
    }
}
