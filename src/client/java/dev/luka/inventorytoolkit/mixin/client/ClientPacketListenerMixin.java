package dev.luka.inventorytoolkit.mixin.client;

import dev.luka.inventorytoolkit.containersearch.ContainerMemory;
import dev.luka.inventorytoolkit.quickstack.QuickStackClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	// Injected at the MenuScreens.create call rather than HEAD so it runs on the
	// client thread, after ensureRunningOnSameThread has rescheduled the packet.
	@Inject(
		method = "handleOpenScreen",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"
		),
		cancellable = true
	)
	private void inventoryToolkit$openQuickStackContainerSilently(
		ClientboundOpenScreenPacket packet,
		CallbackInfo callbackInfo
	) {
		ContainerMemory.onMenuOpened(packet.getType(), packet.getContainerId());
		if (QuickStackClient.nearby().openSilently(packet.getType(), packet.getContainerId(), packet.getTitle())) {
			callbackInfo.cancel();
		}
	}
}
