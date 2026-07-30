package be.luka.quickoffhand;

public enum ItemCategory {
   LIGHT_SOURCES("light_sources", "category.quickoffhand.light_sources", true),
   FOOD("food", "category.quickoffhand.food", true),
   BLOCKS("blocks", "category.quickoffhand.blocks", true),
   WEAPONS("weapons", "category.quickoffhand.weapons", true),
   UTILITY("utility", "category.quickoffhand.utility", true),
   TOOLS("tools", "category.quickoffhand.tools", false),
   OTHER("other", "category.quickoffhand.other", true);

   private final String id;
   private final String translationKey;
   private final boolean enabledByDefault;

   ItemCategory(String id, String translationKey, boolean enabledByDefault) {
      this.id = id;
      this.translationKey = translationKey;
      this.enabledByDefault = enabledByDefault;
   }

   public String id() {
      return this.id;
   }

   public String translationKey() {
      return this.translationKey;
   }

   public boolean enabledByDefault() {
      return this.enabledByDefault;
   }

   public static ItemCategory fromId(String value) {
      if (value == null) {
         return null;
      }

      for (ItemCategory category : values()) {
         if (category.id.equals(value)) {
            return category;
         }
      }

      return null;
   }
}
