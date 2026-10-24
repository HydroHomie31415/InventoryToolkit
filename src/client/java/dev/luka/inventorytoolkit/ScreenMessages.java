package dev.luka.inventorytoolkit;

import dev.luka.inventorytoolkit.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Shows short messages to the player. The action bar is hidden behind
 * container screens, so a message shown while one is open is drawn above the
 * container instead.
 */
public final class ScreenMessages {
	private static final long SCREEN_MESSAGE_MILLIS = 3000;

	private static Component screenMessage;
	private static long screenMessageUntil;

	private ScreenMessages() {
	}

	public static void initialize() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				ScreenEvents.afterExtract(containerScreen).register((ignored, graphics, mouseX, mouseY, delta) -> {
					if (screenMessage != null && Util.getMillis() < screenMessageUntil) {
						int top = ((AbstractContainerScreenAccessor) containerScreen).inventoryToolkit$topPos();
						graphics.centeredText(client.font, screenMessage, width / 2, Math.max(2, top - 12), 0xFFFFFFFF);
					}
				});
			}
		});
	}

	public static void show(Minecraft client, Component message) {
		if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
			screenMessage = message;
			screenMessageUntil = Util.getMillis() + SCREEN_MESSAGE_MILLIS;
		} else if (client.player != null) {
			client.gui.hud.setOverlayMessage(message, false);
		}
	}
}
