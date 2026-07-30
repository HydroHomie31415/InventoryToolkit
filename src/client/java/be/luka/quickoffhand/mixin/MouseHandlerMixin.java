package be.luka.quickoffhand.mixin;

import be.luka.quickoffhand.QuickOffhandClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
   @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
   private void quickoffhand$interceptScroll(long window, double horizontalAmount, double verticalAmount, CallbackInfo callbackInfo) {
      if (QuickOffhandClient.handleMouseScroll(horizontalAmount, verticalAmount)) {
         callbackInfo.cancel();
      }
   }
}
