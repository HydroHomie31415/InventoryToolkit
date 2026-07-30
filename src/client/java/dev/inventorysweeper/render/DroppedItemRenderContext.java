package dev.inventorysweeper.render;

public final class DroppedItemRenderContext {
	private static final ThreadLocal<Float> OPACITY = ThreadLocal.withInitial(() -> 1.0F);

	private DroppedItemRenderContext() {
	}

	public static void begin(float opacity) {
		OPACITY.set(opacity);
	}

	public static void end() {
		OPACITY.remove();
	}

	public static float opacity() {
		return OPACITY.get();
	}
}
