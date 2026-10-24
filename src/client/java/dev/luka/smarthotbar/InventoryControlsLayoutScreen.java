package dev.luka.smarthotbar;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class InventoryControlsLayoutScreen extends Screen {
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;

    private final Screen parent;
    private final LoadoutConfig config;
    private Button presetPreview;
    private Button autoPreview;
    private boolean draggingControls;
    private double dragOffsetX;
    private double dragOffsetY;

    public InventoryControlsLayoutScreen(Screen parent, LoadoutConfig config) {
        super(Component.translatable("screen.smart_hotbar_loadouts.layout.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        InventoryControlsSize size = config.inventoryControlsSize();
        int x = inventoryLeft() + config.inventoryControlsOffsetX();
        int y = inventoryTop() + config.inventoryControlsOffsetY();

        presetPreview = addRenderableWidget(Button.builder(
                InventoryPresetControls.presetLabel(config), button -> {
                }).bounds(x, y, size.presetWidth(), size.height()).build());
        autoPreview = addRenderableWidget(Button.builder(
                InventoryPresetControls.autoLabel(config), button -> {
                }).bounds(x + size.presetWidth() + size.gap(), y,
                        size.autoWidth(), size.height()).build());

        addRenderableWidget(Button.builder(sizeLabel(), button -> {
            config.cycleInventoryControlsSize();
            rebuildWidgets();
        }).bounds(width / 2 - 102, height - 48, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.smart_hotbar_loadouts.layout.reset"), button -> {
            config.resetInventoryControlsLayout();
            rebuildWidgets();
        }).bounds(width / 2 + 2, height - 48, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.smart_hotbar_loadouts.done"), button -> onClose())
                .bounds(width / 2 - 100, height - 24, 200, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && isOverControls(event.x(), event.y())) {
            draggingControls = true;
            dragOffsetX = event.x() - presetPreview.getX();
            dragOffsetY = event.y() - presetPreview.getY();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!draggingControls) {
            return super.mouseDragged(event, dragX, dragY);
        }
        InventoryControlsSize size = config.inventoryControlsSize();
        int newX = Mth.clamp((int) Math.round(event.x() - dragOffsetX),
                0, Math.max(0, width - size.totalWidth()));
        int newY = Mth.clamp((int) Math.round(event.y() - dragOffsetY),
                0, Math.max(0, height - size.height()));
        movePreview(newX, newY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingControls) {
            draggingControls = false;
            config.setInventoryControlsPosition(
                    presetPreview.getX() - inventoryLeft(),
                    presetPreview.getY() - inventoryTop());
            return true;
        }
        return super.mouseReleased(event);
    }

    private void movePreview(int x, int y) {
        InventoryControlsSize size = config.inventoryControlsSize();
        presetPreview.setX(x);
        presetPreview.setY(y);
        autoPreview.setX(x + size.presetWidth() + size.gap());
        autoPreview.setY(y);
    }

    private boolean isOverControls(double mouseX, double mouseY) {
        InventoryControlsSize size = config.inventoryControlsSize();
        return mouseX >= presetPreview.getX()
                && mouseX < presetPreview.getX() + size.totalWidth()
                && mouseY >= presetPreview.getY()
                && mouseY < presetPreview.getY() + size.height();
    }

    private int inventoryLeft() {
        return (width - INVENTORY_WIDTH) / 2;
    }

    private int inventoryTop() {
        return (height - INVENTORY_HEIGHT) / 2;
    }

    private Component sizeLabel() {
        return Component.translatable("screen.smart_hotbar_loadouts.layout.size",
                config.inventoryControlsSize().label());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xE0101010);
        int left = inventoryLeft();
        int top = inventoryTop();
        graphics.fill(left, top, left + INVENTORY_WIDTH, top + INVENTORY_HEIGHT, 0x60404040);
        graphics.outline(left, top, INVENTORY_WIDTH, INVENTORY_HEIGHT, 0xFFAAAAAA);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.smart_hotbar_loadouts.layout.hint"),
                width / 2, 24, 0xFFAAAAAA);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
