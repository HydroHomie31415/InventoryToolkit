package be.luka.quickoffhand;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.luka.inventorytoolkit.InventoryActionCoordinator;
import dev.luka.inventorytoolkit.InventoryToolkitClient;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuickOffhandClient implements ClientModInitializer {
   public static final String MOD_ID = "quickoffhand";
   public static final Logger LOGGER = LoggerFactory.getLogger("quickoffhand");
   private static final int SLOT_SIZE = 24;
   private static final int ITEM_OFFSET = 4;
   private static final int GAP = 2;
   private static KeyMapping openSelectorKey;
   private static KeyMapping openSettingsKey;
   private static QuickOffhandConfig config;
   private static boolean selectorOpen;
   private static int selectedIndex;
   private static int refreshTicks;
   private static List<InventoryCandidate> candidates = List.of();

   public void onInitializeClient() {
      ensureConfigLoaded();
      KeyMapping.Category category = InventoryToolkitClient.keyCategory();
      openSelectorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.quickoffhand.open_selector", Type.MOUSE, InputConstants.MOUSE_BUTTON_4, category));
      openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.quickoffhand.open_settings", Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
      ClientTickEvents.END_CLIENT_TICK.register(QuickOffhandClient::tick);
      HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("quickoffhand", "selector"), QuickOffhandClient::render);
   }

   private static void tick(Minecraft client) {
      if (openSettingsKey != null && openSettingsKey.consumeClick() && client.gui.screen() == null) {
         cancelSelector();
         client.setScreenAndShow(createConfigScreen(null));
      } else {
         boolean keyDown = openSelectorKey != null && openSelectorKey.isDown();
         boolean canUse = canUseSelector(client);
         if (keyDown && canUse) {
            if (!selectorOpen) {
               open(client.player);
            } else if (++refreshTicks >= 5) {
               refreshTicks = 0;
               refresh(client.player);
            }
         } else if (selectorOpen) {
            boolean releasedNormally = !keyDown && canUse;
            selectorOpen = false;
            refreshTicks = 0;
            if (releasedNormally) {
               equipSelected(client);
            }

            candidates = List.of();
            selectedIndex = 0;
         }
      }
   }

   public static Screen createConfigScreen(Screen parent) {
      ensureConfigLoaded();
      cancelSelector();
      return new QuickOffhandConfigScreen(parent, config);
   }

   public static QuickOffhandConfig config() {
      ensureConfigLoaded();
      return config;
   }

   private static void ensureConfigLoaded() {
      if (config == null) {
         config = QuickOffhandConfig.load();
      }
   }

   private static void cancelSelector() {
      selectorOpen = false;
      refreshTicks = 0;
      candidates = List.of();
      selectedIndex = 0;
   }

   public static void onConfigChanged() {
      if (selectorOpen) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            refresh(client.player);
         }
      }
   }

   private static void open(LocalPlayer player) {
      selectorOpen = true;
      refreshTicks = 0;
      candidates = CandidateFinder.find(player, config);
      selectedIndex = findCurrentOffhandIndex(player, candidates);
      if (selectedIndex < 0) {
         selectedIndex = 0;
      }
   }

   private static void refresh(LocalPlayer player) {
      String selectedItemId = selectedCandidate() == null ? null : selectedCandidate().itemId();
      candidates = CandidateFinder.find(player, config);
      if (candidates.isEmpty()) {
         selectedIndex = 0;
      } else {
         if (selectedItemId != null) {
            for (int index = 0; index < candidates.size(); index++) {
               if (candidates.get(index).itemId().equals(selectedItemId)) {
                  selectedIndex = index;
                  return;
               }
            }
         }

         selectedIndex = Math.min(selectedIndex, candidates.size() - 1);
      }
   }

   private static int findCurrentOffhandIndex(LocalPlayer player, List<InventoryCandidate> values) {
      ItemStack offhand = player.getInventory().getItem(40);
      if (offhand.isEmpty()) {
         return -1;
      }

      String offhandId = BuiltInRegistries.ITEM.getKey(offhand.getItem()).toString();

      for (int index = 0; index < values.size(); index++) {
         if (values.get(index).itemId().equals(offhandId)) {
            return index;
         }
      }

      return -1;
   }

   public static boolean handleMouseScroll(double horizontalAmount, double verticalAmount) {
      if (selectorOpen && !candidates.isEmpty()) {
         double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
         if (amount == 0.0) {
            return true;
         }

         int direction = amount > 0.0 ? -1 : 1;
         selectedIndex = Math.floorMod(selectedIndex + direction, candidates.size());
         return true;
      } else {
         return false;
      }
   }

   private static void equipSelected(Minecraft client) {
      InventoryCandidate candidate = selectedCandidate();
      LocalPlayer player = client.player;
      MultiPlayerGameMode gameMode = client.gameMode;
      if (candidate != null && player != null && gameMode != null && canUseSelector(client)) {
         if (player.containerMenu.getCarried().isEmpty()) {
            Inventory inventory = player.getInventory();
            int inventorySlot = candidate.inventorySlot();
            if (inventorySlot >= 0 && inventorySlot < 36) {
               ItemStack currentStack = inventory.getItem(inventorySlot);
               if (!currentStack.isEmpty()) {
                  String currentItemId = BuiltInRegistries.ITEM.getKey(currentStack.getItem()).toString();
                  if (currentItemId.equals(candidate.itemId())) {
                     int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
                     if (InventoryActionCoordinator.acquire(InventoryActionCoordinator.OFFHAND_SWAPPER)) {
                        gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, player);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean canUseSelector(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null
         && config.enabled
         && client.gameMode != null
         && client.gui.screen() == null
         && client.mouseHandler.isMouseGrabbed()
         && player.containerMenu == player.inventoryMenu
         && !player.isSpectator();
   }

   private static InventoryCandidate selectedCandidate() {
      return !candidates.isEmpty() && selectedIndex >= 0 && selectedIndex < candidates.size() ? candidates.get(selectedIndex) : null;
   }

   private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      if (selectorOpen) {
         Minecraft client = Minecraft.getInstance();
         int screenWidth = graphics.guiWidth();
         int screenHeight = graphics.guiHeight();
         if (candidates.isEmpty()) {
            Component empty = Component.translatable("text.quickoffhand.empty");
            int width = client.font.width(empty) + 12;
            int x = (screenWidth - width) / 2;
            int y = screenHeight - 72;
            graphics.fill(x, y, x + width, y + 20, -804253680);
            graphics.outline(x, y, width, 20, -6645094);
            graphics.text(client.font, empty, x + 6, y + 6, -1, true);
         } else {
            int barWidth = candidates.size() * 24 + (candidates.size() - 1) * 2;
            int startX = (screenWidth - barWidth) / 2;
            int startY = screenHeight - 74;
            InventoryCandidate selected = selectedCandidate();
            Component selectedName = selected == null ? Component.empty() : selected.displayStack().getHoverName();
            int nameWidth = client.font.width(selectedName);
            graphics.text(client.font, selectedName, (screenWidth - nameWidth) / 2, startY - 14, -1, true);

            for (int index = 0; index < candidates.size(); index++) {
               int x = startX + index * 26;
               boolean active = index == selectedIndex;
               int background = active ? -532002230 : -1072162792;
               int border = active ? -1 : -8947849;
               graphics.fill(x, startY, x + 24, startY + 24, background);
               graphics.outline(x, startY, 24, 24, border);
               ItemStack stack = candidates.get(index).displayStack();
               graphics.item(stack, x + 4, startY + 4);
               graphics.itemDecorations(client.font, stack, x + 4, startY + 4);
            }

            Component hint = Component.translatable("text.quickoffhand.hint");
            int hintWidth = client.font.width(hint);
            graphics.text(client.font, hint, (screenWidth - hintWidth) / 2, startY + 24 + 5, -3092272, true);
         }
      }
   }
}
