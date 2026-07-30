package dev.luka.smarthotbar;

public enum LoadoutRule {
    NONE("Empty", "—"),
    BEST_PICKAXE("Best pickaxe", "⛏"),
    BEST_AXE("Best axe", "Axe"),
    BEST_WEAPON("Highest-damage weapon", "⚔"),
    WATER_BUCKET("Water bucket", "H₂O"),
    BUILDING_BLOCKS("Building blocks", "Block"),
    BEST_FOOD("Best food", "Food");

    private final String label;
    private final String shortLabel;

    LoadoutRule(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public LoadoutRule next() {
        LoadoutRule[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
