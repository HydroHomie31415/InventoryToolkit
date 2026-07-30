package dev.luka.smarthotbar;

public enum InventoryControlsSize {
    TINY("Tiny", 22, 34, 10, 2),
    SMALL("Small", 30, 50, 12, 3),
    NORMAL("Normal", 40, 64, 16, 4);

    private final String label;
    private final int presetWidth;
    private final int autoWidth;
    private final int height;
    private final int gap;

    InventoryControlsSize(String label, int presetWidth, int autoWidth, int height, int gap) {
        this.label = label;
        this.presetWidth = presetWidth;
        this.autoWidth = autoWidth;
        this.height = height;
        this.gap = gap;
    }

    public String label() {
        return label;
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
