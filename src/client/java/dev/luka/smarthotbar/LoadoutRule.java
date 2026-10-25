package dev.luka.smarthotbar;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum LoadoutRule {
    NONE,
    BEST_PICKAXE,
    BEST_AXE,
    BEST_WEAPON,
    BEST_SHOVEL,
    BEST_HOE,
    WATER_BUCKET,
    BUILDING_BLOCKS,
    BEST_FOOD;

    public Component label() {
        return Component.translatable(translationKey());
    }

    public Component shortLabel() {
        return Component.translatable(translationKey() + ".short");
    }

    private String translationKey() {
        return "rule.smart_hotbar_loadouts." + name().toLowerCase(Locale.ROOT);
    }

    public LoadoutRule next() {
        LoadoutRule[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
