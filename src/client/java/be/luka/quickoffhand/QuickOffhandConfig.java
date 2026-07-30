package be.luka.quickoffhand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class QuickOffhandConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("quickoffhand.json");
   public int maxSlots = 9;
   public boolean fillEmptySlotsFromInventory = true;
   public Map<String, Boolean> categories = defaultCategories();
   public List<String> displayOrder = defaultDisplayOrder();

   public static QuickOffhandConfig load() {
      if (!Files.exists(PATH)) {
         QuickOffhandConfig config = new QuickOffhandConfig();
         config.sanitize();
         config.save();
         return config;
      }

      try (Reader reader = Files.newBufferedReader(PATH)) {
         QuickOffhandConfig config = GSON.fromJson(reader, QuickOffhandConfig.class);
         if (config == null) {
            config = new QuickOffhandConfig();
         }

         config.sanitize();
         return config;
      } catch (IOException | RuntimeException exception) {
         QuickOffhandClient.LOGGER.warn("Could not read {}. Using defaults.", PATH, exception);
         QuickOffhandConfig config = new QuickOffhandConfig();
         config.sanitize();
         return config;
      }
   }

   public boolean isCategoryEnabled(ItemCategory category) {
      return this.categories.getOrDefault(category.id(), category.enabledByDefault());
   }

   public void setCategoryEnabled(ItemCategory category, boolean enabled) {
      this.categories.put(category.id(), enabled);
   }

   public List<DisplayRule> orderedRules() {
      List<DisplayRule> result = new ArrayList<>();

      for (String value : this.displayOrder) {
         DisplayRule rule = DisplayRule.fromId(value);
         if (rule != null) {
            result.add(rule);
         }
      }

      return List.copyOf(result);
   }

   public void moveRule(int index, int direction) {
      this.sanitize();
      if (index > 0 && index < this.displayOrder.size() && direction != 0) {
         int target = index + Integer.signum(direction);
         if (target > 0 && target < this.displayOrder.size()) {
            String value = this.displayOrder.remove(index);
            this.displayOrder.add(target, value);
         }
      }
   }

   public void resetDefaults() {
      this.maxSlots = 9;
      this.fillEmptySlotsFromInventory = true;
      this.categories = defaultCategories();
      this.displayOrder = defaultDisplayOrder();
      this.sanitize();
   }

   public void save() {
      this.sanitize();

      try {
         Files.createDirectories(PATH.getParent());

         try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(this, writer);
         }
      } catch (IOException exception) {
         QuickOffhandClient.LOGGER.warn("Could not save config at {}.", PATH, exception);
      }
   }

   private void sanitize() {
      this.maxSlots = Math.clamp(this.maxSlots, 1, 18);
      Map<String, Boolean> cleanCategories = defaultCategories();
      if (this.categories != null) {
         for (ItemCategory category : ItemCategory.values()) {
            Boolean value = this.categories.get(category.id());
            if (value != null) {
               cleanCategories.put(category.id(), value);
            }
         }
      }

      this.categories = cleanCategories;
      Set<String> cleanOrder = new LinkedHashSet<>();
      cleanOrder.add(DisplayRule.SHIELD.id());
      if (this.displayOrder != null) {
         for (String value : this.displayOrder) {
            DisplayRule rule = DisplayRule.fromId(value);
            if (rule != null && rule != DisplayRule.SHIELD) {
               cleanOrder.add(rule.id());
            }
         }
      }

      for (DisplayRule rule : DisplayRule.values()) {
         cleanOrder.add(rule.id());
      }

      this.displayOrder = new ArrayList<>(cleanOrder);
   }

   private static Map<String, Boolean> defaultCategories() {
      Map<String, Boolean> values = new LinkedHashMap<>();

      for (ItemCategory category : ItemCategory.values()) {
         values.put(category.id(), category.enabledByDefault());
      }

      return values;
   }

   private static List<String> defaultDisplayOrder() {
      return new ArrayList<>(
         List.of(
            DisplayRule.SHIELD.id(),
            DisplayRule.TORCH.id(),
            DisplayRule.BEST_FOOD.id(),
            DisplayRule.LIGHT_SOURCES.id(),
            DisplayRule.FOOD.id(),
            DisplayRule.BLOCKS.id(),
            DisplayRule.WEAPONS.id(),
            DisplayRule.UTILITY.id(),
            DisplayRule.TOOLS.id(),
            DisplayRule.OTHER.id()
         )
      );
   }
}
