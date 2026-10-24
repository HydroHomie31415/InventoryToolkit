package dev.luka.smarthotbar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Two buttons below the inventory: the selected preset (left-click applies it,
 * right-click or scrolling switches presets) and the auto-apply toggle.
 */
public final class InventoryPresetControls {
    private InventoryPresetControls() {
    }

    public static void attach(Minecraft client, Screen screen, LoadoutConfig config) {
        if (client.player == null
                || !(screen instanceof AbstractContainerScreen<?> containerScreen)
                || containerScreen.getMenu() != client.player.inventoryMenu) {
            return;
        }

        AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) containerScreen;
        InventoryControlsSize size = config.inventoryControlsSize();

        Button presetButton = Button.builder(presetLabel(config),
                        button -> LoadoutApplicator.apply(client, config))
                .size(size.presetWidth(), size.height()).build();
        presetButton.setTooltip(presetTooltip(config));

        Button autoButton = Button.builder(autoLabel(config), button -> {
            config.toggleAutoApply();
            button.setMessage(autoLabel(config));
            button.setTooltip(autoTooltip(config));
        }).size(size.autoWidth(), size.height()).build();
        autoButton.setTooltip(autoTooltip(config));

        Screens.getWidgets(screen).add(presetButton);
        Screens.getWidgets(screen).add(autoButton);

        Runnable placeControls = () -> {
            int left = bounds.inventoryToolkit$leftPos() + config.inventoryControlsOffsetX();
            int y = bounds.inventoryToolkit$topPos() + config.inventoryControlsOffsetY();
            presetButton.setPosition(left, y);
            autoButton.setPosition(left + size.presetWidth() + size.gap(), y);
        };
        placeControls.run();
        // Opening the recipe book moves the inventory sideways without
        // re-initialising the screen, so follow it every frame.
        ScreenEvents.beforeExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
                placeControls.run());

        // InventoryScreen overrides Screen#extractRenderState without rendering
        // its normal widget list in 26.2. Keep the widgets in that list for
        // input/narration, and explicitly extract our controls afterward.
        List<AbstractWidget> controls = List.of(presetButton, autoButton);
        ScreenEvents.afterExtract(screen).register((ignored, graphics, mouseX, mouseY, delta) -> {
            for (AbstractWidget control : controls) {
                control.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        });

        ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT
                    && presetButton.isMouseOver(event.x(), event.y())) {
                switchPreset(client, config, presetButton, 1);
                return false;
            }
            return true;
        });
        ScreenMouseEvents.allowMouseScroll(screen).register((ignored, mouseX, mouseY, horizontal, vertical) -> {
            if (vertical != 0.0 && presetButton.isMouseOver(mouseX, mouseY)) {
                switchPreset(client, config, presetButton, vertical > 0.0 ? -1 : 1);
                return false;
            }
            return true;
        });

        if (config.isAutoApply()) {
            LoadoutApplicator.apply(client, config);
        }
    }

    private static void switchPreset(Minecraft client, LoadoutConfig config, Button presetButton, int offset) {
        config.selectRelative(offset);
        presetButton.setMessage(presetLabel(config));
        presetButton.setTooltip(presetTooltip(config));
        client.gui.hud.setOverlayMessage(Component.translatable(
                "message.smart_hotbar_loadouts.selected", config.presetName()), false);
    }

    static Component presetLabel(LoadoutConfig config) {
        return Component.literal(config.presetName());
    }

    private static Tooltip presetTooltip(LoadoutConfig config) {
        return Tooltip.create(Component.translatable(
                "button.smart_hotbar_loadouts.preset.tooltip", config.presetName()));
    }

    static Component autoLabel(LoadoutConfig config) {
        return Component.translatable(config.isAutoApply()
                ? "button.smart_hotbar_loadouts.auto.on"
                : "button.smart_hotbar_loadouts.auto.off");
    }

    private static Tooltip autoTooltip(LoadoutConfig config) {
        return Tooltip.create(Component.translatable(
                "button.smart_hotbar_loadouts.auto.tooltip",
                Component.translatable(config.isAutoApply()
                        ? "button.smart_hotbar_loadouts.state.on"
                        : "button.smart_hotbar_loadouts.state.off")));
    }
}
