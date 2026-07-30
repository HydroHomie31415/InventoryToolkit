package be.luka.quickoffhand;

public enum DisplayRule {
   SHIELD("shield", "rule.quickoffhand.shield", null),
   TORCH("torch", "rule.quickoffhand.torch", ItemCategory.LIGHT_SOURCES),
   BEST_FOOD("best_food", "rule.quickoffhand.best_food", ItemCategory.FOOD),
   LIGHT_SOURCES("light_sources", "rule.quickoffhand.light_sources", ItemCategory.LIGHT_SOURCES),
   FOOD("food", "rule.quickoffhand.food", ItemCategory.FOOD),
   BLOCKS("blocks", "rule.quickoffhand.blocks", ItemCategory.BLOCKS),
   WEAPONS("weapons", "rule.quickoffhand.weapons", ItemCategory.WEAPONS),
   UTILITY("utility", "rule.quickoffhand.utility", ItemCategory.UTILITY),
   TOOLS("tools", "rule.quickoffhand.tools", ItemCategory.TOOLS),
   OTHER("other", "rule.quickoffhand.other", ItemCategory.OTHER);

   private final String id;
   private final String translationKey;
   private final ItemCategory category;

   DisplayRule(String id, String translationKey, ItemCategory category) {
      this.id = id;
      this.translationKey = translationKey;
      this.category = category;
   }

   public String id() {
      return this.id;
   }

   public String translationKey() {
      return this.translationKey;
   }

   public ItemCategory category() {
      return this.category;
   }

   public static DisplayRule fromId(String value) {
      if (value == null) {
         return null;
      }

      for (DisplayRule rule : values()) {
         if (rule.id.equals(value)) {
            return rule;
         }
      }

      return null;
   }
}
