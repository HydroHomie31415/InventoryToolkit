package dev.luka.smarthotbar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LoadoutScreen extends Screen {
    private final Screen parent;
    private final LoadoutConfig config;
    private LoadoutRule[] draft;
    private Button enabledButton;
    private Button presetButton;
    private Button saveButton;
    private boolean dirty;

    public LoadoutScreen(Screen parent, LoadoutConfig config) {
        super(Component.translatable("screen.smart_hotbar_loadouts.title"));
        this.parent = parent;
        this.config = config;
        this.draft = config.copyCurrentSlots();
    }

    @Override
    protected void init() {
        int slotSize = Math.min(44, Math.max(30, (width - 28) / 9));
        int hotbarWidth = slotSize * 9;
        int left = (width - hotbarWidth) / 2;
        int hotbarY = height / 2 - 42;

        for (int slot = 0; slot < 9; slot++) {
            int currentSlot = slot;
            Button ruleButton = Button.builder(slotLabel(slot), button -> {
                draft[currentSlot] = draft[currentSlot].next();
                button.setMessage(slotLabel(currentSlot));
                button.setTooltip(ruleTooltip(currentSlot));
                setDirty(true);
            }).bounds(left + slot * slotSize, hotbarY, slotSize, 42).build();
            ruleButton.setTooltip(ruleTooltip(slot));
            addRenderableWidget(ruleButton);
        }

        int controlsY = hotbarY + 54;
        int controlsWidth = Math.min(460, width - 20);
        int controlsLeft = (width - controlsWidth) / 2;
        int gap = 4;
        int toggleWidth = Math.max(74, controlsWidth * 22 / 100);
        int arrowWidth = 30;
        int saveWidth = Math.max(72, controlsWidth * 20 / 100);
        int presetWidth = controlsWidth - toggleWidth - arrowWidth * 2 - saveWidth - gap * 4;

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            config.toggleEnabled();
            button.setMessage(enabledLabel());
        }).bounds(controlsLeft, controlsY, toggleWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("◀"), button -> loadRelative(-1))
                .bounds(controlsLeft + toggleWidth + gap, controlsY, arrowWidth, 20).build());

        presetButton = addRenderableWidget(Button.builder(presetLabel(), button -> loadRelative(1))
                .bounds(controlsLeft + toggleWidth + gap * 2 + arrowWidth,
                        controlsY, presetWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("▶"), button -> loadRelative(1))
                .bounds(controlsLeft + toggleWidth + gap * 3 + arrowWidth + presetWidth,
                        controlsY, arrowWidth, 20).build());

        saveButton = addRenderableWidget(Button.builder(saveLabel(), button -> savePreset())
                .bounds(controlsLeft + toggleWidth + gap * 4 + arrowWidth * 2 + presetWidth,
                        controlsY, saveWidth, 20).build());

        addRenderableWidget(Button.builder(pickaxePreferenceLabel(), button -> {
            config.cyclePickaxePreference();
            button.setMessage(pickaxePreferenceLabel());
        }).bounds(width / 2 - 100, controlsY + 28, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Inventory controls layout…"),
                button -> minecraft.gui.setScreen(new InventoryControlsLayoutScreen(this, config)))
                .bounds(width / 2 - 100, controlsY + 54, 200, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.smart_hotbar_loadouts.done"),
                button -> onClose()).bounds(width / 2 - 100, controlsY + 80, 200, 20).build());
    }

    private void loadRelative(int offset) {
        config.selectRelative(offset);
        draft = config.copyCurrentSlots();
        dirty = false;
        rebuildWidgets();
    }

    private void savePreset() {
        config.saveCurrent(draft);
        setDirty(false);
    }

    private void setDirty(boolean value) {
        dirty = value;
        if (saveButton != null) {
            saveButton.setMessage(saveLabel());
        }
        if (presetButton != null) {
            presetButton.setMessage(presetLabel());
        }
    }

    private Component slotLabel(int slot) {
        return Component.literal((slot + 1) + " " + draft[slot].shortLabel());
    }

    private Tooltip ruleTooltip(int slot) {
        return Tooltip.create(Component.literal("Slot " + (slot + 1) + ": " + draft[slot].label()));
    }

    private Component enabledLabel() {
        return Component.literal(config.isEnabled() ? "Enabled" : "Disabled");
    }

    private Component presetLabel() {
        return Component.literal((dirty ? "● " : "") + config.presetName());
    }

    private Component saveLabel() {
        return Component.literal(dirty ? "Save Preset *" : "Save Preset");
    }

    private Component pickaxePreferenceLabel() {
        return Component.literal("Pickaxe preference: " + config.pickaxePreference().label());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Do not call Screen#extractBackground here. It requests a blur pass,
        // which crashes when Mod Menu or another UI mod already blurred this
        // frame ("Can only blur once per frame").
        graphics.fill(0, 0, width, height, 0xD0101010);
        graphics.centeredText(font, title, width / 2, height / 2 - 84, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Click a hotbar slot to change its rule"),
                width / 2, height / 2 - 67, 0xFFAAAAAA);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
