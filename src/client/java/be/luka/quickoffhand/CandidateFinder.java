package be.luka.quickoffhand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CandidateFinder {
   private CandidateFinder() {
   }

   public static List<InventoryCandidate> find(LocalPlayer player, QuickOffhandConfig config) {
      Inventory inventory = player.getInventory();
      Map<String, InventoryCandidate> remaining = new LinkedHashMap<>();

      for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
         ItemStack stack = inventory.getItem(inventorySlot);
         if (!stack.isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            remaining.putIfAbsent(itemId, new InventoryCandidate(inventorySlot, itemId, stack.copy()));
         }
      }

      List<InventoryCandidate> result = new ArrayList<>(config.maxSlots);

      for (DisplayRule rule : config.orderedRules()) {
         if (result.size() >= config.maxSlots) {
            break;
         }

         if (rule.category() == null || config.isCategoryEnabled(rule.category())) {
            switch (rule) {
               case SHIELD:
                  addFirstMatching(result, remaining, candidatex -> candidatex.displayStack().is(Items.SHIELD), config.maxSlots);
                  break;
               case TORCH:
                  addPreferredTorch(result, remaining, config.maxSlots);
                  break;
               case BEST_FOOD:
                  addBestFood(result, remaining, config.maxSlots);
                  break;
               case LIGHT_SOURCES:
                  addCategory(result, remaining, ItemCategory.LIGHT_SOURCES, config.maxSlots);
                  break;
               case FOOD:
                  addCategory(result, remaining, ItemCategory.FOOD, config.maxSlots);
                  break;
               case BLOCKS:
                  addCategory(result, remaining, ItemCategory.BLOCKS, config.maxSlots);
                  break;
               case WEAPONS:
                  addCategory(result, remaining, ItemCategory.WEAPONS, config.maxSlots);
                  break;
               case UTILITY:
                  addCategory(result, remaining, ItemCategory.UTILITY, config.maxSlots);
                  break;
               case TOOLS:
                  addCategory(result, remaining, ItemCategory.TOOLS, config.maxSlots);
                  break;
               case OTHER:
                  addCategory(result, remaining, ItemCategory.OTHER, config.maxSlots);
            }
         }
      }

      if (config.fillEmptySlotsFromInventory && result.size() < config.maxSlots) {
         for (InventoryCandidate candidate : List.copyOf(remaining.values())) {
            ItemCategory category = categoryOf(candidate.displayStack(), candidate.itemId());
            if (config.isCategoryEnabled(category)) {
               result.add(candidate);
               remaining.remove(candidate.itemId());
               if (result.size() >= config.maxSlots) {
                  break;
               }
            }
         }
      }

      return List.copyOf(result);
   }

   private static void addPreferredTorch(List<InventoryCandidate> result, Map<String, InventoryCandidate> remaining, int maxSlots) {
      if (result.size() < maxSlots) {
         InventoryCandidate torch = remaining.get("minecraft:torch");
         if (torch == null) {
            torch = remaining.get("minecraft:soul_torch");
         }

         if (torch != null) {
            result.add(torch);
            remaining.remove(torch.itemId());
         }
      }
   }

   private static void addBestFood(List<InventoryCandidate> result, Map<String, InventoryCandidate> remaining, int maxSlots) {
      if (result.size() < maxSlots) {
         InventoryCandidate best = remaining.values()
            .stream()
            .filter(candidate -> isFood(candidate.displayStack()))
            .filter(candidate -> !isGoldenApple(candidate.displayStack()))
            .max(
               Comparator.<InventoryCandidate>comparingDouble(candidate -> foodSaturation(candidate.displayStack()))
                  .thenComparingInt(candidate -> foodNutrition(candidate.displayStack()))
            )
            .orElse(null);
         if (best != null) {
            result.add(best);
            remaining.remove(best.itemId());
         }
      }
   }

   private static void addCategory(List<InventoryCandidate> result, Map<String, InventoryCandidate> remaining, ItemCategory category, int maxSlots) {
      List<InventoryCandidate> matches = remaining.values()
         .stream()
         .filter(candidatex -> categoryOf(candidatex.displayStack(), candidatex.itemId()) == category)
         .toList();
      if (category == ItemCategory.FOOD) {
         matches = matches.stream()
            .sorted(
               Comparator.<InventoryCandidate>comparingDouble(candidatex -> foodSaturation(candidatex.displayStack()))
                  .thenComparingInt(candidatex -> foodNutrition(candidatex.displayStack()))
                  .reversed()
            )
            .toList();
      }

      for (InventoryCandidate candidate : matches) {
         result.add(candidate);
         remaining.remove(candidate.itemId());
         if (result.size() >= maxSlots) {
            return;
         }
      }
   }

   private static void addFirstMatching(
      List<InventoryCandidate> result, Map<String, InventoryCandidate> remaining, Predicate<InventoryCandidate> predicate, int maxSlots
   ) {
      if (result.size() < maxSlots) {
         InventoryCandidate candidate = remaining.values().stream().filter(predicate).findFirst().orElse(null);
         if (candidate != null) {
            result.add(candidate);
            remaining.remove(candidate.itemId());
         }
      }
   }

   static ItemCategory categoryOf(ItemStack stack, String itemId) {
      String path = path(itemId);
      if (isLightSource(itemId)) {
         return ItemCategory.LIGHT_SOURCES;
      } else if (isFood(stack)) {
         return ItemCategory.FOOD;
      } else if (isTool(stack, path)) {
         return ItemCategory.TOOLS;
      } else if (isWeapon(stack, path)) {
         return ItemCategory.WEAPONS;
      } else if (isUtility(itemId)) {
         return ItemCategory.UTILITY;
      } else {
         return stack.getItem() instanceof BlockItem ? ItemCategory.BLOCKS : ItemCategory.OTHER;
      }
   }

   private static boolean isTool(ItemStack stack, String path) {
      return isWeaponPath(path)
         ? false
         : path.endsWith("_pickaxe")
            || path.endsWith("_axe")
            || path.endsWith("_shovel")
            || path.endsWith("_hoe")
            || path.equals("shears")
            || path.equals("fishing_rod")
            || path.equals("brush")
            || path.equals("flint_and_steel")
            || stack.has(DataComponents.TOOL);
   }

   private static boolean isWeapon(ItemStack stack, String path) {
      return isWeaponPath(path) || stack.has(DataComponents.WEAPON);
   }

   private static boolean isWeaponPath(String path) {
      return path.endsWith("_sword")
         || path.endsWith("_spear")
         || path.equals("bow")
         || path.equals("crossbow")
         || path.equals("trident")
         || path.equals("mace");
   }

   private static boolean isFood(ItemStack stack) {
      return stack.has(DataComponents.FOOD);
   }

   private static boolean isGoldenApple(ItemStack stack) {
      return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
   }

   private static double foodSaturation(ItemStack stack) {
      FoodProperties food = stack.get(DataComponents.FOOD);
      return food == null ? -1.0 : food.saturation();
   }

   private static int foodNutrition(ItemStack stack) {
      FoodProperties food = stack.get(DataComponents.FOOD);
      return food == null ? -1 : food.nutrition();
   }

   private static boolean isLightSource(String itemId) {
      String path = path(itemId);
      return path.equals("torch")
         || path.equals("soul_torch")
         || path.equals("redstone_torch")
         || path.contains("lantern")
         || path.contains("campfire")
         || path.contains("froglight")
         || path.equals("glowstone")
         || path.equals("shroomlight")
         || path.equals("sea_lantern")
         || path.equals("end_rod")
         || path.equals("jack_o_lantern");
   }

   private static boolean isUtility(String itemId) {
      String path = path(itemId);
      return path.endsWith("_bucket")
         || path.equals("bucket")
         || path.equals("ender_pearl")
         || path.equals("totem_of_undying")
         || path.equals("firework_rocket")
         || path.equals("compass")
         || path.equals("recovery_compass")
         || path.equals("clock")
         || path.equals("spyglass")
         || path.equals("shears")
         || path.equals("flint_and_steel")
         || path.equals("fishing_rod")
         || path.equals("brush")
         || path.equals("lead")
         || path.equals("name_tag");
   }

   private static String path(String itemId) {
      int separator = itemId.indexOf(58);
      return separator >= 0 ? itemId.substring(separator + 1) : itemId;
   }
}
