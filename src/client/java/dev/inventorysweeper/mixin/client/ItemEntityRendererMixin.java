package dev.inventorysweeper.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.inventorysweeper.DroppedItemVisuals;
import dev.inventorysweeper.render.DroppedItemRenderContext;
import dev.inventorysweeper.render.ItemEntityRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void inventorySweeper$storeOpacity(
		ItemEntity entity,
		ItemEntityRenderState state,
		float partialTicks,
		CallbackInfo callback
	) {
		((ItemEntityRenderStateAccess) state).inventorySweeper$setOpacity(
			DroppedItemVisuals.opacityFor(entity, partialTicks)
		);
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void inventorySweeper$beginOpacity(
		ItemEntityRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo callback
	) {
		DroppedItemRenderContext.begin(
			((ItemEntityRenderStateAccess) state).inventorySweeper$getOpacity()
		);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void inventorySweeper$endOpacity(
		ItemEntityRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo callback
	) {
		DroppedItemRenderContext.end();
	}
}
