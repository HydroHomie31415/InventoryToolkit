package dev.luka.inventorytoolkit.containersearch;

import dev.luka.inventorytoolkit.InventoryToolkitClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows highlighted containers in two ways:
 * <ul>
 *   <li>an outline through walls, using vanilla gizmos (re-added every tick,
 *   since tick gizmos only last until the next tick);</li>
 *   <li>a HUD marker at the container's on-screen position, or on the screen
 *   edge when it is out of view. Shader packs (Iris) composite over the
 *   world after gizmos are drawn and can hide the outline entirely; the HUD
 *   is drawn after that, so the markers always show.</li>
 * </ul>
 */
public final class ContainerHighlights {
	private static final int OUTLINE_COLOR = 0xFFFFC832;
	private static final int FILL_COLOR = 0x30FFC832;
	private static final int MARKER_FILL_COLOR = 0x80FFC832;
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int EDGE_MARGIN = 12;
	private static final int MARKER_SIZE = 10;

	private static final List<Highlight> highlights = new ArrayList<>();
	private static List<Marker> markers = List.of();
	private static long tickCounter;

	private record Highlight(BlockPos pos, BlockPos partner, String label, long expiresAtTick) {
		AABB box() {
			AABB box = new AABB(pos);
			return partner == null ? box : box.minmax(new AABB(partner));
		}
	}

	/**
	 * Clip-space position of a highlight, captured during level extraction.
	 */
	private record Marker(float clipX, float clipY, float clipW, String label, int distance) {
	}

	private ContainerHighlights() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(ContainerHighlights::tick);
		LevelExtractionEvents.END_EXTRACTION.register(ContainerHighlights::projectMarkers);
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(InventoryToolkitClient.MOD_ID, "container_highlights"),
			ContainerHighlights::drawMarkers
		);
	}

	public static void show(ContainerMemory.StoredContainer container, String label, int seconds) {
		show(container.blockPos(), container.partnerPos(), label, seconds);
	}

	/**
	 * @param partner the other half of a double chest, or null
	 */
	public static void show(BlockPos pos, BlockPos partner, String label, int seconds) {
		highlights.add(new Highlight(pos, partner, label, tickCounter + seconds * 20L));
	}

	/**
	 * Outlines every remembered container in this dimension holding the item,
	 * counting copies inside stored shulker boxes and bundles too.
	 *
	 * @return the number of containers highlighted
	 */
	public static int showItem(Item item, int seconds) {
		return showItems(List.of(item), seconds);
	}

	/**
	 * Outlines every remembered container in this dimension holding any of the
	 * items. With a single item the label is its count, otherwise the number
	 * of matching items.
	 *
	 * @return the number of containers highlighted
	 */
	public static int showItems(Collection<Item> items, int seconds) {
		Set<String> itemIds = new HashSet<>();
		for (Item item : items) {
			itemIds.add(BuiltInRegistries.ITEM.getKey(item).toString());
		}
		clear();
		int containers = 0;
		for (ContainerMemory.StoredContainer container : ContainerMemory.containersInCurrentDimension()) {
			int count = 0;
			for (ContainerMemory.StoredStack stack : container.items()) {
				if (itemIds.contains(stack.item())) {
					count += stack.count();
				}
			}
			if (count > 0) {
				show(container, "×" + count, seconds);
				containers++;
			}
		}
		InventoryToolkitClient.LOGGER.info("Highlighting {} containers holding {}", containers, itemIds);
		return containers;
	}

	public static void clear() {
		highlights.clear();
		markers = List.of();
	}

	public static boolean isEmpty() {
		return highlights.isEmpty();
	}

	private static void tick(Minecraft client) {
		tickCounter++;
		if (client.level == null) {
			clear();
			return;
		}
		highlights.removeIf(highlight -> highlight.expiresAtTick() <= tickCounter);

		for (Highlight highlight : highlights) {
			Gizmos.cuboid(
				highlight.box().inflate(0.01),
				GizmoStyle.strokeAndFill(OUTLINE_COLOR, 2.5F, FILL_COLOR)
			).setAlwaysOnTop();
		}
	}

	private static void projectMarkers(LevelExtractionContext context) {
		if (highlights.isEmpty()) {
			markers = List.of();
			return;
		}

		// Same transform the level renderer uses: camera-relative position,
		// then the view rotation, then the projection.
		CameraRenderState camera = context.levelState().cameraRenderState;
		Matrix4f transform = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
		List<Marker> projected = new ArrayList<>(highlights.size());
		for (Highlight highlight : highlights) {
			Vec3 center = highlight.box().getCenter();
			Vector4f clip = transform.transform(new Vector4f(
				(float) (center.x - camera.pos.x),
				(float) (center.y - camera.pos.y),
				(float) (center.z - camera.pos.z),
				1.0F
			));
			projected.add(new Marker(
				clip.x,
				clip.y,
				clip.w,
				highlight.label(),
				(int) Math.round(center.distanceTo(camera.pos))
			));
		}
		markers = projected;
	}

	private static void drawMarkers(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (markers.isEmpty()) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		for (Marker marker : markers) {
			boolean behind = marker.clipW() <= 0.0001F;
			// Dividing by |w| keeps left/right meaningful for points behind the camera.
			float w = Math.max(Math.abs(marker.clipW()), 0.0001F);
			float x = (marker.clipX() / w * 0.5F + 0.5F) * width;
			float y = (0.5F - marker.clipY() / w * 0.5F) * height;
			boolean onScreen = !behind
				&& x >= EDGE_MARGIN && x <= width - EDGE_MARGIN
				&& y >= EDGE_MARGIN && y <= height - EDGE_MARGIN;

			if (!onScreen) {
				float dx = x - width / 2.0F;
				float dy = y - height / 2.0F;
				if (behind && Math.abs(dx) < 1.0F && Math.abs(dy) < 1.0F) {
					dy = 1.0F;
				}
				float scale = Math.min(
					(width / 2.0F - EDGE_MARGIN) / Math.max(Math.abs(dx), 0.0001F),
					(height / 2.0F - EDGE_MARGIN) / Math.max(Math.abs(dy), 0.0001F)
				);
				x = width / 2.0F + dx * scale;
				y = height / 2.0F + dy * scale;
			}

			int centerX = Math.round(x);
			int centerY = Math.round(y);
			int half = MARKER_SIZE / 2;
			graphics.fill(centerX - half, centerY - half, centerX + half, centerY + half, MARKER_FILL_COLOR);
			graphics.outline(centerX - half, centerY - half, MARKER_SIZE, MARKER_SIZE, OUTLINE_COLOR);

			String text = marker.label() == null
				? marker.distance() + "m"
				: marker.label() + " · " + marker.distance() + "m";
			int textY = centerY - half - 10 < 2 ? centerY + half + 2 : centerY - half - 10;
			graphics.centeredText(client.font, text, centerX, textY, LABEL_COLOR);
		}
	}
}
