package dev.luka.smarthotbar;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum PickaxePreference {
    NEUTRAL,
    SILK_TOUCH,
    FORTUNE;

    public Component label() {
        return Component.translatable("pickaxe_preference.smart_hotbar_loadouts." + name().toLowerCase(Locale.ROOT));
    }

    public PickaxePreference next() {
        PickaxePreference[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
