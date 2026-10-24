package dev.luka.smarthotbar;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum InventoryControlsSize {
    TINY(56, 32, 10, 2),
    SMALL(72, 40, 12, 3),
    NORMAL(96, 52, 16, 4);

    private final int presetWidth;
    private final int autoWidth;
    private final int height;
    private final int gap;

    InventoryControlsSize(int presetWidth, int autoWidth, int height, int gap) {
        this.presetWidth = presetWidth;
        this.autoWidth = autoWidth;
        this.height = height;
        this.gap = gap;
    }

    public Component label() {
        return Component.translatable("size.smart_hotbar_loadouts." + name().toLowerCase(Locale.ROOT));
    }

    public int presetWidth() {
        return presetWidth;
    }

    public int autoWidth() {
        return autoWidth;
    }

    public int height() {
        return height;
    }

    public int gap() {
        return gap;
    }

    public int totalWidth() {
        return presetWidth + gap + autoWidth;
    }

    public InventoryControlsSize next() {
        InventoryControlsSize[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
