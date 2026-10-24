package be.luka.quickoffhand;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class QuickOffhandConfigScreen extends Screen {
   private static final int CONTENT_WIDTH = 310;
   private static final int ROW_HEIGHT = 22;
   private static final int ORDER_ROWS_PER_PAGE = 5;
   private final Screen parent;
   private final QuickOffhandConfig config;
   private final QuickOffhandConfigScreen.Page page;
   private final int orderPage;

   public QuickOffhandConfigScreen(Screen parent, QuickOffhandConfig config) {
      this(parent, config, QuickOffhandConfigScreen.Page.GENERAL, 0);
   }

   private QuickOffhandConfigScreen(Screen parent, QuickOffhandConfig config, QuickOffhandConfigScreen.Page page, int orderPage) {
      super(Component.translatable("screen.quickoffhand.title"));
      this.parent = parent;
      this.config = config;
      this.page = page;
      this.orderPage = Math.max(0, orderPage);
   }

   protected void init() {
      int left = (this.width - 310) / 2;
      int tabWidth = 102;
      int tabsY = 28;
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.quickoffhand.tab.general"), button -> this.reopen(QuickOffhandConfigScreen.Page.GENERAL, 0))
            .bounds(left, tabsY, tabWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.quickoffhand.tab.categories"), button -> this.reopen(QuickOffhandConfigScreen.Page.CATEGORIES, 0))
            .bounds(left + tabWidth + 2, tabsY, tabWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.quickoffhand.tab.order"), button -> this.reopen(QuickOffhandConfigScreen.Page.ORDER, this.orderPage))
            .bounds(left + (tabWidth + 2) * 2, tabsY, tabWidth, 20)
            .build()
      );
      switch (this.page) {
         case GENERAL:
            this.initGeneral(left);
            break;
         case CATEGORIES:
            this.initCategories(left);
            break;
         case ORDER:
            this.initOrder(left);
      }

      int bottomY = this.height - 24;
      this.addRenderableWidget(Button.builder(Component.translatable("screen.quickoffhand.reset"), button -> {
         this.config.resetDefaults();
         this.reopen(QuickOffhandConfigScreen.Page.GENERAL, 0);
      }).bounds(left, bottomY, 100, 20).build());
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(left + 310 - 100, bottomY, 100, 20).build());
   }

   private void initGeneral(int left) {
      int y = 66;
      this.addRenderableWidget(
         Button.builder(this.toggleLabel(Component.translatable("screen.quickoffhand.enabled"), this.config.enabled), button -> {
            this.config.enabled = !this.config.enabled;
            this.reopen(QuickOffhandConfigScreen.Page.GENERAL, 0);
         }).bounds(left, y, 310, 20).build()
      );
      y += 30;
      this.addRenderableWidget(Button.builder(Component.literal("−"), button -> {
         this.config.maxSlots = Math.max(1, this.config.maxSlots - 1);
         this.reopen(QuickOffhandConfigScreen.Page.GENERAL, 0);
      }).bounds(left, y, 32, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.quickoffhand.max_slots", new Object[]{this.config.maxSlots}), button -> {})
            .bounds(left + 36, y, 238, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
         this.config.maxSlots = Math.min(18, this.config.maxSlots + 1);
         this.reopen(QuickOffhandConfigScreen.Page.GENERAL, 0);
      }).bounds(left + 310 - 32, y, 32, 20).build());
      y += 30;
      this.addRenderableWidget(
         Button.builder(this.toggleLabel(Component.translatable("screen.quickoffhand.fill_fallback"), this.config.fillEmptySlotsFromInventory), button -> {
            this.config.fillEmptySlotsFromInventory = !this.config.fillEmptySlotsFromInventory;
            this.reopen(QuickOffhandConfigScreen.Page.GENERAL, 0);
         }).bounds(left, y, 310, 20).build()
      );
   }

   private void initCategories(int left) {
      int y = 58;

      for (ItemCategory category : ItemCategory.values()) {
         boolean enabled = this.config.isCategoryEnabled(category);
         this.addRenderableWidget(Button.builder(this.toggleLabel(Component.translatable(category.translationKey()), enabled), button -> {
            this.config.setCategoryEnabled(category, !this.config.isCategoryEnabled(category));
            this.reopen(QuickOffhandConfigScreen.Page.CATEGORIES, 0);
         }).bounds(left, y, 310, 20).build());
         y += 22;
      }
   }

   private void initOrder(int left) {
      List<DisplayRule> rules = this.config.orderedRules();
      int pageCount = Math.max(1, (rules.size() + 5 - 1) / 5);
      int safePage = Math.min(this.orderPage, pageCount - 1);
      int start = safePage * 5;
      int end = Math.min(rules.size(), start + 5);
      int y = 58;

      for (int index = start; index < end; index++) {
         DisplayRule rule = rules.get(index);
         int rowIndex = index;
         boolean fixed = rule == DisplayRule.SHIELD;
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("screen.quickoffhand.order_entry", new Object[]{index + 1, Component.translatable(rule.translationKey())}),
                  button -> {}
               )
               .bounds(left, y, 238, 20)
               .build()
         );
         this.addRenderableWidget(Button.builder(Component.literal("↑"), button -> {
            if (!fixed) {
               this.config.moveRule(rowIndex, -1);
               this.reopen(QuickOffhandConfigScreen.Page.ORDER, safePage);
            }
         }).bounds(left + 310 - 68, y, 32, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("↓"), button -> {
            if (!fixed) {
               this.config.moveRule(rowIndex, 1);
               this.reopen(QuickOffhandConfigScreen.Page.ORDER, safePage);
            }
         }).bounds(left + 310 - 32, y, 32, 20).build());
         y += 22;
      }

      if (pageCount > 1) {
         int pagerY = 172;
         this.addRenderableWidget(
            Button.builder(Component.literal("←"), button -> this.reopen(QuickOffhandConfigScreen.Page.ORDER, Math.max(0, safePage - 1)))
               .bounds(left, pagerY, 40, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("screen.quickoffhand.page", new Object[]{safePage + 1, pageCount}), button -> {})
               .bounds(left + 44, pagerY, 222, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.literal("→"), button -> this.reopen(QuickOffhandConfigScreen.Page.ORDER, Math.min(pageCount - 1, safePage + 1)))
               .bounds(left + 310 - 40, pagerY, 40, 20)
               .build()
         );
      }
   }

   private Component toggleLabel(Component name, boolean enabled) {
      return Component.translatable(
         "screen.quickoffhand.toggle", new Object[]{name, Component.translatable(enabled ? "screen.quickoffhand.on" : "screen.quickoffhand.off")}
      );
   }

   private void reopen(QuickOffhandConfigScreen.Page newPage, int newOrderPage) {
      this.minecraft.setScreenAndShow(new QuickOffhandConfigScreen(this.parent, this.config, newPage, newOrderPage));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.centeredText(this.font, this.title, this.width / 2, 10, -1);

      Component description = switch (this.page) {
         case GENERAL -> Component.translatable("screen.quickoffhand.description.general");
         case CATEGORIES -> Component.translatable("screen.quickoffhand.description.categories");
         case ORDER -> Component.translatable("screen.quickoffhand.description.order");
      };
      graphics.centeredText(this.font, description, this.width / 2, 50, -4671304);
   }

   public void onClose() {
      this.config.save();
      QuickOffhandClient.onConfigChanged();
      this.minecraft.setScreenAndShow(this.parent);
   }

   private enum Page {
      GENERAL,
      CATEGORIES,
      ORDER;
   }
}
