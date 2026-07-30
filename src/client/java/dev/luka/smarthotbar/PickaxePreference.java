package dev.luka.smarthotbar;

public enum PickaxePreference {
    NEUTRAL("Neutral"),
    SILK_TOUCH("Silk Touch"),
    FORTUNE("Fortune");

    private final String label;

    PickaxePreference(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public PickaxePreference next() {
        PickaxePreference[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
