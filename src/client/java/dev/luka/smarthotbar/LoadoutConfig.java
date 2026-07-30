package dev.luka.smarthotbar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LoadoutConfig {
    public static final int PRESET_COUNT = 5;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("smart-hotbar-loadouts.json");
    private static final LoadoutRule[] DEFAULT_SLOTS = {
            LoadoutRule.BEST_PICKAXE,
            LoadoutRule.BEST_AXE,
            LoadoutRule.BEST_WEAPON,
            LoadoutRule.WATER_BUCKET,
            LoadoutRule.BUILDING_BLOCKS,
            LoadoutRule.BUILDING_BLOCKS,
            LoadoutRule.BUILDING_BLOCKS,
            LoadoutRule.BUILDING_BLOCKS,
            LoadoutRule.BEST_FOOD
    };

    private boolean enabled = true;
    private boolean autoApply;
    private int inventoryControlsOffsetX = -35;
    private int inventoryControlsOffsetY = 169;
    private InventoryControlsSize inventoryControlsSize = InventoryControlsSize.TINY;
    private int selectedPreset;
    private List<Preset> presets = createDefaults();

    // Retained solely so configurations from the first version migrate cleanly.
    @SuppressWarnings("unused")
    private LoadoutRule[] slots;

    public static LoadoutConfig load() {
        LoadoutConfig config = null;
        if (Files.exists(PATH)) {
            try {
                config = GSON.fromJson(Files.readString(PATH), LoadoutConfig.class);
            } catch (Exception exception) {
                SmartHotbarClient.LOGGER.warn("Could not read {}, using defaults", PATH, exception);
            }
        }
        if (config == null) {
            config = new LoadoutConfig();
        }
        config.normalize();
        config.save();
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        save();
    }

    public boolean isAutoApply() {
        return autoApply;
    }

    public void toggleAutoApply() {
        autoApply = !autoApply;
        save();
    }

    public int inventoryControlsOffsetX() {
        return inventoryControlsOffsetX;
    }

    public int inventoryControlsOffsetY() {
        return inventoryControlsOffsetY;
    }

    public InventoryControlsSize inventoryControlsSize() {
        return inventoryControlsSize;
    }

    public void setInventoryControlsPosition(int offsetX, int offsetY) {
        inventoryControlsOffsetX = offsetX;
        inventoryControlsOffsetY = offsetY;
        save();
    }

    public void cycleInventoryControlsSize() {
        inventoryControlsSize = inventoryControlsSize.next();
        save();
    }

    public void resetInventoryControlsLayout() {
        inventoryControlsOffsetX = -35;
        inventoryControlsOffsetY = 169;
        inventoryControlsSize = InventoryControlsSize.TINY;
        save();
    }

    public int selectedPreset() {
        return selectedPreset;
    }

    public String presetName() {
        return presets.get(selectedPreset).name;
    }

    public PickaxePreference pickaxePreference() {
        return presets.get(selectedPreset).pickaxePreference;
    }

    public void cyclePickaxePreference() {
        Preset preset = presets.get(selectedPreset);
        preset.pickaxePreference = preset.pickaxePreference.next();
        save();
    }

    public LoadoutRule get(int slot) {
        return presets.get(selectedPreset).slots[slot];
    }

    public LoadoutRule[] copyCurrentSlots() {
        return presets.get(selectedPreset).slots.clone();
    }

    public void selectRelative(int offset) {
        selectedPreset = Math.floorMod(selectedPreset + offset, presets.size());
        save();
    }

    public void saveCurrent(LoadoutRule[] rules) {
        if (validSlots(rules)) {
            presets.get(selectedPreset).slots = rules.clone();
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException exception) {
            SmartHotbarClient.LOGGER.error("Could not save {}", PATH, exception);
        }
    }

    private void normalize() {
        LoadoutRule[] migrated = validSlots(slots) ? slots.clone() : null;
        if (presets == null) {
            presets = new ArrayList<>();
        }
        presets.removeIf(preset -> preset == null || !validSlots(preset.slots));
        for (Preset preset : presets) {
            if (preset.pickaxePreference == null) {
                preset.pickaxePreference = PickaxePreference.NEUTRAL;
            }
        }
        while (presets.size() < PRESET_COUNT) {
            int number = presets.size() + 1;
            LoadoutRule[] initial = number == 1
                    ? (migrated != null ? migrated : DEFAULT_SLOTS.clone())
                    : emptySlots();
            presets.add(new Preset("Preset " + number, initial));
        }
        if (presets.size() > PRESET_COUNT) {
            presets = new ArrayList<>(presets.subList(0, PRESET_COUNT));
        }
        selectedPreset = Math.floorMod(selectedPreset, presets.size());
        if (inventoryControlsSize == null) {
            inventoryControlsSize = InventoryControlsSize.TINY;
        }
        slots = null;
    }

    private static boolean validSlots(LoadoutRule[] rules) {
        return rules != null && rules.length == 9
                && Arrays.stream(rules).noneMatch(rule -> rule == null);
    }

    private static LoadoutRule[] emptySlots() {
        LoadoutRule[] result = new LoadoutRule[9];
        Arrays.fill(result, LoadoutRule.NONE);
        return result;
    }

    private static List<Preset> createDefaults() {
        List<Preset> result = new ArrayList<>();
        result.add(new Preset("Preset 1", DEFAULT_SLOTS.clone()));
        for (int index = 2; index <= PRESET_COUNT; index++) {
            result.add(new Preset("Preset " + index, emptySlots()));
        }
        return result;
    }

    private static final class Preset {
        private String name;
        private LoadoutRule[] slots;
        private PickaxePreference pickaxePreference = PickaxePreference.NEUTRAL;

        private Preset(String name, LoadoutRule[] slots) {
            this.name = name;
            this.slots = slots;
        }
    }
}
