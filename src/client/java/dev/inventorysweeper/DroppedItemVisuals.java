package dev.inventorysweeper;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Matches server-spawned item entities back to Inventory Sweeper's inventory
 * throw actions. Minecraft does not synchronize an item's thrower to clients,
 * so matching is deliberately narrow: same stack, fresh entity, close to the
 * local player, and within a short window after the action.
 */
public final class DroppedItemVisuals {
	private static final long MATCH_WINDOW_NANOS = 3_000_000_000L;
	private static final double MAX_MATCH_DISTANCE_SQUARED = 16.0;
	private static final int MAX_MATCH_AGE_TICKS = 20;

	private static final ArrayDeque<ExpectedDrop> expectedDrops = new ArrayDeque<>();
	private static final Set<Integer> trackedEntityIds = new HashSet<>();
	private static ClientLevel trackedLevel;
	private static boolean initialized;

	private DroppedItemVisuals() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> trackedEntityIds.remove(entity.getId()));
	}

	public static void expectDrop(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		expectedDrops.addLast(new ExpectedDrop(
			stack.copy(),
			System.nanoTime() + MATCH_WINDOW_NANOS
		));
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			reset(null);
			return;
		}
		if (level != trackedLevel) {
			reset(level);
		}

		long now = System.nanoTime();
		while (!expectedDrops.isEmpty() && expectedDrops.peekFirst().expiresAtNanos() < now) {
			expectedDrops.removeFirst();
		}
		if (expectedDrops.isEmpty()) {
			return;
		}

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity itemEntity)
				|| trackedEntityIds.contains(entity.getId())
				|| itemEntity.getAge() > MAX_MATCH_AGE_TICKS
				|| itemEntity.distanceToSqr(player) > MAX_MATCH_DISTANCE_SQUARED) {
				continue;
			}

			Iterator<ExpectedDrop> iterator = expectedDrops.iterator();
			while (iterator.hasNext()) {
				ExpectedDrop expected = iterator.next();
				if (expected.matches(itemEntity.getItem())) {
					trackedEntityIds.add(entity.getId());
					iterator.remove();
					break;
				}
			}
		}
	}

	public static float opacityFor(ItemEntity entity, float partialTicks) {
		if (!trackedEntityIds.contains(entity.getId())) {
			return 1.0F;
		}

		SweeperConfig config = InventorySweeperClient.config();
		float initialOpacity = config.autoDroppedItemOpacity / 100.0F;
		if (config.autoDroppedItemFadeTicks == 0) {
			return initialOpacity;
		}

		float progress = Mth.clamp(
			(entity.getAge() + partialTicks) / config.autoDroppedItemFadeTicks,
			0.0F,
			1.0F
		);
		return Mth.lerp(progress, initialOpacity, 1.0F);
	}

	private static void reset(ClientLevel level) {
		trackedLevel = level;
		expectedDrops.clear();
		trackedEntityIds.clear();
	}

	private record ExpectedDrop(ItemStack stack, long expiresAtNanos) {
		private boolean matches(ItemStack other) {
			return stack.getCount() == other.getCount()
				&& ItemStack.isSameItemSameComponents(stack, other);
		}
	}
}
