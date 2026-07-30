package dev.inventorysweeper.mixin.client;

import dev.inventorysweeper.render.ItemEntityRenderStateAccess;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemEntityRenderStateAccess {
	@Unique
	private float inventorySweeper$opacity = 1.0F;

	@Override
	public void inventorySweeper$setOpacity(float opacity) {
		inventorySweeper$opacity = opacity;
	}

	@Override
	public float inventorySweeper$getOpacity() {
		return inventorySweeper$opacity;
	}
}
